# Quality Stabilization Report (2026-02-24)

## Baseline
- Branch: `main`
- Initial working tree: clean
- Baseline compile: `mvn -DskipTests compile` ?
- Baseline regression: `mvn -DskipITs test` ?

## Findings and Fixes

### S1 - Public H2 console while backend security is enabled
- Risk: administrative DB console was reachable without authentication (`/h2-console/**`) even when backend security was enabled.
- Fix:
  - moved `/h2-console/**` from permit-all list to authenticated list.
  - file: `src/main/java/at/htlle/freq/infrastructure/security/BackendSecurityConfig.java`
- Added regression test:
  - `BackendSecurityIntegrationTest.h2ConsoleIsProtectedWhenBackendSecurityEnabled`
  - file: `src/test/java/at/htlle/freq/web/BackendSecurityIntegrationTest.java`

### S1 - Physical delete fallback in generic delete path
- Risk: `GenericCrudController#delete` used hard delete fallback (`DELETE FROM ...`) when `ArchiveService` was unavailable.
- Fix:
  - replaced fallback with soft-delete update:
    - `SET IsArchived = TRUE, ArchivedAt = CURRENT_TIMESTAMP, ArchivedBy = :actor`
  - kept API contract (`DELETE` endpoint + `204/404`) unchanged.
  - file: `src/main/java/at/htlle/freq/web/GenericCrudController.java`
- Updated test expectation to assert soft-delete SQL:
  - file: `src/test/java/at/htlle/freq/web/GenericCrudControllerTest.java`

### S2 - Weak default credentials operational risk
- Risk: default backend credentials (`lifex/12345`) are insecure if not overridden.
- Fix:
  - added startup warning when backend security is enabled and defaults are used.
  - added fail-fast validation for blank username/password when backend security is enabled.
  - file: `src/main/java/at/htlle/freq/infrastructure/security/BackendSecurityConfig.java`

## Regression Evidence
- Targeted tests:
  - `mvn "-Dtest=GenericCrudControllerTest,BackendSecurityIntegrationTest" test` ?
- Full regression:
  - `mvn -DskipITs test` ?

## Residual Risks / Follow-ups
- No open S0/S1 issues found in reviewed security + delete-integrity paths.
- Suggested future hardening (non-blocking):
  - externalize backend credentials to secret store for production deployments.
  - evaluate whether `anyRequest().permitAll()` should be narrowed further once endpoint inventory stabilizes.
