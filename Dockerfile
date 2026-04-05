FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml ./
RUN mvn -B -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests clean package

FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /app/target/finance-dashboard-backend-0.0.1-SNAPSHOT.jar /app/app.jar

# Railway injects PORT env var — pass it to Spring Boot's server.port
# Falls back to 8081 for local Docker runs
EXPOSE 8081
ENTRYPOINT ["java", "-Dserver.port=${PORT:-8081}", "-jar", "/app/app.jar"]