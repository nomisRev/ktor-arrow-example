FROM openjdk:8-jre-slim
EXPOSE 8080
RUN mkdir /app
COPY ktor-arrow-sample.jar /app/ktor-arrow-sample.jar
ENTRYPOINT ["java", "-jar","/app/ktor-arrow-sample.jar"]