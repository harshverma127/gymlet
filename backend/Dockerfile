FROM eclipse-temurin:21-jdk

WORKDIR /app

COPY . .

RUN chmod +x mvnw
RUN ./mvnw clean package -DskipTests

EXPOSE 10000

# Render injects PORT; bind to it. The "prod" profile points at the managed
# PostgreSQL database (Supabase) via DB_URL / DB_USER / DB_PASSWORD.
CMD ["sh", "-c", "java -Dserver.port=${PORT:-10000} -jar target/gymlet-backend-0.1.0.jar"]