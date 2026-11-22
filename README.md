# J.A.R.V.I.S. Android — Quantum Core

Android voice assistant with a Quantum Core holographic hub, local notes and learned facts, voice recognition, notification reading, media controls, navigation/dialler shortcuts, optional Anthropic answers, and optional ElevenLabs speech.

## Build
Open in Android Studio with JDK 17 and Android SDK 35, or run the GitHub Actions workflow named Build JARVIS Android APK. A successful run publishes an installable debug APK artifact named jarvis-quantum-core-debug-apk.

## Voice setup
Open J.A.R.V.I.S. Settings, enter your ElevenLabs API key and voice ID, then use the voice test control when available in the app. Keep the API key private and never commit it to GitHub. ElevenLabs usage consumes account quota. Voice quality depends on the selected voice. The app currently uses the ElevenLabs multilingual v2 model with natural-delivery settings. When ElevenLabs is unavailable, Android text-to-speech is used as a fallback.

## Capabilities and limits
Local commands, notes, saved facts and taught phrases work without an AI API key. Open-ended answers require an optional Anthropic API key and internet access; this is not a local large language model. Speech recognition uses the Android speech provider with en-GB locale. Offline recognition depends on installed language packs. Notification reading requires Notification Access. Media controls depend on Android media sessions. An always-on Hey J.A.R.V.I.S. wake-word engine is not yet implemented.
