# Architecture

<!-- TEMPLATE: replace each section. Keep this an overview that links to
area docs (docs/architecture-<area>.md) once it outgrows one page. The
devflow coder agent reads this file on every run, so keep it short. -->

## Overview

`<What the system is, its entry point(s), and the main runtime flow in a few
sentences.>`

## Modules

| Module / package | Responsibility | May depend on |
|---|---|---|
| `<domain>` | `<business rules, no framework code>` | nothing |
| `<application>` | `<use cases, orchestration>` | `<domain>` |
| `<ui / api / infra>` | `<delivery and adapters>` | `<application>`, `<domain>` |

## Dependency rule

`<Which direction dependencies may point, and the test or lint rule that
enforces it (e.g. an ArchUnit test, eslint-plugin-boundaries,
dependency-cruiser).>` A violation is fixed by inverting the dependency or
extracting an interface, never by weakening the rule (`.claude/workflow.md`).

## Key decisions

`<Non-obvious design decisions and why they were made. One bullet each, with
the date or PR.>`
