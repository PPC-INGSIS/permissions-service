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

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]