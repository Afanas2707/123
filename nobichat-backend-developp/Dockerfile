FROM maven:3.9.4-eclipse-temurin-21 AS builder
WORKDIR /opt/app
COPY pom.xml ./
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn clean install

FROM eclipse-temurin:21-jre
WORKDIR /opt/app
COPY --from=builder /opt/app/target/*.jar /opt/app/nobichat.jar
EXPOSE 8081
ENTRYPOINT ["java", "-Xmx2g", "-jar", "/opt/app/nobichat.jar"]
