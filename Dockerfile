# 1. Faza bildovanja sa Gradle-om
FROM gradle:8.5-jdk17 AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN ./gradlew build -x test --no-daemon

# 2. Faza pokretanja aplikacije
FROM openjdk:17-slim
EXPOSE 8080
COPY --from=build /home/gradle/src/build/libs/*-all.jar /app/ktor-app.jar
ENTRYPOINT ["java", "-jar", "/app/ktor-app.jar"]
