FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/uae-ocr-anpr-0.0.1-SNAPSHOT.jar app.jar
COPY tessdata ./tessdata
EXPOSE 9090
ENTRYPOINT ["java","-jar","/app/app.jar"]
