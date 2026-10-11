# Testing

## Layers

| Layer | What it proves | Lives in | Runs with |
|---|---|---|---|
| Unit | one unit's behavior, in isolation, fast | `src/test/kotlin/dev/starve/**/*Test.kt` | `./mvnw -B test` |
| Architecture | module dependency rule | `ArchitectureTest.kt` (ArchUnit) | `./mvnw -B test` |
| Acceptance | a `specs/features/<slug>.feature` scenario, end to end over HTTP against the real app and a temp SQLite file | `src/test/kotlin/dev/starve/acceptance/` | `./mvnw -B verify`; one scenario: `./mvnw -B verify -Dcucumber.filter.name="<scenario name>"` |

All of them run under the verify command (`./mvnw -B verify`,
`.claude/workflow.md` Project configuration), which is what CI and the Clean
Code gate run.

## Acceptance tests

Cucumber-JVM, launched by the JUnit suite `AcceptanceTest`. Every
`specs/features/*.feature` is copied onto the test classpath by the pom and
run, so each is executable. `ApplicationHarness` starts the real app
(`SpringApplicationBuilder`, random port, a fresh temp DB file per scenario)
and can restart it against the same file. Step definitions are shared test
infrastructure: after changing one, run `./mvnw -B clean verify` twice and
require identical results. A scenario filter skips the coverage gate (profile
`single-scenario` in the pom).

## Gates

- Coverage: JaCoCo, minimum 85% line (`.claude/workflow.md`, Constraints).
- Mutation testing: PIT, `./mvnw -B org.pitest:pitest-maven:mutationCoverage`
  against new/changed code (Step 6).
- Lint / static analysis: detekt (`config/detekt.yml`) and ktlint both run in
  verify; `LINT_CMD` in `.claude/tools/clean.env` runs ktlint alone.
  `./mvnw com.github.gantsign.maven:ktlint-maven-plugin:format` fixes style.

## Troubleshooting

- Tests must never use `./data/starve.db`; the harness passes a temp path.
- The restart scenario relies on fully closing the context before reopening
  the SQLite file.
- Docker is not verified locally; CI runs `docker build .`.
