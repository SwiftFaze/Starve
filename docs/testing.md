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
infrastructure. Every page needs a signed-in user, so "Given the application
is running" completes first-run setup with the logged code and leaves the
default browser signed in (and signs it back in after a restart); use "Given the
application is running with no users yet" or "I am not signed in" to start
signed out. The harness client is a `Browser` with its own cookie jar that sends
the CSRF token on form posts, so scenarios exercise the real login and CSRF
flow; `browserAt(address)` gives another client with its own forwarded address.
A second user is created with "Given the user {string} exists" and the default
browser switches to them with "When I sign in as {string}"; steps that name an
item, source or group pick the signed-in user's own row first, so two users can hold
same-named rows. Scenarios that build an older database with `LegacyDatabase` start from
a pre-accounts database file: the harness migrates it to V13, seeds the `owner`
account, and only then starts the app, whose own startup applies V14 and assigns the
old rows to that owner.
After changing a step, run `./mvnw -B clean verify` twice and
require identical results. A scenario filter skips the coverage gate (profile
`single-scenario` in the pom).

## Security chain tests

Acceptance scenarios cover the sign-in flow end to end but are excluded from
PIT, so the security wiring (`SecurityConfig`, `AccountSecurity`,
`AccountConfig`, the login and setup controllers) has its own fast layer in
`src/test/kotlin/dev/starve/infra/` (`Security*Test`, `SetupGateTest`,
`LoginFlowTest`, `SetupFlowTest`). They run the real filter chain behind
MockMvc (`@WebMvcTest` slice, in-memory accounts, base class
`SecurityTestBase`). Each test dirties its context on purpose: PIT would
otherwise reuse a context built before the mutant was applied, and
mutants in the chain's configuration would survive. Put new sign-in, CSRF,
throttle or gate behaviour here as well as in a scenario.

Scenarios that need a second client (a deleted user's remember-me cookie, say) use `ApplicationHarness.rememberedBrowser(username)`, which logs in ticking Remember me and then drops the session; the result is kept in `World.secondBrowser`.

## Gates

- Coverage: JaCoCo, minimum 85% line (`.claude/workflow.md`, Constraints).
- Mutation testing: PIT via `bash .claude/tools/pit-changed.sh` (Step 6). It
  derives `targetClasses` from the Kotlin files changed since `origin/develop`
  (`PIT_BASE_REF` overrides) and excludes the Cucumber suite. Per class, not
  per line: only survivors on lines the change added need action. `--print`
  shows the classes without running. (`withHistory` needs the commercial
  Arcmutate plugin in PIT 1.30, so every run is a full one.)
  Why: whole-project runs that re-booted Spring per scenario for every mutant
  took ~45 min. With parallel worktrees, wrap it in devflow's `mutation-lock.sh`.
  A full run is still `./mvnw -B org.pitest:pitest-maven:mutationCoverage`.
- Lint / static analysis: detekt (`config/detekt.yml`) and ktlint both run in
  verify; `LINT_CMD` in `.claude/tools/clean.env` runs ktlint alone.
  `./mvnw com.github.gantsign.maven:ktlint-maven-plugin:format` fixes style.

## Troubleshooting

- Tests must never use `./data/starve.db`; the harness passes a temp path.
- The restart scenario relies on fully closing the context before reopening
  the SQLite file.
- Docker is not verified locally; CI runs `docker build .`.
