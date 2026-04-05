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

# Shell form so ${PORT} expands correctly at runtime.
# JVM flags keep memory under 512MB for Railway/Render free tier.
CMD java -Xms64m -Xmx350m -XX:MaxMetaspaceSize=128m -Dserver.port=${PORT:-8081} -jar /app/app.jar