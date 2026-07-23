# AGENTS.md

Instructions for AI coding agents working on the Conductor codebase.

## Project Overview

Conductor is an open-source, distributed workflow orchestration engine. It uses a pluggable architecture with interface-based abstractions for persistence, queuing, and indexing. Built with Java 21, Gradle, and Spring Boot 3.3.x.

## Setup Commands

| Command | Description |
|---------|-------------|
| `./gradlew build` | Build all modules (CI excludes test-harness tests; see below) |
| `./gradlew test` | Run all unit tests |
| `./gradlew :conductor-<module>:test` | Run tests for a specific module (e.g. `:conductor-core:test`) |
| `./gradlew spotlessApply` | Apply code formatting — required after every code change |
| `./gradlew clean build` | Clean and rebuild |
| `./gradlew :conductor-server:bootRun` | Start the dev server (listens on `:8080`) |
| `./gradlew jacocoAggregatedReport` | Aggregate JaCoCo coverage (run `build` first) |

> **Always run `./gradlew spotlessApply` before committing.**

### Running the server

```shell
cd server && ../gradlew bootRun
# http://localhost:8080, swagger at /swagger-ui/index.html
```

Config via env var `CONDUCTOR_CONFIG_FILE` or Spring properties. See `docker/server/config/` for example configs (Redis+ES7 default, Postgres, MySQL, etc.).

### Indexing backend build-time selection

The server module resolves the indexing backend at **build time** to avoid Lucene conflicts between ES and OpenSearch:

```shell
./gradlew build -PindexingBackend=os3
# Accepted values: elasticsearch|es7 (default), elasticsearch8|es8, opensearch|os, opensearch3|os3
```

## Module Architecture

All subprojects are prefixed with `conductor-` (enforced by `settings.gradle`). Use this prefix in Gradle task references.

### Core modules

| Module | Path | Role |
|--------|------|------|
| `conductor-core` | `core/` | Interfaces, domain models, core business logic, GraalVM script engine |
| `conductor-common` | `common/` | Shared metadata and model classes |
| `conductor-rest` | `rest/` | REST controllers (`com.netflix.conductor.rest.controllers`) |
| `conductor-server` | `server/` | Spring Boot app — main class `com.netflix.conductor.Conductor` |
| `conductor-grpc` | `grpc/` | Protobuf definitions + gRPC stubs (generated code) |
| `conductor-grpc-server` | `grpc-server/` | gRPC server implementation |
| `conductor-grpc-client` | `grpc-client/` | gRPC client |

### Persistence modules

DAO interfaces live in `core`. Implementations go in their respective module (e.g. `postgres-persistence`, `redis-persistence`).

| Module | Path |
|--------|------|
| `conductor-redis-persistence` | `redis-persistence/` |
| `conductor-postgres-persistence` | `postgres-persistence/` |
| `conductor-mysql-persistence` | `mysql-persistence/` |
| `conductor-cassandra-persistence` | `cassandra-persistence/` |
| `conductor-sqlite-persistence` | `sqlite-persistence/` |
| `conductor-es7-persistence` | `es7-persistence/` |
| `conductor-es8-persistence` | `es8-persistence/` |
| `conductor-os-persistence` / `-v2` / `-v3` | `os-persistence/`, `os-persistence-v2/`, `os-persistence-v3/` |

### Scheduler submodules

Scheduler modules are nested under `scheduler/` but named as top-level Gradle modules:

```groovy
project(':conductor-scheduler-core').projectDir = file('scheduler/core')
project(':conductor-scheduler-postgres-persistence').projectDir = file('scheduler/postgres-persistence')
// etc.
```

### Other modules

- `conductor-ai` (`ai/`) — AI/LLM integration tasks
- `conductor-agentspan` (`agentspan/`) — Embedded AgentSpan runtime (activated via `conductor.integrations.ai.enabled=true`)
- `conductor-annotations` + `conductor-annotations-processor` — Protogen codegen (converts annotated POJOs to `.proto`)
- `conductor-test-harness` (`test-harness/`) — Integration tests using TestContainers
- `conductor-e2e` (`e2e/`) — End-to-end tests
- `conductor-client` modules in `conductor-clients/` — polyglot client wrappers (ignored by CI: `paths-ignore: ["conductor-clients/**"]`)
- `ui-next/` — React-based UI (pnpm, Vite, Vitest, Playwright)

## Testing

### Testing philosophy

- **Avoid mocks.** Use real implementations and TestContainers where possible.
- Tests use JUnit 5 platform. `core` and `test-harness` also use Spock (Groovy).
- TestContainers Docker API version is forced to 1.44 in CI via `~/.docker-java.properties`.

