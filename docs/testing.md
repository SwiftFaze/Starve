# Testing

<!-- TEMPLATE: replace the placeholders with the project's real layout and
commands. This is the canonical home for test mechanics; .claude/workflow.md
links here rather than restating it. -->

## Layers

| Layer | What it proves | Lives in | Runs with |
|---|---|---|---|
| Unit | one unit's behavior, in isolation, fast | `<path>` | `<command>` |
| Acceptance | a `specs/features/<slug>.feature` scenario, end to end through the public API/UI | `<step definitions path>` | `<command>`; one scenario: the scenario command |
| Integration | real I/O (files, DB, network) | `<path>` | `<command>` |

All of them run under the verify command (`.claude/workflow.md`, Project
configuration), which is what CI and the Clean Code gate run.

## Acceptance tests

Every `.feature` file under `specs/features/` must be wired to the runner
(`<Cucumber / Playwright-BDD / pytest-bdd / ...>`), so it is executable, not
documentation. Step definitions shared by several features are shared test
infrastructure: after changing one, run the verify command twice from clean
and require identical results.

## Gates

- Coverage: `<tool>`, minimum `<n>%` (value recorded in `.claude/workflow.md`,
  Constraints).
- Mutation testing: `<tool>`, run by the mutation command against new/changed
  code (Step 6).
- Lint / static analysis: `<tool>`, run by `LINT_CMD` in
  `.claude/tools/clean.env`.

## Troubleshooting

`<Known flaky areas, environment setup, how to debug a failing layer.>`
