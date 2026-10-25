FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
COPY server ./server
RUN sh ./gradlew --no-daemon serverDist serverTest

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/build/server-dist/ /app/
RUN chmod 755 /app/bin/trap-house-server
USER 10001:10001
CMD ["/app/bin/trap-house-server"]
