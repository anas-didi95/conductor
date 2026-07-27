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
| `./gradlew :conductor-core:benchmarkScripts` | JS engine benchmark runner (GraalJS vs Rhino vs Javet/V8) |
| `./gradlew build jacocoAggregatedReport` | Full build + aggregated JaCoCo coverage (does not trigger tests itself) |

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
  -x :conductor-os-persistence-v3:test \
  -x :conductor-scheduler-cassandra-persistence:test \
  -x :conductor-scheduler-mysql-persistence:test

# Integration tests (requires Docker)
./gradlew :conductor-test-harness:test

# File-storage backend integration tests (tagged, requires Docker)
./gradlew :conductor-test-harness:fileStorageIntegrationTest

# E2E functional tests against embedded server
./gradlew :conductor-test-harness:functionalTest
```
The `functionalTest` uses a dedicated source set + classpath. It excludes `SetVariableTests.testAllFast`.

### E2E (docker-compose based, requires Docker)

E2E tests live in `e2e/` and run via shell scripts that spin up full Conductor + backend via docker-compose:

```shell
# Default: Redis + ES8
./e2e/run_tests-es8.sh
# Other backends: run_tests-postgres.sh, run_tests-mysql.sh, etc.
```

Pass `-PrunE2E` to the underlying Gradle invocation to activate the e2e test suite.

### Indexing backend at build time

Avoids Lucene conflicts between ES and OS by selecting which persistence module `conductor-server` depends on:

```shell
./gradlew build -PindexingBackend=os3
# Accepted: elasticsearch|es7 (default), elasticsearch8|es8, opensearch|os, opensearch3|os3
```

## Module Architecture

All subprojects auto-prefixed `conductor-` (by `settings.gradle`). Scheduler modules are nested under `scheduler/` but are top-level Gradle modules mapped to their directory:

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
| `annotations/` | Protogen annotations |
| `annotations-processor/` | Protogen codegen (annotated POJOs → `.proto`) via JavaPoet + Handlebars |
| `common-persistence/` | Shared persistence utilities |
| `conductor-clients/` | Polyglot client wrappers (CI ignores via `paths-ignore`) |
| `scheduler/core/` → `conductor-scheduler-core` | Workflow scheduler |
| `scheduler/*-persistence/` → `conductor-scheduler-*-persistence` | Scheduler persistence backends |
| `ui-next/` | React UI (Vite, Vitest, Playwright) |

DAO interfaces live in `core`. Implementations in their own module (`redis-persistence/`, `postgres-persistence/`, `mysql-persistence/`, `cassandra-persistence/`, `sqlite-persistence/`, `es7-persistence/`, `es8-persistence/`, `os-persistence/`, `os-persistence-v2/`, `os-persistence-v3/`).

### Protobuf codegen

Two-step process:
1. **Protogen** (`conductor-annotations-processor`): converts annotated POJOs in `conductor-common` to `.proto` files. Task `:conductor-common:protogen` runs as a `JavaExec` with main class `com.netflix.conductor.annotationsprocessor.protogen.ProtoGenTask`.
2. **Protoc** (`conductor-grpc`): compiles `.proto` to Java gRPC stubs. Uses `protoc:3.25.5` and `protoc-gen-grpc-java`.

Build order dependency — without this, gRPC builds fail with missing proto files:
```
compileJava.dependsOn(':conductor-common:protogen')
```

Both `:conductor-grpc:generateProto` and `:conductor-grpc:compileJava` depend on `:conductor-common:protogen`.

## UI (`ui-next/`)

Use **npm**. The lockfile is `package-lock.json`.

```shell
cd ui-next
npm install
npm run typecheck          # tsc --noEmit
npm test                   # vitest (unit)
npm test -- --coverage     # vitest with coverage
npm run test:e2e           # Playwright (mocked backend, headless)
npm run lint               # ESLint
npm run build              # Vite production build
```

UI uses **ESLint** + **Prettier** (not Spotless). CI order: `prettier:check → lint → typecheck → test → build`.

### UI E2E tests

Playwright tests in `ui-next/e2e/` mock the Conductor backend with `page.route()` — no server required for basic E2E. Integration tests in `ui-next/e2e/integration/` run against a real Docker backend. Snapshot tests run via Docker Compose:

```shell
# Unit tests
npm test

# E2E (mocked, no server needed)
npm run test:e2e

# E2E (integration, requires Docker)
npm run test:e2e:integration

# E2E snapshots (Docker)
npm run test:e2e:snapshots
npm run test:e2e:snapshots:update  # update snapshots
```

## Code Style

- Spotless with Google Java Format (AOSP variant), `removeUnusedImports`, import order: `java`, `javax`, `org`, `com.netflix`, ``, `\#com.netflix`, `\#`
- License header enforced (see `licenseheader.txt`)
- Lombok 1.18.42 throughout (`@Slf4j`, `@Data`, `@Builder`, etc.)
- No emojis in code, logs, or comments
- **Pre-commit hook:** `ln -s ../../hooks/pre-commit .git/hooks/pre-commit` (runs `spotlessApply`)
- Spotless is applied to all subprojects except `conductor-grpc`. Groovy formatting applied to `cassandra-persistence`, `core`, `redis-concurrency-limit`, `test-harness`.

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

### Security CVE overrides (in `build.gradle`)

| Dependency | Minimum version | CVE |
|---|---|---|
| `org.lz4:lz4-java` | 1.8.1 | CVE-2025-12183 |
| `org.apache.tika:tika-core` | 3.2.2 | CVE-2025-66516 |
| `commons-beanutils:commons-beanutils` | 1.11.0 | CVE-2025-48734 |
| `com.microsoft.sqlserver:mssql-jdbc` | 12.8.2.jre11 | CVE-2025-59250 |
| `org.apache.commons:commons-lang3` | 3.18.0 | Required by Testcontainers/commons-compress |

### Other constraints

- Jackson: all modules aligned to 2.17.0 via resolution strategy
- Tomcat: 10.1.54
- GraalVM: all artifacts share `revGraalVM` from `dependencies.gradle`
- Jedis: overridden to `revJedis` (6.0.0) — Spring Boot BOM would downgrade
- Spring Boot BOM also overridden: groovy (4.0.21), Testcontainers (1.21.4), Elasticsearch version
- Logging: Log4j2 throughout (logback excluded via configuration)

## Java References

Say "Java 21+" only — never link to a specific distribution (Adoptium, Temurin, etc.).
