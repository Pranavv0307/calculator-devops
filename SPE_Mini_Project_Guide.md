# CSE 816 Mini-Project — Build Guide
### End-to-End CI/CD Pipeline for a Calculator REST API (Plain Java → Git → Jenkins → Docker → Docker Compose)

> **Framework choice:** this version builds the calculator as a **plain Java program** — no Spring Boot — using only the JDK's built-in `com.sun.net.httpserver.HttpServer` to serve HTTP. Your course brief explicitly allows this ("a simple Java calculator program will also be sufficient while following the remaining project requirements"). Every other requirement — REST endpoints, JSON responses, input validation with HTTP 400, a health endpoint, JUnit tests, Maven build, Docker, Compose, Jenkins — stays exactly as specified; only the *how you serve HTTP* changes. One upside worth knowing: this also means **zero runtime dependencies**, which makes the Docker image smaller and the Maven setup simpler to actually understand line-by-line.

This guide walks you through the **entire mini-project from zero**, assuming you've never used Maven, Git branching, Docker, Docker Compose, or Jenkins before. Each section explains *what the tool is*, *why this step exists*, and *what you should understand* before moving on — not just commands to copy-paste.

Work through the phases in order. Don't skip the "why" boxes — the demo (and the viva-style questions during it) will test understanding, not just working screenshots.

---

## 0. Big picture — what you're actually building

```
You write code  →  Git/GitHub stores + tracks it
                         │
                         ▼ (push triggers)
                     Jenkins pipeline
                         │
        ┌────────────────┼─────────────────┐
        ▼                ▼                 ▼
  mvn clean package   mvn test        docker build
   (compile JAR)    (run tests)    (package JAR into
                                     a container image)
                                         │
                                         ▼
                                  docker push
                                (image → Docker Hub)
                                         │
                                         ▼
                              docker compose up -d
                           (deploy app + nginx proxy)
```

Five tools, five jobs:
- **Git/GitHub** — version control: track every change, collaborate, branch/merge.
- **Maven** — Java build tool: compiles your code, runs tests, produces a `.jar`.
- **Docker** — packages your app + everything it needs to run into a portable "image," so it runs identically anywhere.
- **Jenkins** — the automation robot: watches GitHub, and on every push runs build → test → containerize → push → deploy, with zero manual steps.
- **Docker Compose** — starts multiple containers (your app + an nginx proxy) together with one command.

---

## Phase 1 — Environment setup

You need these installed on your machine (Windows/Mac/Linux all fine; use WSL2 on Windows for a smoother experience).

