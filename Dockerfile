FROM maven:3.9.7-eclipse-temurin-22-alpine AS build

WORKDIR /app

COPY ./pom.xml .

# verify --fail-never works much better than dependency:resolve or dependency:go-offline
RUN mvn clean verify --fail-never

COPY ./src ./src

RUN mvn package -DskipTests

FROM eclipse-temurin:22-alpine

COPY --from=build /app/target/vas3k_music.jar /usr/local/lib/vas3k_music.jar

ENTRYPOINT ["java","-Xmx32m","-jar","/usr/local/lib/vas3k_music.jar"]
