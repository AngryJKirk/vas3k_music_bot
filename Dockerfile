FROM maven:3.8.4-openjdk-17-slim AS build

WORKDIR /app

COPY ./pom.xml .

# verify --fail-never works much better than dependency:resolve or dependency:go-offline
RUN mvn clean verify --fail-never

COPY ./src ./src

RUN mvn package -DskipTests

FROM openjdk:17-jdk-slim

ENV SPRING_PROFILES_ACTIVE production

COPY --from=build /app/target/vas3k_music.jar /usr/local/lib/vas3k_music.jar

ENTRYPOINT ["java","-jar","/usr/local/lib/vas3k_music.jar"]
