FROM eclipse-temurin:21-jdk

WORKDIR /app

COPY . .

RUN chmod +x mvnw
RUN ./mvnw clean package -DskipTests

EXPOSE 10000

CMD ["java", "-jar", "target/gymlet-backend-0.1.0.jar"]