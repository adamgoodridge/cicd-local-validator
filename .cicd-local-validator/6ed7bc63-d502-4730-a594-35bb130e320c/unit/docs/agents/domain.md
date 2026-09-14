# Domain Docs

How the engineering skills should consume this repo's domain documentation.

## Before exploring, read these

- `CONTEXT.md` at the repository root.
- `docs/adr/` for ADRs that touch the area being explored.

If any of these files do not exist, proceed silently. The `/domain-modeling` skill creates them lazily when terms or decisions are resolved.

## Use the glossary's vocabulary

When naming a domain concept in an issue title, refactor proposal, or test name, use the terminology defined in `CONTEXT.md`. Do not drift to synonyms that the glossary explicitly avoids.

## Flag ADR conflicts

If an output contradicts an existing ADR, surface it explicitly rather than silently overriding it.
