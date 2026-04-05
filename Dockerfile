FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml ./
RUN mvn -B -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests clean package

FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /app/target/finance-dashboard-backend-0.0.1-SNAPSHOT.jar /app/app.jar

EXPOSE 8080

# Shell form (not JSON array) so ${PORT} is expanded by the shell
CMD java -Dserver.port=${PORT:-8081} -jar /app/app.jar