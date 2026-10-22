# トリマ新チャットの開発ブランチ起点

- branch: `dev/torima`
- based on stable Torima v3.14 plus the 2026-10-22 multi-app handoff foundation.
- This branch exists solely for Torima-specific code and on-device diagnostics. `main` is **not** a development branch.
- Required reading: [HANDOFF_TORIMA.md](HANDOFF_TORIMA.md), [AGENTS.md](../../AGENTS.md), [BRANCH_AND_RELEASE_RULES.md](BRANCH_AND_RELEASE_RULES.md).
- The first build on this branch checks the new workflow and two Torima install variants:
  - `app-debug.apk` -> `com.neru.powlautotest.dev`, app name Torima Auto DEV, may have ephemeral debug signing.
  - `TorimaAuto-QA.apk` -> `com.neru.powlautotest.qa`, app name Torima Auto QA, **signed with the same secured release keystore** when the configured secrets are available. It does NOT replace the stable production Torima.
- The release variant remains `com.neru.powlautotest` (current production v3.14). Installed QA has an independent Android app data directory. Learning examples DO NOT transfer automatically.
- Use the QA APK for repeated field learning tests, and manually enable only the QA AccessibilityService before starting it.
- A green GitHub build verifies compilation and unit tests, not live Torima navigation. Record field-tested results in the handoff.
