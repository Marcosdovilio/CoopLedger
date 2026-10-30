FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.m2,sharing=locked ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre-noble
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* && groupadd --system coopledger && useradd --system --gid coopledger coopledger
WORKDIR /app
ARG SERVICE=core-service
COPY --from=build --chown=coopledger:coopledger /workspace/${SERVICE}/target/${SERVICE}-0.1.0-SNAPSHOT.jar app.jar
USER coopledger
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
