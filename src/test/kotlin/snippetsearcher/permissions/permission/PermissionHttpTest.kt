package snippetsearcher.permissions.permission

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import snippetsearcher.permissions.TestcontainersConfiguration
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Los otros tests de controller usan MockMvc, que no levanta Tomcat: arma el request
 * y se lo da directo a Spring. Por eso no ven lo que Tomcat rechaza antes.
 *
 * Esto importa para el userId, que es el "sub" de Auth0 y puede traer "|", "/" o "@".
 * Tomcat rechaza varios de esos en el path por defecto, incluso percent-encoded, y
 * responde 400 sin que el controller se entere. Por eso el userId va como query param,
 * y por eso este test pasa por un servidor de verdad.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
class PermissionHttpTest(
    @Autowired private val service: PermissionService,
) {
    @LocalServerPort
    private var port: Int = 0

    private val http: HttpClient = HttpClient.newHttpClient()

    private fun consultar(
        snippetId: UUID,
        userId: String,
    ): HttpResponse<String> {
        val query = URLEncoder.encode(userId, StandardCharsets.UTF_8)
        val request =
            HttpRequest
                .newBuilder(URI.create("http://localhost:$port/permissions/$snippetId?userId=$query"))
                .GET()
                .build()
        return http.send(request, HttpResponse.BodyHandlers.ofString())
    }

    @Test
    fun `un userId con caracteres raros llega entero al controller`() {
        // "/" y "@" son los que Tomcat rechazaria en el path; "|" es el separador de Auth0
        val raros = listOf("auth0|abc123", "waad|tenant/usuario", "oidc|persona@example.com")

        raros.forEach { userId ->
            val snippetId = UUID.randomUUID()
            service.register(snippetId, userId, "OWNER")

            val response = consultar(snippetId, userId)

            assertEquals(200, response.statusCode(), "fallo con userId=$userId")
            // Que vuelva igual prueba que no se perdio ni se corto en el camino
            assertTrue(response.body().contains("\"role\":\"OWNER\""), "fallo con userId=$userId")
        }
    }

    @Test
    fun `un usuario sin acceso responde 404 contra el servidor real`() {
        val snippetId = UUID.randomUUID()
        service.register(snippetId, "auth0|dueno", "OWNER")

        val response = consultar(snippetId, "auth0|otro")

        assertEquals(404, response.statusCode())
    }
}
