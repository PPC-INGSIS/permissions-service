// Dónde busca Gradle los plugins. Va primero: se resuelve antes que todo lo demás.
pluginManagement {
    repositories {
        // Las convenciones de build del proyecto (ppc.kotlin-service, ppc.kotlin-jpa-service).
        // GitHub Packages pide autenticación para bajar, aunque el paquete sea público.
        maven {
            url = uri("https://maven.pkg.github.com/PPC-INGSIS/gradle-conventions")
            credentials {
                // En tu máquina: ~/.gradle/gradle.properties. En el CI: variables de entorno.
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
            }
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "permissions"
