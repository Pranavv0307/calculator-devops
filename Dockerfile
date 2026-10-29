# ---- Stage 1: build ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
# Copy only the pom first so Docker can cache the dependency download layer
# separately from your source code (huge speedup on repeated builds).
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

# ---- Stage 2: runtime ----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Security best practice: don't run as root inside the container.
RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=build /app/target/calculator.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
