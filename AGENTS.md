# AGENTS.md

Instructions for AI coding agents working on the Conductor codebase. See [CLAUDE.md](CLAUDE.md) for documentation writing rules.

## Project Overview

Distributed workflow orchestration engine. Java 21, Gradle, Spring Boot 3.3.x.

**Main class:** `com.netflix.conductor.Conductor` — `@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class, MongoAutoConfiguration.class})`
**Component scan:** `com.netflix.conductor`, `io.orkes.conductor`, `dev.agentspan`, `org.conductoross.conductor`

## Java Build & Test

| Command | Description |
|---------|-------------|
| `./gradlew build -x test -x :conductor-test-harness:test` | Compile all modules (CI equivalent) |
| `./gradlew spotlessApply` | **Required before every commit** — formats Java/Groovy |
| `./gradlew :conductor-server:bootRun` | Dev server on `:8080` (swagger at `/swagger-ui/index.html`) |
| `./gradlew :conductor-core:benchmarkScripts` | JS engine benchmark runner |

Config via `CONDUCTOR_CONFIG_FILE` env var or Spring properties.

### Testing

- **Avoid mocks.** Prefer real implementations + TestContainers.
- JUnit 5 platform. `core` and `test-harness` also use Spock (Groovy).
- TestContainers Docker API version forced to `1.44` in CI via `~/.docker-java.properties`.
- Each module's `test` task finalizes with `jacocoTestReport`.

```shell
# Single module (fastest feedback)
./gradlew :conductor-core:test

# Run all unit tests (skips heavy persistence and test-harness)
./gradlew test -x :conductor-test-harness:test \
  -x :conductor-cassandra-persistence:test \
  -x :conductor-es6-persistence:test \
  -x :conductor-es7-persistence:test \
  -x :conductor-es8-persistence:test \
  -x :conductor-mysql-persistence:test \
  -x :conductor-os-persistence:test \
  -x :conductor-os-persistence-v2:test \
  -x :conductor-os-persistence-v3:test

# Integration tests (requires Docker)
./gradlew :conductor-test-harness:test

# File-storage backend integration tests (tagged, requires Docker)
./gradlew :conductor-test-harness:fileStorageIntegrationTest

# E2E functional tests against embedded server
./gradlew :conductor-test-harness:functionalTest
```
The `functionalTest` uses a dedicated source set + classpath. It excludes `SetVariableTests.testAllFast`.

### Indexing backend at build time

Avoids Lucene conflicts between ES and OS:

```shell
./gradlew build -PindexingBackend=os3
# Accepted: elasticsearch|es7 (default), elasticsearch8|es8, opensearch|os, opensearch3|os3
```

## Module Architecture

All subprojects auto-prefixed `conductor-` (by `settings.gradle`). Scheduler modules live under `scheduler/` but are top-level Gradle modules (e.g. `:conductor-scheduler-core` → `scheduler/core`).

| Path → Module | Role |
|---------------|------|
| `core/` → `conductor-core` | Interfaces, domain models, business logic, GraalVM script engine |
| `common/` → `conductor-common` | Shared models + protogen annotation source |
| `rest/` → `conductor-rest` | REST controllers (`com.netflix.conductor.rest.controllers`) |
| `server/` → `conductor-server` | Spring Boot app (only module with a `bootJar`) |
| `grpc/` → `conductor-grpc` | Proto definitions + generated gRPC stubs |
| `grpc-server/` / `grpc-client/` | gRPC implementations |
| `ai/` → `conductor-ai` | AI/LLM integration tasks |
| `agentspan/` → `conductor-agentspan` | AgentSpan runtime (activated via `conductor.integrations.ai.enabled=true`) |
| `annotations-processor/` | Protogen codegen (annotated POJOs → `.proto`) |
| `conductor-clients/` | Polyglot client wrappers (CI ignores via `paths-ignore`) |
| `ui-next/` | React UI (npm, Vite, Vitest, Playwright) |

DAO interfaces live in `core`. Implementations in their own module (`redis-persistence/`, `postgres-persistence/`, `mysql-persistence/`, `cassandra-persistence/`, `sqlite-persistence/`, `es7-persistence/`, `es8-persistence/`, `os-persistence/`, `os-persistence-v2/`, `os-persistence-v3/`).

### Protobuf codegen

`conductor-grpc` generates Java from `.proto`. The `protogen` annotation processor converts annotated POJOs to `.proto`. Build order dependency:

```
compileJava.dependsOn(':conductor-common:protogen')
```

Without this, gRPC builds fail with missing proto files.

## UI (`ui-next/`)

Use **npm**, not pnpm. The lockfile is `package-lock.json`.

```shell
cd ui-next
npm install
npm run typecheck          # tsc --noEmit
npm run test               # vitest (unit)
npm run test -- --coverage # vitest with coverage
npm run test:e2e           # Playwright (mocked backend, headless)
npm run lint               # ESLint
npm run build              # Vite production build
```

UI uses **ESLint** + **Prettier** (not Spotless). CI order: `prettier:check → lint → typecheck → test → build`.

### UI E2E tests

Playwright tests in `ui-next/e2e/` mock the Conductor backend with `page.route()` — no server required for basic E2E. Integration tests in `ui-next/e2e/integration/` run against a real Docker backend.

```shell
# Unit tests
npm run test

# E2E (mocked, no server needed)
npm run test:e2e

# E2E (integration, requires Docker)
npm run test:e2e:integration
```

## Code Style

- Spotless with Google Java Format (AOSP variant), `removeUnusedImports`, import order: `java`, `javax`, `org`, `com.netflix`, ``, `\#com.netflix`, `\#`
- License header enforced (see `licenseheader.txt`)
- Lombok 1.18.42 throughout (`@Slf4j`, `@Data`, `@Builder`, etc.)
- No emojis in code, logs, or comments
- **Pre-commit hook:** `ln -s ../../hooks/pre-commit .git/hooks/pre-commit` (runs `spotlessApply`)

## Dependency Management

### PINNED — do not bump without reading issue #964

| Dependency | Constraint | Why |
|---|---|---|
| `protobuf-java` | 3.x | 4.x + GraalVM polyglot 25.x breaks Gradle resolution |
| `protoc` | 3.25.5 | Must match protobuf-java 3.x used by grpc-protobuf |
| `org.graalvm.*` | all same `revGraalVM` | Mixing versions causes polyglot/Truffle runtime errors |
| `conductor-client` (test-harness) | 5.0.1 | Fat JAR classpath conflict; resolved via stripped JAR task |
| `awaitility` (functionalTest) | 4.2.0 | e2e uses `pollInterval(Duration)` (3.x in main scope) |
| `jettison` | strictly 1.5.4 | No validated higher version |

### Other constraints from `build.gradle`

- Jackson: all modules aligned to 2.17.0
- Tomcat: 10.1.54
- GraalVM: all artifacts share `revGraalVM` from `dependencies.gradle`
- Jedis: overridden to `revJedis` (6.0.0) — Spring Boot BOM would downgrade

## Java References

Say "Java 21+" only — never link to a specific distribution (Adoptium, Temurin, etc.).
