FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace

COPY gradle ./gradle
COPY gradlew gradlew.bat build.gradle.kts settings.gradle.kts gradle.properties ./
RUN chmod +x ./gradlew && ./gradlew --no-daemon dependencies

COPY src ./src
COPY config ./config
RUN ./gradlew --no-daemon clean buildFatJar

FROM eclipse-temurin:21-jre

RUN useradd --create-home --uid 10001 nmsi
WORKDIR /app

COPY --from=build /workspace/build/libs/nmsi-support-navigator-all.jar /app/application.jar

ENV PORT=8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC"

EXPOSE 8080
USER nmsi

CMD ["java", "-jar", "/app/application.jar"]
