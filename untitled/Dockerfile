# Build stage
# JDK 21 (LTS): supports the -proc:full flag and Lombok 1.18.46 required for
# annotation processing on modern JDKs, while still targeting Java 17 bytecode.
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app

# Kopiraj pom.xml
COPY pom.xml .

# Preuzmi zavisnosti
RUN mvn dependency:go-offline -B

# Kopiraj izvor
COPY src ./src

# Izgradi aplikaciju
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:17-jre

WORKDIR /app

# Kopiraj JAR iz builder stage-a
COPY --from=builder /app/target/*.jar app.jar

# Izloži port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8080/api/measurements/health || exit 1

# Pokreni aplikaciju
ENTRYPOINT ["java", "-jar", "app.jar"]
