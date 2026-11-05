FROM eclipse-temurin:21-jre
WORKDIR /app
COPY build/libs/service.jar service.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/service.jar"]
