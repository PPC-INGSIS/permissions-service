package snippetsearcher.permissions.security

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

// Mismos @Import que PermissionControllerTest: Spring reusa ese contexto en vez de levantar otro
@Import(TestcontainersConfiguration::class)
@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigurationTest(
    @Autowired private val mockMvc: MockMvc,
) {
    private val readScope = jwt().authorities(SimpleGrantedAuthority("SCOPE_read:snippets"))

    @Test
    fun `un pedido sin token responde 401`() {
        mockMvc
            .post("/permissions") {
                contentType = MediaType.APPLICATION_JSON
                content = "{}"
            }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `registrar un acceso con solo read_snippets responde 403`() {
        mockMvc
            .post("/permissions") {
                with(readScope)
                contentType = MediaType.APPLICATION_JSON
                content = "{}"
            }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `un GET sin read_snippets responde 403`() {
        // jwt() sin authorities trae solo SCOPE_read: token válido, pero sin el permiso de la API
        mockMvc.get("/permissions") { with(jwt()) }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `un GET con read_snippets pasa la seguridad`() {
        // Todavía no hay ningún GET: el 405 dice que llegó al controller, o sea que la seguridad lo dejó pasar
        mockMvc.get("/permissions") { with(readScope) }.andExpect { status { isMethodNotAllowed() } }
    }

    @Test
    fun `el healthcheck responde sin token`() {
        mockMvc.get("/actuator/health").andExpect { status { isOk() } }
    }
}
