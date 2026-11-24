plugins {
    // Kotlin, Spring, JPA, Postgres, Flyway, ktlint, detekt, cobertura y hooks
    id("ppc.kotlin-jpa-service") version "1.1.0"
    // Valida el token de Auth0 en cada pedido (presentación de Auth0, diap. 48)
    id("ppc.kotlin-security") version "1.1.0"
}

group = "snippetsearcher"
version = "0.0.1-SNAPSHOT"
