# Expects the fat jar to be built first (run-tests.sh runs `mvnw package`).
# single-stage copy — fast, reuses host ~/.m2. Go multi-stage if you
# need `docker build` to work standalone without a prior mvnw build.
FROM eclipse-temurin:21-jre
COPY target/*.jar /app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
