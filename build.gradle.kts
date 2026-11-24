plugins {
    // Kotlin, Spring, JPA, Postgres, Flyway, ktlint, detekt, cobertura y hooks
    id("ppc.kotlin-jpa-service") version "1.1.0"
    // Valida el token de Auth0 en cada pedido (presentación de Auth0, diap. 48)
    id("ppc.kotlin-security") version "1.1.0"
}

group = "snippetsearcher"
version = "0.0.1-SNAPSHOT"

// La versión probada en infraestructura#46 con Spring Boot 4.1.1 y Java 21
val newRelicVersion = "9.5.0"

// El agente no es una dependencia de la app: no va al classpath. Se baja en una
// configuración aparte y se copia a la imagen, porque se engancha a la JVM con
// -javaagent antes de que arranque Spring.
// Los nombres son los mismos que en printscript-service, para que los tres servicios
// se lean igual.
val newRelicAgent = configurations.create("newRelicAgent")

dependencies {
    newRelicAgent("com.newrelic.agent.java:newrelic-agent:$newRelicVersion")
    // Esta sí va al classpath, solo para leer el trace id. Sin el agente devuelve vacío,
    // así que la app corre igual en local y en los tests.
    implementation("com.newrelic.agent.java:newrelic-api:$newRelicVersion")
}

val copyNewRelicAgent =
    tasks.register<Copy>("copyNewRelicAgent") {
        description = "Deja el jar del agente en build/newrelic/newrelic.jar para el Dockerfile"
        from(newRelicAgent)
        into(layout.buildDirectory.dir("newrelic"))
        rename { "newrelic.jar" }
    }

// Colgado de bootJar y no invocado aparte desde el Dockerfile: así el jar aparece
// siempre que se arme el artefacto, lo arme quien lo arme.
tasks.named("bootJar") {
    dependsOn(copyNewRelicAgent)
}
