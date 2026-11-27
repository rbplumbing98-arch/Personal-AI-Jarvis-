# Ruflo developer setup for J.A.R.V.I.S.

This is an **optional developer tool**, not an Android app dependency. Ruflo is not bundled into the APK and does not replace J.A.R.V.I.S.'s Brain, Store or Speaker.

## Pinned version

The setup example pins Ruflo **3.80.9**, the version currently published on npm when checked on 2026-11-27. Avoid changing this to `@latest` in a reproducible project setup.

Official project: https://github.com/ruvnet/ruflo  
Published package: https://www.npmjs.com/package/ruflo

## Requirements

- Node.js 20 or newer
- npm 9 or newer
- Git
- A supported local MCP coding-agent client (for example, Claude Code or Codex)

Ruflo is an orchestration harness, not a language model. Model access and any associated API usage/billing are separate from Ruflo itself. Do not add model API keys to this repository.

## Enable the MCP server (opt-in)

1. Work from a trusted local clone of this repository.
2. Copy `.mcp.json.example` to `.mcp.json` in the repository root.
3. Start or reload your supported MCP client and inspect the Ruflo server entry before approving tool access.
4. Confirm the client reports Ruflo connected. If it does not, check that Node/npm are available in that client's environment.

The example launches the pinned server with:

```bash
npx --yes ruflo@3.80.9 mcp start
```

The local `.mcp.json` is ignored by Git. Do not add credentials, personal memory exports, signing keys or tokens to it.

## First safe task

Start with a read-only review:

> Use Ruflo to inspect this J.A.R.V.I.S. repository. Do not edit files, run shell commands that change the environment, install plugins, change settings, or create commits. Report the current architecture, the exact files you inspected, risks, and a proposed small next step.

Only enable write-capable agents after reviewing their tools and permissions. Require a human to inspect diffs and approve commits, merges, releases and deployments.

## Important project boundaries

- Do not install the Ruflo CLI, Node.js runtime or MCP server in the Android APK.
- Do not alter Android provider defaults, encrypted key storage, voice IDs, memory schema, dashboard, CastPlay or signing configuration as part of developer-tool setup.
- Existing offline features include local commands, saved notes/facts, taught phrases and cached answers. The inspected `Brain.kt` sends open-ended AI questions to Anthropic when an API key is configured; do not describe this as a fully offline LLM.
- Preserve the existing ElevenLabs speech path and Android text-to-speech fallback.
- Ruflo is optional. The Android app must build and run without Node.js or Ruflo installed.

## Verification

The pull request's Android workflow is the compatibility gate: Android lint, unit tests and debug APK assembly must pass. A green CI build does not replace testing Ruflo connectivity in a supported local MCP client or testing the APK on a physical Android device.
