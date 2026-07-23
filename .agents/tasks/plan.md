# Plan: Static Resources Login Protection in `@server`

## Objective

Add Spring Security form-login protection for static UI resources in the `server` module while keeping all API endpoints open for the Conductor client. The feature is gated by a configuration toggle (default: disabled) so it has zero impact when not enabled.

## Requirements Snapshot

- **R1:** Use Spring Security in-memory authentication.
- **R2:** Conductor client (REST API consumers) must connect without authentication; only static resources are protected.
- **R3:** Introduce a configuration property to enable/disable static resources protection.
- **R4:** (Implied — safety) When the feature is disabled, Spring Security on the classpath must not accidentally protect anything (provide a fallback permit-all config).
- **R5:** (Implied — UX) Login page should be a branded, minimal HTML page consistent with the Conductor UI look.

## Scope

- Add `spring-boot-starter-security` dependency to `server/build.gradle`
- Add config properties to `application.properties` (enable toggle, username, password)
- Create `StaticResourcesSecurityConfig.java` with two inner `@Configuration` classes (protected + permit-all fallback)
- Create a custom `login.html` page in `server/src/main/resources/static/`
- Create an integration test for the security config

## Assumptions and Constraints

- The project uses Spring Boot 3.3.x → Spring Security 6.x APIs (`SecurityFilterChain`, `BCryptPasswordEncoder`, etc.)
- No template engine (Thymeleaf, etc.) is in use — login page is plain HTML + inline CSS served as a static resource
- API paths: all REST controllers live under `/api/**`; health at `/health`; actuator at `/actuator/**`; OpenAPI at `/api-docs/**`, `/v3/api-docs/**`, `/swagger-ui/**`; A2A at `/a2a/**`
- The `SpaInterceptor` rewrites unrecognized GET requests to `/index.html` — this must remain functional under authentication (the interceptor runs, then the secured page is served to authenticated users)
- Static resources live at the root of `classpath:static/` and are served by Spring Boot's default resource handler under `/`

## Risks and Areas Requiring Care

1. **Permit-all fallback:** Adding `spring-boot-starter-security` to the classpath triggers Spring Boot's `SecurityAutoConfiguration`, which secures everything by default. The permit-all fallback config (active when the feature is disabled) is essential to avoid breaking existing deployments.
2. **SpaInterceptor interaction:** The `SpaInterceptor` forwards to `/index.html` for unmatched GET routes. If `/index.html` requires authentication, the forward will trigger a login redirect — this is the desired behavior for unauthenticated users. Authenticated users will get the SPA normally.
3. **CSRF:** Disabled for simplicity (the SPA does its own auth). The login form POST (`/login`) is handled by Spring Security's `UsernamePasswordAuthenticationFilter` which works regardless of CSRF.
4. **Password encoder:** Use `BCryptPasswordEncoder` (not deprecated `withDefaultPasswordEncoder()`).

## Core Concepts

### Spring Security `SecurityFilterChain`

The modern (Spring Security 5.7+) approach replaces `WebSecurityConfigurerAdapter` with a `@Bean`-defined `SecurityFilterChain`:

```java
@Bean
SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(authz -> authz
            .requestMatchers("/api/**").permitAll()
            .anyRequest().authenticated()
    ).formLogin(form -> form.loginPage("/login.html"));
    return http.build();
}
```

### `@ConditionalOnProperty` for feature gating

```java
@Configuration
@ConditionalOnProperty(name = "feature.x.enabled", havingValue = "true")
class MyConfig { ... }
```

When the property is `false` or absent, the entire `@Configuration` class is skipped — no beans are created.

### Request matcher ordering

Matchers are evaluated in the order declared. More specific paths must come before `anyRequest()`:

```java
.requestMatchers("/api/**").permitAll()     // specific — first
.requestMatchers("/login.html").permitAll() // specific — second
.anyRequest().authenticated()               // catch-all — last
```

## Sub-Tasks

### Sub-Task 1: Add Spring Security dependency

- **Status:** Pending
- **Objective:** Add `spring-boot-starter-security` to the server module's Gradle build file so Spring Security classes are available at compile and runtime.
- **Related Requirements:** R1, R4, R5
- **Dependencies and Preconditions:** None
- **In Scope for This Sub-Task:** One-line addition in `server/build.gradle`
- **Out of Scope for This Sub-Task:** Any code changes, configuration, or other dependency additions
- **Instructions:** In `server/build.gradle`, add `implementation 'org.springframework.boot:spring-boot-starter-security'` after the existing `spring-boot-starter-web` line (alphabetically adjacent).
- **Acceptance Criteria:** `./gradlew :conductor-server:dependencies` shows `spring-boot-starter-security` in the compile classpath
- **Cautionary Points (Risks & Edge Cases):** None — this is a Spring Boot managed starter, no version pinning needed
- **Implementation Suggestions:** Place it right after `spring-boot-starter-web` for readability
- **Testing Suggestions:** Run `./gradlew :conductor-server:dependencies | grep spring-boot-starter-security`
- **Done When:** The dependency line is added and Gradle resolves it successfully

