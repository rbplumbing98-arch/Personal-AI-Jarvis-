# Ruflo integration plan for J.A.R.V.I.S.

Status: preparation only. This branch is isolated from `main`; no Android runtime code or existing settings have been changed.

## Repository baseline inspected

- Android application written in Kotlin; Gradle/Android build is the source of the APK.
- Existing assistant pipeline: `MainActivity` -> `Brain` -> local/offline model and optional NVIDIA NIM / Anthropic providers.
- Existing memory and preferences: `Store` (including encrypted API-key storage and saved memories).
- Existing speech: `Speaker` with ElevenLabs online speech and Android on-device TTS fallback.
- Existing visual interface: `AureliaView` and the dashboard in `MainActivity`.
- Existing CI: `.github/workflows/build.yml` runs Android lint, unit tests and APK assembly.
- Latest observed main commit: `1354b967507cd2dfaf98adab9fdce3e6f5c437e1` (Friday online/offline voice system).

## What Ruflo is — and is not

Ruflo is a developer-side agent orchestration harness for Claude Code, Codex and MCP-compatible clients. It is not an Android SDK, a Kotlin library, or a replacement for the app's current `Brain`, `LocalAi`, `Store`, or `Speaker` classes.

Official project: https://github.com/ruvnet/ruflo

The official repository's latest release observed during preparation is `v3.80.10`. Do not use a floating `@latest` dependency in a reproducible production setup; pin and verify the selected release before installation.

## Proposed two-layer integration

### Layer 1 — Ruflo for J.A.R.V.I.S. development (first)

Use Ruflo on the development workstation / coding-agent environment to coordinate specialized work:

- Architect: inspect the Android architecture and propose minimal changes.
- Android/Kotlin developer: implement approved app changes.
- QA/test agent: add or run tests, lint and build checks.
- Security reviewer: inspect permissions, API-key handling, dependencies and generated changes.
- Documentation agent: maintain setup and release notes.

Ruflo configuration and agent instructions must remain separate from the Android APK runtime. Add a dedicated CI validation job only after the pinned Ruflo setup is verified and the exact generated files/commands are reviewed. The existing Android build job remains authoritative.

### Layer 2 — multi-agent capabilities in the Android assistant (evaluation first)

Do not embed the Ruflo CLI, Node.js runtime, MCP server, or developer credentials in the APK.

Evaluate an app-facing orchestration interface behind a separate, opt-in capability:
- Keep current local-first command handling and local model as the default.
- Keep NVIDIA / Anthropic provider selection and the existing ElevenLabs / offline voice paths intact.
- Add a typed task interface only if a genuine app use case is demonstrated (for example, research + summarize + verify).
- Any remote orchestration service must be separately hosted, authenticated, rate-limited, and explicitly enabled by the user. Never expose provider keys or Ruflo/MCP credentials in the Android client.
- When offline or the service is unavailable, J.A.R.V.I.S. must continue using its existing local brain and commands.

## Safety and compatibility gates

1. Work only on `ruflo-integration-prep` until reviewed.
2. Pin Ruflo to an inspected release; review package scripts, plugins, MCP server declarations and permissions before executing.
3. No secrets, API keys, keystores, tokens, personal memory exports, or signing material in Git.
4. Do not grant autonomous write, shell, network, release, or deployment permissions by default. Require review before edits and explicit approval before merges or releases.
5. Do not alter the existing app provider defaults, saved preferences, voice IDs, memory schema, dashboard, CastPlay, or signing workflow as part of the Ruflo setup.
6. Verify Android lint, unit tests and debug APK build after any Android code change.
7. Test existing offline brain, online provider fallback, ElevenLabs voice, offline voice, memory persistence and CastPlay before merging.
8. Do not claim an APK or app integration is complete until CI and device tests pass.

## Acceptance criteria

- Ruflo developer workflow starts successfully using the pinned version and a supported coding-agent client.
- Agent tasks are limited to this repository and produce inspectable diffs.
- Android build/test workflow remains green.
- Existing app functionality and user settings remain unchanged.
- Any app-facing agent capability is optional, secure, and demonstrably useful; otherwise keep Ruflo developer-only.

## Current implementation state

This document is a safe implementation plan, not a claim that Ruflo has been installed or that multi-agent runtime functionality has been added to the APK.
