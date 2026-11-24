package snippetsearcher.permissions.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.Customizer.withDefaults
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

/**
 * Quién puede entrar. Validar el token (firma, iss, aud, exp) lo configura application.properties;
 * acá se decide qué rutas lo exigen y con qué scope (presentación de Auth0, diap. 48).
 * La regla sale del método HTTP, igual que en Snippets: leer pide read:snippets y cambiar, write:snippets.
 */
@Configuration
class SecurityConfiguration {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .authorizeHttpRequests {
                it
                    // El healthcheck pega sin token: con 401 marcaría como caído a un contenedor sano.
                    // Va primero: Spring usa la primera regla que coincide, y el healthcheck también es un GET
                    .requestMatchers("/actuator/health/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/**")
                    .hasAuthority("SCOPE_read:snippets")
                    .anyRequest()
                    .hasAuthority("SCOPE_write:snippets")
            }.oauth2ResourceServer { it.jwt(withDefaults()) }
            // Con Bearer no hay sesión: cada pedido trae su token y cualquier réplica lo valida
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            // CSRF abusa de las cookies que el browser manda solo; el header Authorization no se manda solo
            .csrf { it.disable() }
        return http.build()
    }
}