---

### Sub-Task 2: Add configuration properties

- **Status:** Pending
- **Objective:** Define the three configuration properties that control the feature in `application.properties`.
- **Related Requirements:** R1, R3
- **Dependencies and Preconditions:** None
- **In Scope for This Sub-Task:** Adding the following properties to `server/src/main/resources/application.properties`:
  - `conductor.ui.security.static-resources-protection.enabled=false`
  - `conductor.ui.security.static-resources-protection.username=admin`
  - `conductor.ui.security.static-resources-protection.password=admin`
- **Out of Scope for This Sub-Task:** Any other property changes
- **Instructions:** Add the three properties as a block with a descriptive comment, placed just before the existing `# Start AI Workers` comment block.
- **Acceptance Criteria:** Properties are present in the file with default values
- **Cautionary Points (Risks & Edge Cases):** Default is `false` so existing deployments are unaffected
- **Implementation Suggestions:** Use a clear comment header explaining what the feature does
- **Testing Suggestions:** Verify the file contains the expected lines
- **Done When:** Three new properties exist in `application.properties` with `enabled=false`

---

### Sub-Task 3: Create `StaticResourcesSecurityConfig.java`

- **Status:** Pending
- **Objective:** Create the main Spring Security configuration class with two inner `@Configuration` classes: `ProtectedSecurityConfig` (form-login protecting static resources) and `PermitAllSecurityConfig` (fallback that disables all security when the feature is off).
- **Related Requirements:** R1, R2, R3, R4
- **Dependencies and Preconditions:** Sub-Task 1 (dependency) and Sub-Task 2 (properties)
- **In Scope for This Sub-Task:**
  - File location: `server/src/main/java/com/netflix/conductor/server/config/StaticResourcesSecurityConfig.java`
  - Package: `com.netflix.conductor.server.config`
  - Outer class `StaticResourcesSecurityConfig` (not annotated — just a holder)
  - Inner class `ProtectedSecurityConfig`:
    - Annotated `@Configuration`, `@EnableWebSecurity`
    - Gated by `@ConditionalOnProperty(name = "conductor.ui.security.static-resources-protection.enabled", havingValue = "true")`
    - `SecurityFilterChain` bean at `@Order(1)` with:
      - **PermitAll paths:** `/api/**`, `/health`, `/health/**`, `/actuator`, `/actuator/**`, `/api-docs`, `/api-docs/**`, `/v3/api-docs`, `/v3/api-docs/**`, `/swagger-ui`, `/swagger-ui/**`, `/a2a`, `/a2a/**`, `/error`, `/login`, `/login/**`, `/login.html`, `/favicon.ico`
      - **Authenticated:** `.anyRequest().authenticated()`
      - Form login with `loginPage("/login.html")`, `loginProcessingUrl("/login")`, `defaultSuccessUrl("/", true)`
      - Logout with `logoutSuccessUrl("/login.html?logout")`
      - CSRF disabled
    - `UserDetailsService` bean using `User.builder()` + `BCryptPasswordEncoder`
    - `PasswordEncoder` bean returning `BCryptPasswordEncoder`
    - Username/password injected via `@Value` from the config properties (defaults `admin`/`admin`)
  - Inner class `PermitAllSecurityConfig`:
    - Annotated `@Configuration`, `@EnableWebSecurity`
    - Gated by `@ConditionalOnProperty(name = "conductor.ui.security.static-resources-protection.enabled", havingValue = "false", matchIfMissing = true)`
    - `SecurityFilterChain` bean at `@Order(99)` permitting all requests, disabling form login, logout, and HTTP basic
- **Out of Scope for This Sub-Task:** Login page HTML, integration tests
- **Instructions:** Write the complete class following the design above. Use standard Java imports and the project's Apache 2.0 license header.
- **Acceptance Criteria:** The file compiles and the two inner configuration classes are correctly gated by the property
- **Cautionary Points (Risks & Edge Cases):**
  - `@EnableWebSecurity` must be on each inner `@Configuration` class, not the outer holder
  - Matcher ordering matters — permit specific paths before `.anyRequest()`
  - Do NOT use deprecated `User.withDefaultPasswordEncoder()` — use `BCryptPasswordEncoder`
