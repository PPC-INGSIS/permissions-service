package snippetsearcher.permissions.permission

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
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

    private companion object {
        const val SOME_USER = "auth0|65f3a1b2c3d4e5f6a7b8c9d0"
    }
}
