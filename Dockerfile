FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

ARG GITHUB_ACTOR

COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle

RUN --mount=type=secret,id=github_token \
    GITHUB_ACTOR="$GITHUB_ACTOR" GITHUB_TOKEN="$(cat /run/secrets/github_token)" \
    ./gradlew dependencies --no-daemon

COPY src src
# bootJar depende de copyNewRelicAgent, que deja el jar del agente en build/newrelic/
RUN --mount=type=secret,id=github_token \
    GITHUB_ACTOR="$GITHUB_ACTOR" GITHUB_TOKEN="$(cat /run/secrets/github_token)" \
    ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd --system --no-create-home --shell /usr/sbin/nologin app
COPY --from=build --chown=app:app /app/build/libs/*SNAPSHOT.jar app.jar
COPY --from=build --chown=app:app /app/build/newrelic/newrelic.jar newrelic.jar
COPY --chown=app:app newrelic/newrelic.yml newrelic.yml
USER app

EXPOSE 8080

# El agente se engancha antes que Spring: tiene que ir en la línea de java, no adentro
# de la app. Sin NEW_RELIC_LICENSE_KEY no manda nada y la app arranca igual.
ENTRYPOINT ["java", "-javaagent:/app/newrelic.jar", "-jar", "app.jar"]
