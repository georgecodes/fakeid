FROM eclipse-temurin:17-jdk-jammy AS builder
ENV HOME=/app
RUN mkdir -p $HOME
WORKDIR $HOME
ADD . $HOME
RUN --mount=type=cache,target=/root/.m2 ./mvnw -f $HOME/pom.xml clean package

#FROM eclipse-temurin:17.0.13_11-jdk-ubi9-minimal
FROM gcr.io/distroless/java17-debian12

ARG JAR_FILE=/app/target/fakeid-all.jar
COPY --from=builder ${JAR_FILE} fakeid.jar

EXPOSE 8091
ENTRYPOINT ["java","-jar","/fakeid.jar"]
