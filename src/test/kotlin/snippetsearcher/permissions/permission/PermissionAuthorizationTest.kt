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
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import snippetsearcher.permissions.TestcontainersConfiguration
import java.util.UUID
import kotlin.test.assertEquals

/**
 * Quién puede operar sobre los accesos de un snippet.
 *
 * Hasta ahora Permisos **validaba** el token pero no **autorizaba** la operación: el usuario al que
 * afectaba el pedido venía en el body o en el query, no del `sub`. Con un token válido cualquiera
 * —y todo usuario logueado tiene uno— se podía registrar un acceso sobre un snippet ajeno, borrar
 * los accesos de cualquiera, o enumerar los snippets de otro.
 *
 * Todas las reglas se deciden con esta misma tabla: el dueño de un snippet es la fila con rol
 * `OWNER`. No hace falta saber nada de snippets.
 */
@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class PermissionAuthorizationTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @Test
    fun `registrarse como dueno de un snippet ajeno responde 403`() {
        val snippetId = UUID.randomUUID()
        registrar(ANA, snippetId, ANA, "OWNER").andExpect { status { isCreated() } }

        // Beto se registra como dueño del snippet de Ana: la clave es (snippet, usuario), así que
        // antes no chocaba con la fila de Ana y Beto quedaba dueño también
        registrar(BETO, snippetId, BETO, "OWNER").andExpect { status { isForbidden() } }

        consultar(BETO, snippetId).andExpect { status { isNotFound() } }
    }

    @Test
    fun `registrar a otro como dueno responde 403`() {
        val snippetId = UUID.randomUUID()

        registrar(ANA, snippetId, BETO, "OWNER").andExpect { status { isForbidden() } }
    }

    @Test
    fun `compartir un snippet ajeno responde 403`() {
        val snippetId = UUID.randomUUID()
        registrar(ANA, snippetId, ANA, "OWNER")

        // Beto se da acceso de lectura a sí mismo sobre el snippet de Ana
        registrar(BETO, snippetId, BETO, "READ").andExpect { status { isForbidden() } }

        consultar(BETO, snippetId).andExpect { status { isNotFound() } }
    }

    @Test
    fun `el dueno si puede compartir`() {
        val snippetId = UUID.randomUUID()
        registrar(ANA, snippetId, ANA, "OWNER")

        registrar(ANA, snippetId, BETO, "READ").andExpect { status { isCreated() } }

        consultar(BETO, snippetId).andExpect {
            status { isOk() }
            jsonPath("$.role") { value("READ") }
        }
    }

    @Test
    fun `borrar los accesos de un snippet ajeno responde 403 y no borra nada`() {
        val snippetId = UUID.randomUUID()
        registrar(ANA, snippetId, ANA, "OWNER")

        mockMvc
            .delete("/permissions/{snippetId}", snippetId) { with(escribiendo(BETO)) }
            .andExpect { status { isForbidden() } }

        consultar(ANA, snippetId).andExpect { status { isOk() } }
    }

    // Un lector tampoco: compartir y borrar siguen siendo solo del dueño
    @Test
    fun `un lector no puede borrar los accesos ni compartir`() {
        val snippetId = UUID.randomUUID()
        registrar(ANA, snippetId, ANA, "OWNER")
        registrar(ANA, snippetId, BETO, "READ")

        mockMvc
            .delete("/permissions/{snippetId}", snippetId) { with(escribiendo(BETO)) }
            .andExpect { status { isForbidden() } }
        registrar(BETO, snippetId, CARLA, "READ").andExpect { status { isForbidden() } }
    }

    // Preguntar por el acceso de otro contaría quién tiene acceso a qué
    @Test
    fun `consultar el rol de otro usuario responde 403`() {
        val snippetId = UUID.randomUUID()
        registrar(ANA, snippetId, ANA, "OWNER")

        consultar(BETO, snippetId, sobre = ANA).andExpect { status { isForbidden() } }
    }

    // Y listar los de otro dejaría enumerar sus snippets
    @Test
    fun `listar los accesos de otro usuario responde 403`() {
        mockMvc
            .get("/permissions") {
                with(leyendo(BETO))
                param("userId", ANA)
            }.andExpect { status { isForbidden() } }
    }

    /**
     * El mensaje del 403 es el mismo para todos los casos, a propósito.
     *
     * Decir *por qué* no se puede —"ese snippet ya tiene dueño", "no sos el dueño"— le contaría a
     * cualquiera si un snippet existe y de quién es, que es justo lo que no se cuenta.
     */
    @Test
    fun `el 403 no cuenta por que`() {
        val snippetId = UUID.randomUUID()
        registrar(ANA, snippetId, ANA, "OWNER")

        val sobreSnippetAjeno =
            registrar(BETO, snippetId, BETO, "OWNER").andReturn().response.contentAsString
        val sobreSnippetQueNoExiste =
            registrar(BETO, UUID.randomUUID(), ANA, "OWNER").andReturn().response.contentAsString

        assertEquals(sobreSnippetAjeno, sobreSnippetQueNoExiste)
    }

    private fun registrar(
        caller: String,
        snippetId: UUID,
        userId: String,
        role: String,
    ) = mockMvc.post("/permissions") {
        with(escribiendo(caller))
        contentType = MediaType.APPLICATION_JSON
        content = """{"snippetId":"$snippetId","userId":"$userId","role":"$role"}"""
    }

    private fun consultar(
        caller: String,
        snippetId: UUID,
        sobre: String = caller,
    ) = mockMvc.get("/permissions/{snippetId}", snippetId) {
        with(leyendo(caller))
        param("userId", sobre)
    }

    private companion object {
        const val ANA = "auth0|autorizacion-ana"
        const val BETO = "auth0|autorizacion-beto"
        const val CARLA = "auth0|autorizacion-carla"

        fun escribiendo(sub: String) =
            jwt().jwt { it.subject(sub) }.authorities(SimpleGrantedAuthority("SCOPE_write:snippets"))

        fun leyendo(sub: String) =
            jwt().jwt { it.subject(sub) }.authorities(SimpleGrantedAuthority("SCOPE_read:snippets"))
    }
}
