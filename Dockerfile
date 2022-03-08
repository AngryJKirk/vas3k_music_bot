FROM maven:3.8.4-openjdk-17-slim AS build

WORKDIR /app

COPY ./pom.xml .

# verify --fail-never works much better than dependency:resolve or dependency:go-offline
RUN mvn clean verify --fail-never

COPY ./src ./src

RUN mvn package -DskipTests

FROM openjdk:17-jdk-slim

COPY --from=build /app/target/vas3k_music.jar /usr/local/lib/vas3k_music.jar

ENTRYPOINT ["java","-Xmx32m","-jar","/usr/local/lib/vas3k_music.jar"]
