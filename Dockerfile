FROM eclipse-temurin:21-jdk AS build
WORKDIR /build
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY config config
COPY src src
# Tests, lint and coverage gates run in CI; the image build only packages.
RUN chmod +x mvnw && ./mvnw -B -DskipTests -Djacoco.skip=true -Ddetekt.skip=true -Dktlint.skip=true package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/target/starve-*.jar app.jar
ENV STARVE_DB_PATH=/data/starve.db
VOLUME /data
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