| Tool | Check install | Install from |
|---|---|---|
| JDK 17+ | `java -version` | [Adoptium Temurin](https://adoptium.net/) |
| Maven | `mvn -version` | [maven.apache.org](https://maven.apache.org/download.cgi) (or use the Maven wrapper later, see below) |
| Git | `git --version` | [git-scm.com](https://git-scm.com/downloads) |
| Docker Desktop | `docker --version` | [docker.com](https://www.docker.com/products/docker-desktop/) |
| An IDE | — | IntelliJ IDEA Community or VS Code with the "Extension Pack for Java" |
| GitHub account | — | github.com |
| Docker Hub account | — | hub.docker.com (free tier is enough) |

**Why Docker Hub?** It's a public registry — like GitHub but for container images. Jenkins will push your built image there, and anyone (including your professor) can `docker pull` and run it.

Create a new empty **public** GitHub repository now, named `calculator-devops`. Don't initialize it with a README yet — you'll push from your local project.

---

## Phase 2 — The plain-Java Calculator API

### 2.1 What we're using instead of a framework

No Spring, no third-party web framework at all. We'll use **`com.sun.net.httpserver.HttpServer`** — a minimal HTTP server that has shipped *inside the JDK itself* since Java 6. It's low-level (you parse the query string yourself, you write the JSON yourself), which is exactly why it's a good way to actually understand what a framework like Spring normally hides from you: routing, request parsing, status codes, and response writing, all by hand.

Because it needs no external library, your `pom.xml` will have **zero runtime dependencies** — only a test-scoped JUnit dependency. That also simplifies Docker later: no risk of a dependency mismatch between build and runtime stages.

### 2.2 Create the project structure and `pom.xml` by hand

Without Spring Initializr to generate things for you, you'll build the Maven layout yourself — which is worth doing once, since Maven expects this exact folder shape for every Java project you'll ever touch:

```
calculator-devops/
  ├── pom.xml
  └── src/
      ├── main/java/com/example/calculator/
      └── test/java/com/example/calculator/
```

Create those folders, then create `pom.xml` in the project root:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                              http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>calculator</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <properties>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <junit.version>5.10.2</junit.version>
    </properties>

    <dependencies>
        <!-- test-only: not shipped in the final runtime image -->
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>${junit.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <finalName>calculator</finalName>
        <plugins>
            <!-- Makes the JAR runnable with `java -jar`, by writing a Main-Class
                 entry into the JAR's manifest. Without this plugin, `java -jar`
                 wouldn't know which class has your main() method. -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-jar-plugin</artifactId>
                <version>3.4.1</version>
                <configuration>
                    <archive>
                        <manifest>
                            <mainClass>com.example.calculator.CalculatorServer</mainClass>
                        </manifest>
                    </archive>
                </configuration>
            </plugin>
            <!-- Runs your JUnit 5 tests during `mvn test` and writes the
                 XML reports Jenkins will later publish. -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.2.5</version>
            </plugin>
        </plugins>
    </build>
</project>
```

**Why each part matters:**
- `<packaging>jar</packaging>` + `<finalName>calculator</finalName>` — `mvn package` will produce `target/calculator.jar`, a predictable name you'll reference later in the Dockerfile.
- `maven-jar-plugin`'s `mainClass` — this is the plain-Java equivalent of what `spring-boot-maven-plugin` does for you automatically: it tells the JVM which class's `main()` to run when you do `java -jar calculator.jar`.
- `junit-jupiter` is `test`-scoped, meaning Maven never puts it in the final JAR or passes it to the runtime Docker stage — only used while running `mvn test`.

### 2.3 Generate the Maven wrapper (so Jenkins doesn't need Maven installed)

With Maven installed locally, run once from the project root:
```bash
mvn -N wrapper:wrapper
```
This creates `mvnw`, `mvnw.cmd`, and a `.mvn/` folder — commit all of them. From now on, anyone (including Jenkins) can run `./mvnw ...` and get the exact same Maven version without installing Maven themselves.

### 2.4 Write the calculator logic

Split this into two classes on purpose: a small pure-logic class (easy to unit-test with no HTTP involved) and the HTTP server class that wraps it (what you'll integration-test).

Create `src/main/java/com/example/calculator/Calculator.java`:
```java
package com.example.calculator;

public class Calculator {
    public static double add(double a, double b) { return a + b; }
    public static double subtract(double a, double b) { return a - b; }
    public static double multiply(double a, double b) { return a * b; }

    public static double divide(double a, double b) {
        if (b == 0) {
            throw new ArithmeticException("Division by zero");
        }
        return a / b;
    }
}
```

Create `src/main/java/com/example/calculator/CalculatorServer.java`:
```java
package com.example.calculator;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class CalculatorServer {

    public static void main(String[] args) throws IOException {
        HttpServer server = startServer(8080);
        System.out.println("Calculator API listening on port 8080");
    }

    /** Package-private so tests can start the server on a throwaway port. */
    static HttpServer startServer(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/api/v1/add", ex -> handleOp(ex, "add"));
        server.createContext("/api/v1/subtract", ex -> handleOp(ex, "subtract"));
        server.createContext("/api/v1/multiply", ex -> handleOp(ex, "multiply"));
        server.createContext("/api/v1/divide", ex -> handleOp(ex, "divide"));
        server.createContext("/actuator/health", CalculatorServer::handleHealth);

        server.setExecutor(null); // uses the JDK's default single-threaded executor
        server.start();
        return server;
    }

    private static void handleOp(HttpExchange exchange, String op) throws IOException {
        Map<String, String> params = queryToMap(exchange.getRequestURI());
        String aStr = params.get("a");
        String bStr = params.get("b");

        if (aStr == null || bStr == null) {
            sendJson(exchange, 400, errorJson("Missing required parameters a and/or b", op, aStr, bStr));
            return;
        }

        double a, b;
        try {
            a = Double.parseDouble(aStr);
            b = Double.parseDouble(bStr);
        } catch (NumberFormatException e) {
            sendJson(exchange, 400, errorJson("Parameters a and b must be numeric", op, aStr, bStr));
            return;
        }

        double result;
        try {
            result = switch (op) {
                case "add" -> Calculator.add(a, b);
                case "subtract" -> Calculator.subtract(a, b);
                case "multiply" -> Calculator.multiply(a, b);
                case "divide" -> Calculator.divide(a, b);
                default -> throw new IllegalStateException("Unknown operation: " + op);
            };
        } catch (ArithmeticException e) {
            sendJson(exchange, 400, errorJson(e.getMessage(), op, aStr, bStr));
            return;
        }

        String json = String.format(
                "{\"operation\":\"%s\",\"a\":%s,\"b\":%s,\"result\":%s}", op, a, b, result);
        sendJson(exchange, 200, json);
    }

    private static void handleHealth(HttpExchange exchange) throws IOException {
        sendJson(exchange, 200, "{\"status\":\"UP\"}");
    }

    private static String errorJson(String message, String op, String a, String b) {
        return String.format(
                "{\"error\":\"%s\",\"operation\":\"%s\",\"a\":%s,\"b\":%s}",
                message, op, quoteOrNull(a), quoteOrNull(b));
    }

    private static String quoteOrNull(String s) {
        return s == null ? "null" : "\"" + s + "\"";
    }

    private static Map<String, String> queryToMap(URI uri) {
        Map<String, String> result = new HashMap<>();
        String query = uri.getQuery();
        if (query == null) return result;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) result.put(kv[0], kv[1]);
        }
        return result;
    }

    private static void sendJson(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
```

**Things to understand, not just copy:**
- `server.createContext(path, handler)` is the plain-Java equivalent of Spring's `@GetMapping` — it maps a URL path to a handler, except you're doing the wiring explicitly instead of it happening via annotations.
- `startServer(int port)` is pulled out as its own method, separate from `main`, *specifically* so your integration test can start a real server on a different port without needing Spring's test machinery — this is the one deliberate design choice that makes the "hit a real endpoint" test (§2.6) possible here.
- Validation happens in two layers, matching the brief's "validate inputs; 400 for invalid/missing parameters": missing params → 400 before parsing, non-numeric params → 400 on `NumberFormatException`, divide-by-zero → 400 via `Calculator.divide`'s `ArithmeticException`.
- `/actuator/health` is hand-written here to return `{"status":"UP"}` — you're not getting it from a library, but it answers the same question the brief cares about ("does Compose's health check have a real endpoint to hit"), and keeping the same path means your Compose healthcheck and the rubric's endpoint table still line up exactly.

### 2.5 Run it locally

```bash
./mvnw package
java -jar target/calculator.jar
```
Visit `http://localhost:8080/api/v1/add?a=2&b=3` in a browser — you should see JSON back. Also try `http://localhost:8080/api/v1/add?a=2` (missing `b`) and `http://localhost:8080/api/v1/divide?a=4&b=0` to see your 400 responses. Stop the server with `Ctrl+C` when done.

### 2.6 Tests (JUnit 5 — unit tests + one HTTP integration test)

Create `src/test/java/com/example/calculator/CalculatorTest.java` — pure unit tests, no server involved:
```java
package com.example.calculator;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CalculatorTest {

    @Test void add() { assertEquals(5.0, Calculator.add(2, 3)); }

    @Test void subtract() { assertEquals(-1.0, Calculator.subtract(2, 3)); }

    @Test void multiply() { assertEquals(6.0, Calculator.multiply(2, 3)); }

    @Test void divide() { assertEquals(2.0, Calculator.divide(10, 5)); }

    @Test
    void divideByZero_throwsArithmeticException() {
        assertThrows(ArithmeticException.class, () -> Calculator.divide(5, 0));
    }
}
```

Create `src/test/java/com/example/calculator/CalculatorServerIntegrationTest.java` — this is this project's stand-in for the brief's "MockMvc hitting a real endpoint" requirement: it starts an *actual* `HttpServer` on a test port and makes *actual* HTTP calls to it using Java's built-in `HttpClient`:
```java
package com.example.calculator;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

class CalculatorServerIntegrationTest {

    private static final int TEST_PORT = 8099;
    private static HttpServer server;
    private static HttpClient client;

    @BeforeAll
    static void startServer() throws Exception {
        server = CalculatorServer.startServer(TEST_PORT);
        client = HttpClient.newHttpClient();
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    @Test
    void add_returnsSumAndOk() throws Exception {
        HttpResponse<String> response = get("/api/v1/add?a=2&b=3");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"result\":5.0"));
    }

    @Test
    void divide_byZero_returns400() throws Exception {
        HttpResponse<String> response = get("/api/v1/divide?a=4&b=0");
        assertEquals(400, response.statusCode());
    }

    @Test
    void health_returnsUp() throws Exception {
        HttpResponse<String> response = get("/actuator/health");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("UP"));
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + TEST_PORT + path))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
```

**Why this counts as the integration test the brief wants:** `CalculatorTest` only proves your arithmetic is correct — it never touches HTTP. `CalculatorServerIntegrationTest` starts the real server and sends real HTTP requests over a real socket, then checks real status codes and response bodies — that's the "hitting a real endpoint" behavior MockMvc gives you in Spring, achieved here with the JDK's own `HttpClient` instead.

Run everything:
```bash
./mvnw test
```
Maven writes results to `target/surefire-reports/*.xml` — this is the "JUnit XML reports that Jenkins can publish" the brief mentions. The Surefire plugin you declared in `pom.xml` (§2.2) handles this automatically.

### 2.7 `.gitignore` and README (do this now, not later)

Create `.gitignore` in the project root:
```
target/
*.class
.idea/
*.iml
.vscode/
.DS_Store
*.log
```

Create a short `README.md` describing how to build/test/run/call the API (you'll flesh this out later — a stub now is fine, since the rubric wants "a complete README" and you'll iterate on it with real commits over time, not write it once at the end).

---

## Phase 3 — Git & GitHub: real version-control practice

### 3.1 Why this matters for your grade

The rubric explicitly checks for **branching + a merge/PR + meaningful commit history** — "one commit the night before" loses marks. So from here on, work like this: small logical commits, and at least one feature on its own branch merged via a Pull Request.

### 3.2 First commit and push

```bash
cd calculator-devops
git init
git add .
git commit -m "Initial plain-Java calculator skeleton with core endpoints and tests"
git branch -M main
git remote add origin https://github.com/<your-username>/calculator-devops.git
git push -u origin main
```

### 3.3 Do at least one feature on a branch

Pick something genuinely separable — e.g., the error-handling/validation improvements, or the README. Example:

```bash
git checkout -b feature/error-handling
# make your changes
git add .
git commit -m "Add structured 400 error body for invalid/missing parameters"
git push -u origin feature/error-handling
```
Then on GitHub: **Pull Requests → New Pull Request → base: main ← compare: feature/error-handling → Create → Merge**.

**Why bother with a branch for something this small?** This is the practice being graded, not the size of the change. In real teams, branches isolate unfinished work from the deployable `main` branch. Doing it once correctly here is what the rubric is checking for.

Keep committing in small chunks as you build each later phase (Dockerfile, Compose file, Jenkinsfile) — don't batch it all into one commit at the end.

---

## Phase 4 — Dockerizing the app

### 4.1 What a container actually is

A Docker **image** is a frozen filesystem snapshot (your app + a minimal OS + a JRE) built in layers from a `Dockerfile`. A **container** is a running instance of that image — isolated from your host machine's filesystem and processes. "It works on my machine" stops being a problem because the container *is* the machine it runs on, everywhere.

### 4.2 Why *multi-stage* builds

If you just `COPY` your source into an image with a full JDK and run `mvn package` inside it, the final image carries the entire JDK, Maven, and all your build-time dependencies — bloated and a bigger attack surface. A **multi-stage** Dockerfile uses one throwaway stage to *build* the JAR, then copies *only the finished JAR* into a second, minimal stage that actually ships. The first stage's junk never reaches the final image.

### 4.3 Write the Dockerfile

Create `Dockerfile` in the project root:

```dockerfile
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
```

**Line-by-line, why each best-practice is there:**
- `COPY --from=build /app/target/calculator.jar app.jar` — named explicitly (not a wildcard) because `pom.xml`'s `<finalName>calculator</finalName>` (§2.2) guarantees that exact filename, and because the plain-Java approach has no extra "sources" or "original" jar variants to accidentally match, unlike some packaging setups.
- `COPY pom.xml .` before `COPY src` — this is **layer caching**: Docker only re-runs `mvn dependency:go-offline` when `pom.xml` changes, not on every source edit, so rebuilds are fast. There's only one test-scoped dependency to fetch here (JUnit), so this layer is tiny and fast either way — but the pattern is worth keeping since real projects have many more dependencies.
- `eclipse-temurin:17-jre-alpine` for the runtime stage — a **JRE** (not full JDK) on a minimal **Alpine Linux** base — this is the "minimal base image" requirement.
- `addgroup`/`adduser`/`USER app` — the container process runs as a **non-root user**, so if the app were ever compromised, the attacker doesn't have root inside the container. This is the "non-root user" rubric point.
- `EXPOSE 8080` — documents which port the container listens on (doesn't actually open it — `-p` at runtime does that; it's metadata for anyone reading the Dockerfile and for Compose).

### 4.4 `.dockerignore`

Create `.dockerignore` (prevents your build context from including junk, makes builds faster):
```
target/
.git/
.idea/
*.iml
.vscode/
```

### 4.5 Build and run it yourself, manually, before Jenkins touches it

```bash
docker build -t calculator:local .
docker run -p 8080:8080 calculator:local
```
Visit `http://localhost:8080/api/v1/add?a=2&b=3` again — now it's coming from inside a container. Stop with `Ctrl+C`.

**Why do this manually first?** If Jenkins later fails at the "Docker Build" stage, you need to already know whether the problem is your Dockerfile or your Jenkins config. Never debug two unknowns at once.

Commit the Dockerfile and `.dockerignore`.

---

## Phase 5 — Docker Compose: multi-container deployment

### 5.1 Why Compose, when you already have `docker run`

`docker run` starts one container. The brief wants **two** components talking to each other (your API + an Nginx reverse proxy in front of it) started together, networked together, with health checks and restart policies — that's exactly what Compose's YAML file declares in one place, started with one command.

**What's a reverse proxy, and why put one in front of your API?** Nginx will sit between the outside world and your calculator container, forwarding requests to it. In real deployments this is used for TLS termination, load balancing across replicas, and hiding your app's internals behind one public entry point. Here, it's mainly to demonstrate the *pattern* — but understand it, because the demo will ask.

### 5.2 Nginx config

Create `nginx/default.conf`:
```nginx
server {
    listen 80;

    location / {
        proxy_pass http://calculator:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```
Note `http://calculator:8080` — `calculator` isn't a real hostname on the internet, it's the **service name** you'll give your app container in Compose. Compose creates an internal DNS so containers can reach each other by service name over the network it creates for them.

### 5.3 `docker-compose.yml`

```yaml
version: "3.9"

services:
  calculator:
    build: .
    image: <your-dockerhub-username>/calculator-devops:latest
    container_name: calculator-app
    expose:
      - "8080"
    environment:
      - JAVA_TOOL_OPTIONS=-Xmx256m
    healthcheck:
      test: ["CMD", "wget", "-qO-", "http://localhost:8080/actuator/health"]
      interval: 15s
      timeout: 5s
      retries: 5
      start_period: 20s
    restart: unless-stopped
    networks:
      - calc-net

  nginx:
    image: nginx:alpine
    container_name: calculator-proxy
    ports:
      - "8080:80"
    volumes:
      - ./nginx/default.conf:/etc/nginx/conf.d/default.conf:ro
    depends_on:
      calculator:
        condition: service_healthy
    restart: unless-stopped
    networks:
      - calc-net

networks:
  calc-net:
    driver: bridge
```

**Why each piece:**
- `environment: JAVA_TOOL_OPTIONS=-Xmx256m` — this is the "environment variables" requirement: a standard JVM variable that caps the container's heap at 256MB without touching any code. The JVM picks it up automatically on startup; you'll see it logged as `Picked up JAVA_TOOL_OPTIONS` when the container starts.
- `expose: "8080"` on the app (not `ports:`) — the app is reachable *only inside* the Compose network, not directly from your host. Only Nginx is published to the host (`ports: "8080:80"`), which is the whole point of a reverse proxy — one public door.
- `healthcheck` — Docker runs this command inside the container periodically; `docker ps` will show `(healthy)`/`(unhealthy)`. This is literally what the rubric means by "health check (using `/actuator/health`)".
- `depends_on: condition: service_healthy` — Nginx won't be marked ready until the calculator app's healthcheck passes, avoiding the classic "proxy started before backend is up" race.
- `restart: unless-stopped` — the required restart policy; containers auto-restart if they crash, but not if *you* deliberately stop them.
- A custom `networks:` block instead of Compose's default network is the explicit "define a network" requirement.

### 5.4 Run the whole stack

```bash
docker compose up -d --build
docker ps                      # should show both containers, calculator as (healthy)
curl http://localhost:8080/api/v1/add?a=2&b=3     # now going through nginx → app
docker compose logs -f         # watch logs; Ctrl+C to stop watching
docker compose down            # tear down when done testing
```

Commit `docker-compose.yml`, `nginx/default.conf`.

---

## Phase 6 — Jenkins: wiring it all into one automated pipeline

### 6.1 What Jenkins actually does here

Jenkins is a long-running server that watches your GitHub repo. On a trigger (a push), it checks out your latest code and runs a sequence of **stages** you define in a `Jenkinsfile` — which is just a script, checked into your repo like any other file, written in Groovy using Jenkins' "declarative pipeline" syntax.

### 6.2 Install Jenkins locally

Easiest path — run Jenkins itself in Docker (it needs access to the host's Docker daemon so it can build/push images, so we mount the docker socket):

```bash
docker volume create jenkins_home

docker run -d --name jenkins \
  -p 8081:8080 -p 50000:50000 \
  -v jenkins_home:/var/jenkins_home \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -u root \
  jenkins/jenkins:lts
```
(Port `8081` on the host, because `8080` is already used by your app.)

Get the initial admin password:
```bash
docker exec jenkins cat /var/jenkins_home/secrets/initialAdminPassword
```
Open `http://localhost:8081`, paste the password, choose **"Install suggested plugins"**, create your admin user.

Since Jenkins will run `docker build`/`docker push` itself, it needs the Docker CLI inside its own container:
```bash
docker exec -u root jenkins sh -c "apt-get update && apt-get install -y docker.io"
```

### 6.3 Add credentials Jenkins will need

**Docker Hub login** (so Jenkins can `docker push` on your behalf):
Jenkins dashboard → **Manage Jenkins → Credentials → System → Global credentials → Add Credentials**
- Kind: *Username with password*
- Username: your Docker Hub username
- Password: a Docker Hub **access token** (Docker Hub → Account Settings → Security → New Access Token — don't use your real account password)
- ID: `dockerhub-creds` (you'll reference this exact ID in the Jenkinsfile)

**Why "Jenkins Credentials store, never hard-code"?** If you type your password into the Jenkinsfile, it's sitting in plaintext in your public GitHub repo forever (even if you delete it later, it's still in git history). The Credentials store keeps secrets out of version control and injects them only at runtime.

**Email notification** — Manage Jenkins → System → configure an SMTP server (if you have access to one, e.g. a Gmail app password) under "Extended E-mail Notification" or the basic "E-mail Notification" section. Use your student email as the recipient.

### 6.4 Create the pipeline job

Dashboard → **New Item → Pipeline** → name it `calculator-devops-pipeline`.
In configuration:
- **Build Triggers** → check **GitHub hook trigger for GITScm polling** (webhook — preferred) *or* **Poll SCM** with a schedule like `H/5 * * * *` (every ~5 min — acceptable fallback if your Jenkins isn't reachable from the internet for a real webhook, which is the common case on a laptop).
- **Pipeline → Definition**: "Pipeline script from SCM" → SCM: Git → your repo URL → Branch: `*/main` → Script Path: `Jenkinsfile`.

**Webhook note:** GitHub needs to reach your Jenkins over the internet to call a webhook — if Jenkins is only on `localhost`, GitHub can't reach it. For local development, use a tunnel like `ngrok http 8081` to get a temporary public URL, then add that URL + `/github-webhook/` under your GitHub repo's **Settings → Webhooks**. If you can't get a tunnel working reliably, Poll SCM is explicitly acceptable per the brief — just say so honestly in your report rather than faking a webhook screenshot.

### 6.5 Write the `Jenkinsfile`

Create this in your project root:

```groovy
pipeline {
    agent any

    environment {
        DOCKERHUB_CREDS = credentials('dockerhub-creds')
        IMAGE_NAME = "<your-dockerhub-username>/calculator-devops"
        IMAGE_TAG  = "${env.BUILD_NUMBER}"
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build') {
            steps {
                sh './mvnw clean package -DskipTests'
            }
        }

        stage('Test') {
            steps {
                sh './mvnw test'
            }
            post {
                always {
                    junit 'target/surefire-reports/*.xml'
                }
            }
        }

        stage('Docker Build') {
            steps {
                sh "docker build -t ${IMAGE_NAME}:${IMAGE_TAG} -t ${IMAGE_NAME}:latest ."
            }
        }

        stage('Push') {
            steps {
                sh "echo $DOCKERHUB_CREDS_PSW | docker login -u $DOCKERHUB_CREDS_USR --password-stdin"
                sh "docker push ${IMAGE_NAME}:${IMAGE_TAG}"
                sh "docker push ${IMAGE_NAME}:latest"
            }
        }

        stage('Deploy') {
            steps {
                sh 'docker compose down || true'
                sh 'docker compose up -d --build'
                sh 'sleep 15'
                sh 'curl -f http://localhost:8080/actuator/health || exit 1'
            }
        }
    }

    post {
        success {
            mail to: '<your-student-email>',
                 subject: "SUCCESS: Pipeline #${env.BUILD_NUMBER}",
                 body: "The calculator-devops pipeline succeeded. Image: ${IMAGE_NAME}:${IMAGE_TAG}"
        }
        failure {
            mail to: '<your-student-email>',
                 subject: "FAILED: Pipeline #${env.BUILD_NUMBER}",
                 body: "The calculator-devops pipeline failed. Check Jenkins console output."
        }
    }
}
```

**Understand these before the demo, since you'll be asked:**
- `environment { DOCKERHUB_CREDS = credentials('dockerhub-creds') }` — Jenkins automatically splits a "username/password" credential into `DOCKERHUB_CREDS_USR` and `DOCKERHUB_CREDS_PSW` env variables, available only inside this pipeline run, never logged in plaintext.
- `junit 'target/surefire-reports/*.xml'` in `post { always { ... } }` — this runs whether tests pass *or* fail, so Jenkins always publishes a test report; the stage itself still fails the build if tests fail (that's `mvn test`'s own exit code).
- `docker login ... --password-stdin` — piping the password in avoids it appearing in `docker history` or process listings, which plain `-p yourpassword` would expose.
- **"Re-runnable and idempotent"** — `docker compose down || true` before `up -d` means re-running the pipeline cleanly replaces the running stack instead of erroring because a container with that name already exists. The `|| true` stops a "nothing to tear down" case (first ever run) from failing the stage.
- The `Deploy` stage's `curl -f .../actuator/health` is what actually *proves* the deployment worked, not just that containers started.

Commit the Jenkinsfile, then push.

### 6.6 Run it

Push any small change to `main` (or trigger the job manually the first time via **Build Now** to confirm it works before relying on the trigger). Watch **Console Output** — this is where you'll debug almost everything. Common first-run issues and what they mean:
- `mvnw: Permission denied` → run `chmod +x mvnw` locally and commit that permission change (`git update-index --chmod=+x mvnw`).
- `docker: command not found` inside Jenkins → the docker CLI install from §6.2 didn't take; re-run it.
- `permission denied while trying to connect to the Docker daemon socket` → the `-u root` and `-v /var/run/docker.sock:/var/run/docker.sock` flags from §6.2 need to both be present when Jenkins' container was started.

Once it's green end-to-end: screenshot the green pipeline stages view, the email you received, `docker ps` output, and two or three sample API responses (including a divide-by-zero 400).

---

## Phase 7 — Report, screenshots, and the live demo

### 7.1 The report (2–3 pages, PDF)

Structure it around what you *built and understood*, not a restatement of the brief:
1. **Architecture diagram** — draw the Phase 0 diagram above (or your own) showing GitHub → Jenkins → Docker Hub → Compose stack (app + nginx).
2. **Stage-by-stage description** — one short paragraph per Jenkinsfile stage, in your own words.
3. **Challenges faced** — be specific and honest (e.g., "webhook couldn't reach local Jenkins, switched to Poll SCM and documented why" is a *better* report line than silence, since the brief explicitly allows Poll SCM).

### 7.2 Before the demo, rehearse explaining out loud

The brief states you must be able to explain every part during the demo — "use of AI assistants is allowed for learning, but you must understand and be able to explain." Run through these out loud once, from memory, without looking at the file:
- Why multi-stage Docker build (two stages, what each discards/keeps).
- Why the app container isn't directly port-published, only nginx is.
- What `credentials('dockerhub-creds')` does and why it's safer than hard-coding.
- What happens, stage by stage, when you `git push`.

### 7.3 Demo-day checklist

- [ ] Jenkins reachable and the job configured, before class starts.
- [ ] Make a small, visible code change (e.g., tweak a response field or add a log line).
- [ ] Push it live, let the class watch Jenkins trigger and run through all stages.
- [ ] Show `docker ps` with the new image/tag running.
- [ ] Hit the live endpoint to prove the new behavior deployed.
- [ ] Have your report and screenshots ready as backup, but remember: **a live, working deployment is required — screenshots alone don't earn the deployment marks.**

---

## Suggested order to actually do the work in

Given the rubric weighting (CI/CD pipeline is worth the most: 3/10), don't let Jenkins be what you rush at the end:

1. Plain-Java calculator app + tests, committed in small chunks, one branch/PR done (Phases 2–3).
2. Dockerfile, verified with manual `docker build`/`run` (Phase 4).
3. Compose stack, verified manually (Phase 5).
4. Jenkins installed and the *first three stages* (Checkout/Build/Test) working (Phase 6, start).
5. Docker Build/Push stages working against your real Docker Hub account.
6. Deploy stage + email notifications + trigger (webhook or Poll SCM).
7. Full green run, screenshots, report, demo rehearsal.

Spread this across the mid-term break window rather than one sitting — your commit history is graded, and a realistic spread of commits over days is also just a better way to catch problems early.