- **Implementation Suggestions:** Verify the import for `@ConditionalOnProperty` is from `org.springframework.boot.autoconfigure.condition`
- **Testing Suggestions:** `./gradlew :conductor-server:compileJava` must succeed
- **Done When:** `./gradlew :conductor-server:compileJava` succeeds

---

### Sub-Task 4: Create login page HTML

- **Status:** Pending
- **Objective:** Create a branded, minimal HTML login page served as a static resource.
- **Related Requirements:** R5
- **Dependencies and Preconditions:** None
- **In Scope for This Sub-Task:**
  - File location: `server/src/main/resources/static/login.html`
  - Dark theme matching the Conductor UI style (dark background `#0f172a`/`#1e293b`, indigo accent `#6366f1`)
  - Conductor logo from `/conductorLogo.svg` (with graceful fallback if missing)
  - Username + password form fields, POST to `/login`
  - Client-side JS to show error (`?error`) and logout (`?logout`) messages from query params
  - Inline CSS only (no extra dependencies)
- **Out of Scope for This Sub-Task:** Any JavaScript framework, external CSS, or template engine
- **Instructions:** Write a complete HTML5 document with inline `<style>` and `<script>`. Use the existing `conductorLogo.svg` from the same static directory. Form action is `/login`, method is `post`. Input `name` attributes must be `username` and `password` (Spring Security defaults).
- **Acceptance Criteria:** The login page is served at `/login.html` and renders correctly
- **Cautionary Points (Risks & Edge Cases):**
  - The logo SVG may not exist in all builds — use `onerror="this.style.display='none'"` to hide gracefully
  - No external resources (fonts, images) — everything must be self-contained or reference existing static assets
  - The page must work without JavaScript (JS is only for error/success messages — the form works without it)
- **Implementation Suggestions:** Use the same color palette observed in `index.html` and the Conductor logo for visual consistency
- **Testing Suggestions:** Open the file directly in a browser to verify rendering
- **Done When:** `login.html` exists in `static/` and is a valid HTML5 document

---

### Sub-Task 5: Create integration test

- **Status:** Pending
- **Objective:** Write a Spring Boot integration test that verifies the security behavior with the feature both enabled and disabled.
- **Related Requirements:** R1, R2, R3
- **Dependencies and Preconditions:** Sub-Tasks 1, 2, 3, 4
- **In Scope for This Sub-Task:**
  - File location: `server/src/test/java/com/netflix/conductor/server/config/StaticResourcesSecurityConfigTest.java`
  - Package: `com.netflix.conductor.server.config`
  - Test class with two test profiles or property overrides:
    1. **Feature disabled (default):** Verify that `/api/` returns 200, `/` returns 200 (permit-all fallback)
    2. **Feature enabled:** Verify that `/api/` returns 200 (always open), `/` returns 302 (login redirect), and `/login.html` returns 200 (login page accessible)
  - Use `@SpringBootTest` with `webEnvironment = RANDOM_PORT` and `TestRestTemplate` or `MockMvc`
  - Use `@TestPropertySource` or nested `@TestConfiguration` classes to toggle the feature
  - Optionally test that authenticating then accessing `/` returns 200
- **Out of Scope for This Sub-Task:** Testing every static path — one representative path (`/`) is sufficient
- **Instructions:**
  - Create a base test class with shared setup
  - For the "enabled" profile, set `conductor.ui.security.static-resources-protection.enabled=true` and provide credentials
  - Use `TestRestTemplate` with `HttpURLConnection` following redirects disabled to check for 302
- **Acceptance Criteria:** Both test cases pass: feature disabled = all paths open; feature enabled = API open, static protected
- **Cautionary Points (Risks & Edge Cases):**
  - The test must not require a database or other infrastructure (use `@SpringBootTest` with minimal context)
  - If `MockMvc` is preferred over `TestRestTemplate`, ensure the security filter chain is registered
- **Implementation Suggestions:** Use `@Nested` inner classes for "enabled" and "disabled" scenarios with different property sets
- **Testing Suggestions:** `./gradlew :conductor-server:test --tests "*StaticResourcesSecurityConfigTest*"`
- **Done When:** The test compiles and both scenarios pass

---

## Final Integration & Verification

- **System-Wide Test:** Run `./gradlew :conductor-server:test` — all server module tests pass
- **Completion Checklist:**
  - [ ] `server/build.gradle` contains `spring-boot-starter-security`
  - [ ] `application.properties` contains the three feature properties (default: disabled)
  - [ ] `StaticResourcesSecurityConfig.java` compiles with both inner config classes
  - [ ] `login.html` exists and renders a branded login form
  - [ ] Integration test passes for both enabled and disabled scenarios
  - [ ] `./gradlew spotlessApply` passes (code formatting)

## Open Questions

None.
