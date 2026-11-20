package snippetsearcher.permissions.permission

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import snippetsearcher.permissions.TestcontainersConfiguration
import java.util.UUID

@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class PermissionControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    private fun body(
        snippetId: UUID,
        role: String,
        userId: String = SOME_USER,
    ) = """{"snippetId":"$snippetId","userId":"$userId","role":"$role"}"""

    private fun registrar(
        snippetId: UUID,
        role: String,
        userId: String = SOME_USER,
    ) = mockMvc.post("/permissions") {
        with(writeScope)
        contentType = MediaType.APPLICATION_JSON
        content = body(snippetId, role, userId)
    }

    @Test
    fun `POST permissions con un acceso nuevo responde 201`() {
        val snippetId = UUID.randomUUID()

        registrar(snippetId, "OWNER").andExpect {
            status { isCreated() }
            jsonPath("$.snippetId") { value(snippetId.toString()) }
            jsonPath("$.role") { value("OWNER") }
        }
    }

    @Test
    fun `POST permissions del mismo acceso dos veces responde 409`() {
        val snippetId = UUID.randomUUID()
        registrar(snippetId, "OWNER")

        registrar(snippetId, "READ").andExpect {
            status { isConflict() }
            jsonPath("$.message") { exists() }
        }
    }

    @Test
    fun `POST permissions con un rol que no existe responde 400`() {
        registrar(UUID.randomUUID(), "ADMIN").andExpect {
            status { isBadRequest() }
            jsonPath("$.message") { value("El rol 'ADMIN' no existe. Los validos son: OWNER, READ") }
        }
    }

    @Test
    fun `POST permissions sin cuerpo responde 400`() {
        mockMvc
            .post("/permissions") {
                with(writeScope)
                contentType = MediaType.APPLICATION_JSON
            }.andExpect {
                status { isBadRequest() }
            }
    }

    @Test
    fun `POST permissions sin userId responde 400`() {
        registrar(UUID.randomUUID(), "OWNER", userId = "").andExpect {
            status { isBadRequest() }
            jsonPath("$.message") { value("Falta el userId") }
        }
    }

    // Sin esto la base lo rechaza por el largo de la columna y sale un 500
    @Test
    fun `POST permissions con un userId mas largo que la columna responde 400`() {
        val largo = "a".repeat(SnippetPermissionId.MAX_USER_ID_LENGTH + 1)

        registrar(UUID.randomUUID(), "OWNER", userId = largo).andExpect {
            status { isBadRequest() }
            jsonPath("$.message") { exists() }
        }
    }

    private fun consultar(
        snippetId: Any,
        userId: String = SOME_USER,
    ) = mockMvc.get("/permissions/{snippetId}", snippetId) {
        with(readScope)
        param("userId", userId)
    }

    @Test
    fun `GET permissions devuelve el rol del usuario`() {
        val snippetId = UUID.randomUUID()
        registrar(snippetId, "OWNER")

        consultar(snippetId).andExpect {
            status { isOk() }
            jsonPath("$.snippetId") { value(snippetId.toString()) }
            jsonPath("$.role") { value("OWNER") }
        }
    }

    @Test
    fun `GET permissions de un usuario sin acceso responde 404`() {
        val snippetId = UUID.randomUUID()
        registrar(snippetId, "OWNER")

        consultar(snippetId, userId = "auth0|00000000000000000000000b").andExpect {
            status { isNotFound() }
            jsonPath("$.message") { exists() }
        }
    }

    @Test
    fun `GET permissions de un snippet que nadie registro responde 404`() {
        consultar(UUID.randomUUID()).andExpect {
            status { isNotFound() }
        }
    }

    // La razon de que el userId sea query param: en el path, Tomcat rechaza varios de
    // estos caracteres por defecto, incluso codificados, y nunca se llegaria al controller
    @Test
    fun `GET permissions acepta un userId con caracteres raros`() {
        val raros = listOf("auth0|abc", "waad|tenant/user", "oidc|user@example.com")

        raros.forEach { userId ->
            val snippetId = UUID.randomUUID()
            registrar(snippetId, "READ", userId = userId)

            consultar(snippetId, userId = userId).andExpect {
                status { isOk() }
                jsonPath("$.userId") { value(userId) }
                jsonPath("$.role") { value("READ") }
            }
        }
    }

    @Test
    fun `GET permissions con un snippetId que no es UUID responde 400`() {
        consultar("no-es-un-uuid").andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `GET permissions sin userId responde 400`() {
        mockMvc.get("/permissions/{snippetId}", UUID.randomUUID()) { with(readScope) }.andExpect {
            status { isBadRequest() }
        }
    }

    // Usuario nuevo por test: la base es compartida y listar "todo lo de un usuario"
    // solo da un resultado estable si nadie mas lo toco
    private fun usuarioNuevo() = "auth0|${UUID.randomUUID()}"

    private fun listar(
        userId: String,
        role: String? = null,
    ) = mockMvc.get("/permissions") {
        param("userId", userId)
        role?.let { param("role", it) }
    }

    @Test
    fun `GET permissions lista los accesos del usuario`() {
        val usuario = usuarioNuevo()
        val propio = UUID.randomUUID()
        registrar(propio, "OWNER", userId = usuario)
        registrar(UUID.randomUUID(), "READ", userId = usuario)

        listar(usuario).andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(2) }
        }
    }

    @Test
    fun `GET permissions filtra por rol`() {
        val usuario = usuarioNuevo()
        val propio = UUID.randomUUID()
        registrar(propio, "OWNER", userId = usuario)
        registrar(UUID.randomUUID(), "READ", userId = usuario)

        listar(usuario, role = "OWNER").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].snippetId") { value(propio.toString()) }
            jsonPath("$[0].role") { value("OWNER") }
        }
    }

    // Sin accesos no es un error: el usuario existe y la respuesta es "ninguno"
    @Test
    fun `GET permissions de un usuario sin accesos responde 200 con lista vacia`() {
        listar(usuarioNuevo()).andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(0) }
        }
    }

    @Test
    fun `GET permissions con un rol que no existe responde 400`() {
        listar(usuarioNuevo(), role = "ADMIN").andExpect {
            status { isBadRequest() }
            jsonPath("$.message") { value("El rol 'ADMIN' no existe. Los validos son: OWNER, READ") }
        }
    }

    @Test
    fun `GET permissions del listado sin userId responde 400`() {
        mockMvc.get("/permissions").andExpect {
            status { isBadRequest() }
        }
    }

    private companion object {
        const val SOME_USER = "auth0|65f3a1b2c3d4e5f6a7b8c9d0"

        // Registrar un acceso es escribir: exige write:snippets (la seguridad se prueba en SecurityConfigurationTest)
        val writeScope = jwt().authorities(SimpleGrantedAuthority("SCOPE_write:snippets"))

        // Consultar es leer: exige read:snippets
        val readScope = jwt().authorities(SimpleGrantedAuthority("SCOPE_read:snippets"))
    }
}