### CI test execution order and quirks

CI runs three separate jobs:
1. **`build`** — compiles everything (`./gradlew build -x :conductor-test-harness:test -x test`)
2. **`unit-test`** — runs `./gradlew test -x :conductor-test-harness:test`
3. **`test-harness`** — runs `./gradlew :conductor-test-harness:test`

On PRs, heavy persistence tests (cassandra, es6/7/8, mysql, opensearch) are **skipped** unless their source changed (detected by `dorny/paths-filter`).

### Running tests locally

```shell
# Single module (fastest feedback)
./gradlew :conductor-core:test

# All unit tests except test-harness
./gradlew test -x :conductor-test-harness:test

# Integration tests (requires Docker)
./gradlew :conductor-test-harness:test

# File-storage backend integration tests (tagged, requires Docker)
./gradlew :conductor-test-harness:fileStorageIntegrationTest

# E2E functional tests against embedded server
./gradlew :conductor-test-harness:functionalTest
```

The `functionalTest` task runs e2e tests via a dedicated source set with separate classpath config. It excludes one heavy load test (`SetVariableTests.testAllFast`).

### UI tests

```shell
cd ui-next
pnpm install --frozen-lockfile
pnpm typecheck      # tsc --noEmit
pnpm test           # vitest
pnpm test:e2e       # Playwright (requires server)
pnpm build          # vite build
```

## Code Style

- Spotless with Google Java Format (AOSP variant), `removeUnusedImports`, custom import order: `java`, `javax`, `org`, `com.netflix`, ``, `\#com.netflix`, `\#`
- License header is enforced by Spotless (see `licenseheader.txt`)
- Lombok 1.18.42 used throughout (`@Slf4j`, `@Data`, `@Builder`, etc.)
- No emojis in code, logs, or comments

### Pre-commit hook

```shell
ln -s ../../hooks/pre-commit .git/hooks/pre-commit
```

Runs `spotlessApply` and re-stages modified files.

## Dependency Management

### PINNED dependencies

Some deps have hard version constraints marked with `// PINNED (#964): <reason>`. **Do not bump without reading the pinned comment and #964.** Key pins:

| Dependency | Pinned at | Why |
|---|---|---|
| `protobuf-java` | 3.x | 4.x + GraalVM polyglot 25.x breaks Gradle resolution |
| `protoc` | 3.25.5 | Must match protobuf-java 3.x used by grpc-protobuf |
| All `org.graalvm.*` | same version (`revGraalVM`) | Mixing versions causes polyglot/Truffle runtime errors |
| `conductor-client` in test-harness | 5.0.1 | Fat JAR classpath conflict; resolved via a stripped JAR task |
| `awaitility` in functionalTest | 4.2.0 | e2e tests use `pollInterval(Duration)` added in 4.x (3.x in main scope) |
| `jettison` | strictly 1.5.4 | No validated higher version |

### Version floor dependencies

```groovy
// Security: <CVE> — <reason>
// Compat: <reason>
```

These can be freely bumped by Dependabot.

### Other constraints (in `build.gradle`)

- Jackson: all modules aligned to 2.17.0
- Tomcat: 10.1.54
- GraalVM all artifacts (5!) must share `revGraalVM` from `dependencies.gradle`

### Protobuf codegen

`conductor-grpc` generates Java code from `.proto` files. The `protogen` annotation processor in `conductor-annotations-processor` converts annotated POJOs to `.proto`. Build order dependency:

```
compileJava.dependsOn(':conductor-common:protogen')
```

## Java Version References

**Never link to a specific Java distribution** (Adoptium, Temurin, etc.) in docs or comments. Just say "Java 21+".

## Writing Documentation

Documentation is **derived from source**, not composed from memory. Open the source first, read what's there, then write.

- **REST endpoints**: Read `rest/src/main/java/com/netflix/conductor/rest/controllers/` — copy paths from `@PostMapping`/`@GetMapping` literally
- **CLI commands**: See `conductor-cli` (separate repo), read `cobra.Command` `Flags()`
- **SDK examples**: Read the SDK method signature or a working test
- **Expected output**: Get real output, paste verbatim
- **If you can't verify**: Add `<!-- TODO: verify against live server -->` comment

## Agent Behavior

- Prefer automation — execute requested actions without confirmation unless blocked
- Run independent operations in parallel
- Always run `spotlessApply` and test before considering work complete
- When tests or builds fail, consult CI workflows for the exact command that CI uses
