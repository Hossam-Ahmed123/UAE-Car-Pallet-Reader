# Build stage
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY src ./src
RUN mvn -q -DskipTests package

# Runtime stage
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/target/anpr-service-1.0.0.jar app.jar
COPY models ./models
COPY tessdata ./tessdata
EXPOSE 9090
ENTRYPOINT ["java", "-jar", "app.jar"]
