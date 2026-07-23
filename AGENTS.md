# AGENTS.md

Instructions for AI coding agents working on the Conductor codebase. See [CLAUDE.md](CLAUDE.md) for documentation writing rules.

## Project Overview

Conductor is an open-source, distributed workflow orchestration engine. Pluggable architecture with interface-based abstractions for persistence, queuing, and indexing. Java 21, Gradle, Spring Boot 3.3.x.

**Main class:** `com.netflix.conductor.Conductor` — `@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class, MongoAutoConfiguration.class})`
**Component scan:** `com.netflix.conductor`, `io.orkes.conductor`, `dev.agentspan`, `org.conductoross.conductor`

## Setup & Commands

| Command | Description |
|---------|-------------|
| `./gradlew build` | Compile all modules (CI skips tests with `-x test -x :conductor-test-harness:test`) |
| `./gradlew test -x :conductor-test-harness:test` | Run all unit tests |
| `./gradlew :conductor-<module>:test` | Run tests for one module (e.g. `:conductor-core:test`) |
| `./gradlew spotlessApply` | **Required before every commit** |
| `./gradlew :conductor-server:bootRun` | Dev server on `:8080` (swagger at `/swagger-ui/index.html`) |
| `./gradlew jacocoAggregatedReport` | Aggregate coverage (run tests first; does NOT trigger tests) |
| `./gradlew :conductor-core:benchmarkScripts` | JS engine benchmark runner |

```shell
# Server (config via CONDUCTOR_CONFIG_FILE env var or Spring props)
cd server && ../gradlew bootRun

# Indexing backend at build time (avoids Lucene conflicts):
./gradlew build -PindexingBackend=os3
# Accepted: elasticsearch|es7 (default), elasticsearch8|es8, opensearch|os, opensearch3|os3
```

**Gradle JVM:** `-Xmx4g` (set in `gradle.properties`). Current version: `3.30.2-rc1`.

## Module Architecture

All subprojects automatically prefixed `conductor-` (by `settings.gradle`). Scheduler modules live under `scheduler/` but are top-level Gradle modules (e.g. `:conductor-scheduler-core` → `scheduler/core`).

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
| `annotations-processor/` | Protogen codegen (annotated POJOs → .proto) |
| `conductor-clients/` | Polyglot client wrappers (ignored by CI: `paths-ignore`) |
| `ui-next/` | React-based UI (pnpm, Vite, Vitest, Playwright) |

DAO interfaces live in `core`. Implementations in their own module (`redis-persistence/`, `postgres-persistence/`, `mysql-persistence/`, `cassandra-persistence/`, `sqlite-persistence/`, `es7-persistence/`, `es8-persistence/`, `os-persistence/`, `os-persistence-v2/`, `os-persistence-v3/`).

## Testing

- **Avoid mocks.** Prefer real implementations + TestContainers.
- JUnit 5 platform. `core` and `test-harness` also use Spock (Groovy).
- TestContainers Docker API version forced to `1.44` in CI via `~/.docker-java.properties`.
- Tests use `systemProperty 'dockerconfig.source', 'autoIgnoringUserProperties'` (no user-level Docker client config overrides).
- Each module's `test` task finalizes with `jacocoTestReport`.

```shell
# Single module (fastest feedback)
./gradlew :conductor-core:test

# CI skips heavy persistence tests (cassandra, es6/7/8, mysql, opensearch) on PRs
# unless their source changed (detected by dorny/paths-filter). Redis, postgres, sqlite always run.
# Local equivalent:
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

### UI tests (`ui-next/`)

```shell
cd ui-next
pnpm install --frozen-lockfile
pnpm typecheck      # tsc --noEmit
pnpm test           # vitest
pnpm test:e2e       # Playwright (requires server)
pnpm build          # vite build
```

UI uses **ESLint** + **Prettier** (not Spotless). CI runs: `format → lint → typecheck → test → build`.

## Code Style

- Spotless with Google Java Format (AOSP variant), `removeUnusedImports`, custom import order: `java`, `javax`, `org`, `com.netflix`, ``, `\#com.netflix`, `\#`
- License header enforced (see `licenseheader.txt`)
- Lombok 1.18.42 throughout (`@Slf4j`, `@Data`, `@Builder`, etc.)
- No emojis in code, logs, or comments
- **Pre-commit hook:** `ln -s ../../hooks/pre-commit .git/hooks/pre-commit`

## Dependency Management

### PINNED — do not bump without reading pinned comments and issue #964

| Dependency | Constraint | Why |
|---|---|---|
| `protobuf-java` | 3.x | 4.x + GraalVM polyglot 25.x breaks Gradle resolution |
| `protoc` | 3.25.5 | Must match protobuf-java 3.x used by grpc-protobuf |
| `org.graalvm.*` | all same `revGraalVM` | Mixing versions causes polyglot/Truffle runtime errors |
| `conductor-client` (test-harness) | 5.0.1 | Fat JAR classpath conflict; resolved via stripped JAR task |
| `awaitility` (functionalTest) | 4.2.0 | e2e uses `pollInterval(Duration)` (3.x in main scope) |
| `jettison` | strictly 1.5.4 | No validated higher version |

### Version floors (CVE/compat — freely bumpable)

```groovy
// Security: <CVE> — <reason>
// Compat: <reason>
```

### Other constraints

- Jackson: all modules aligned to 2.17.0
- Tomcat: 10.1.54
- GraalVM: all artifacts share `revGraalVM` from `dependencies.gradle`
- Jedis: overridden to `revJedis` (6.0.0) — Spring Boot BOM would downgrade

### Protobuf codegen

`conductor-grpc` generates Java from `.proto`. The `protogen` annotation processor (`conductor-annotations-processor`) converts annotated POJOs to `.proto`. Build order dependency:

```
compileJava.dependsOn(':conductor-common:protogen')
```

Without this, gRPC builds fail with missing proto files.

## Java References

Say "Java 21+" only — never link to a specific distribution (Adoptium, Temurin, etc.).
