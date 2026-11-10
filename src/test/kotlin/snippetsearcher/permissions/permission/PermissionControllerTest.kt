package snippetsearcher.permissions.permission

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
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
        userId: String = "auth0|65f3a1b2c3d4e5f6a7b8c9d0",
    ) = """{"snippetId":"$snippetId","userId":"$userId","role":"$role"}"""

    private fun registrar(
        snippetId: UUID,
        role: String,
        userId: String = "auth0|65f3a1b2c3d4e5f6a7b8c9d0",
    ) = mockMvc.post("/permissions") {
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
                contentType = MediaType.APPLICATION_JSON
            }.andExpect {
                status { isBadRequest() }
            }
    }

    // Se pasa como variable de template y no interpolado: el "sub" de Auth0 trae "|",
    // que en una URL va percent-encoded, y asi lo codifica Spring por nosotros.
    private fun consultar(
        snippetId: UUID,
        userId: String = "auth0|65f3a1b2c3d4e5f6a7b8c9d0",
    ) = mockMvc.get("/permissions/{snippetId}/users/{userId}", snippetId, userId)

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
}
