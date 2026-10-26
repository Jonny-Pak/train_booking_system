# Build context: backend/ (set Render Root Directory to backend).
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
RUN mvn -B dependency:go-offline
COPY src/main/ src/main/
# Integration tests need an isolated PostgreSQL database; run them outside image builds.
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app --home-dir /app app
COPY --from=build --chown=app:app /workspace/target/ticket-booking-0.0.1-SNAPSHOT.jar /app/app.jar
USER app
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65.0 -XX:InitialRAMPercentage=20.0 -Djava.awt.headless=true"
EXPOSE 10000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
