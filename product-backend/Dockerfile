# syntax=docker/dockerfile:1

# ---- Build stage ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

# Resolve dependencies first (cached layer selama pom.xml tidak berubah)
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -q -DskipTests dependency:resolve

# Build aplikasi (test di-skip; integration test butuh container terpisah)
COPY src/ src/
RUN ./mvnw -B -q clean package -DskipTests

# ---- Runtime stage ----
FROM eclipse-temurin:25-jdk AS runtime
WORKDIR /app

# Jalankan sebagai non-root
RUN useradd --system --uid 1001 spring
USER spring

COPY --from=build /app/target/product-backend-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
