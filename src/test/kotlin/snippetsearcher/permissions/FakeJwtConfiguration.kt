package snippetsearcher.permissions

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import java.time.Instant

/**
 * Un JwtDecoder que acepta cualquier token y lo devuelve con los dos scopes.
 *
 * **El `sub` lo elige el test** con `FakeJwtSubject`. Desde que Permisos autoriza según quién pide,
 * un test que pega por HTTP necesita hablar como un usuario u otro, y no alcanza con poner el sub en
 * el token: un sub de Auth0 trae `|`, que RFC 6750 no permite en un bearer token, así que Spring lo
 * rechaza con 401 antes de que el decoder lo vea.
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
    fun fakeJwtSubject() = FakeJwtSubject()

    @Bean
    fun jwtDecoder(subject: FakeJwtSubject): JwtDecoder =
        JwtDecoder { token ->
            Jwt
                .withTokenValue(token)
                .header("alg", "none")
                .claim("sub", subject.value)
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

/** Quién dice ser el próximo token. Los tests que pegan por HTTP lo cambian antes de cada pedido. */
class FakeJwtSubject {
    @Volatile
    var value: String = "auth0|test"
}
