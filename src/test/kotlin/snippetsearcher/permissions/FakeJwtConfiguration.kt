package snippetsearcher.permissions

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import java.time.Instant

/**
 * Un JwtDecoder que acepta cualquier token y lo devuelve con los dos scopes.
 *
 * Hace falta solo en los tests que pegan por HTTP de verdad: ahí no existe el `jwt()`
 * de MockMvc, el filtro de seguridad corre entero y el decoder real saldría a buscar
 * las claves del emisor, que en los tests es un dominio inexistente.
 *
 * Reemplaza al decoder que arma Boot a partir del issuer-uri, porque ese está declarado
 * como @ConditionalOnMissingBean.
 */
@TestConfiguration(proxyBeanMethods = false)
class FakeJwtConfiguration {
    @Bean
    fun jwtDecoder(): JwtDecoder =
        JwtDecoder { token ->
            Jwt
                .withTokenValue(token)
                .header("alg", "none")
                .claim("sub", "auth0|test")
                .claim("scope", "read:snippets write:snippets")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(TOKEN_DURATION_SECONDS))
                .build()
        }

    companion object {
        /** El valor da igual: el decoder de arriba acepta cualquiera. */
        const val TOKEN = "token-de-prueba"
        private const val TOKEN_DURATION_SECONDS = 3600L
    }
}
