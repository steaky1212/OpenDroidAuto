# OpenDroidAuto

Android Auto head-unit app for an Android 4 (API 15) Honda Jazz infotainment system.

## Constraints

- Must keep `minSdk`/`targetSdk` at 15 and ABI `armeabi-v7a` (see `build.gradle` `ext`). Check the merged manifest if AGP or androidx versions change.
- Build: `./gradlew assembleDebug` -> `app/build/outputs/apk/debug/HondaAppCenter_A1.apk`.
- If native linking fails with `undefined reference to JNIBase::...`, delete `aasdk/build/.cxx`, `omxvideocodec/build/.cxx` and their `build/intermediates/cxx` (stale prefab config for `common`).

## Agent skills

### Issue tracker

Issues are tracked in GitHub Issues (steaky1212/OpenDroidAuto, via `gh`). See `docs/agents/issue-tracker.md`.

### Triage labels

Default five-label vocabulary (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `CONTEXT.md` and `docs/adr/` at the repo root. See `docs/agents/domain.md`.
