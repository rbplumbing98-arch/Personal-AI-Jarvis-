# J.A.R.V.I.S. repository guidance

## Change discipline
- Preserve existing working Android features and saved user settings.
- Keep changes small, reviewable and scoped to the requested task.
- Do not rewrite working architecture or migrate preference/memory schemas without an explicit migration plan and tests.
- Never commit API keys, tokens, keystores, signing material, personal memory exports or local machine configuration.
- Do not change release signing, package identity, versioning or publishing behavior unless specifically requested.

## Android compatibility
- The Android app must remain independent of Node.js, Ruflo and MCP tooling.
- Preserve the existing Brain/Store/Speaker behavior, Anthropic configuration, ElevenLabs voice configuration, Android TTS fallback, dashboard and CastPlay unless a task explicitly targets them.
- Do not claim a fully offline LLM or NVIDIA NIM integration is active without locating the actual inference/provider implementation and proving it with tests. Offline commands, notes/facts, taught phrases and cached answers are distinct from open-ended LLM inference.

## Ruflo and coding agents
- Use Ruflo only as an optional developer-side orchestration tool.
- Start with read-only inspection. Do not install plugins, run generated hooks, alter global settings or grant autonomous shell/write/network/release permissions without human review.
- Require a human to inspect every diff and explicitly approve commits, merges, releases and deployments.
- Keep MCP credentials and local client configuration out of Git.

## Required checks
- Run Android lint and unit tests, then assemble the debug APK for Android code changes.
- Report exact commands and outcomes. Never describe a build or device test as successful unless its result was observed.
