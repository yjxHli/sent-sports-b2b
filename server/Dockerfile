# 后端运行镜像
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN ./mvnw -q -DskipTests package || mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/target/sent-server.jar app.jar
EXPOSE 8088
ENTRYPOINT ["java", "-jar", "-Dspring.profiles.active=prod", "app.jar"]
