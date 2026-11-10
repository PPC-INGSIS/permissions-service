FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

ARG GITHUB_ACTOR

COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle

RUN --mount=type=secret,id=github_token \
    GITHUB_ACTOR="$GITHUB_ACTOR" GITHUB_TOKEN="$(cat /run/secrets/github_token)" \
    ./gradlew dependencies --no-daemon

COPY src src
RUN --mount=type=secret,id=github_token \
    GITHUB_ACTOR="$GITHUB_ACTOR" GITHUB_TOKEN="$(cat /run/secrets/github_token)" \
    ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd --system --no-create-home --shell /usr/sbin/nologin app
COPY --from=build --chown=app:app /app/build/libs/*SNAPSHOT.jar app.jar
USER app

# El puerto es configuración: el stack lo puede pisar con SERVER_PORT. Spring y el
# healthcheck leen esta misma variable, así nunca apuntan a puertos distintos.
ENV SERVER_PORT=8080
EXPOSE ${SERVER_PORT}

# Swarm da por lista la tarea recién cuando Spring contesta, no cuando arranca Java.
# readiness y no /actuator/health: ese incluye Postgres, y una caída de la base
# reiniciaría la app en loop. start-period cubre el arranque en frío de la VM (~35 s).
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=3 \
    CMD wget -q -O /dev/null "http://127.0.0.1:${SERVER_PORT}/actuator/health/readiness" || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
