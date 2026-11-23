# Upstream 1.0.38 integration

| Item | Value |
| --- | --- |
| Upstream target | `v1.0.38`, `7552e1f31086ad64c59f29434a11ec6638176534` |
| Fork baseline | Ext.9, `30121526a57f872da091265cfedb6ad2214d9607` |
| New version | `1.0.38-ext.1`, code `1521` |
| Package | `app.dsforever.ext` |
| Bundled companion set | `424` |

## Preserved fork behavior

- Questie and Shortest Path Forever replace Polestar installation and automation.
- QuestieDB has one installer, with Forever contract checks and a verified repair fallback.
- Navigation follows the focused quest automatically once enabled. Later opt-outs persist.
- The bottom Home map and Map room show the active walking route. Stale routes and transport gaps are not drawn as walking paths.
- Menu → GPU driver retains Bundled, System, and compatible imported Adrenotools ZIP choices.
- The FPS action sends a safe local Ctrl+R chord. It now reads upstream's queued logical key state, including held modifiers.
- Game FPS and measured output FPS remain separate. HUD sampling stays off the UI thread.
- Ext keeps its package, signing identity, update channel, and disabled upstream report-upload endpoint.

## Upstream changes retained

Home editing, controller situations and chooser grids, sticky realm/character keys, loading gates, queued X input, foreground game service, HTTPS CDN downloads, resumable installs, repair tooling, and memory/cache improvements are included.

Upstream's video-memory cap and native BC texture mode on detected Adreno devices are retained. Light 8 applies automatically on 8 GB models, including pipeline lifetime tracking and four compiler threads. Low-memory graphics cuts now include 12 GB models and can reduce particle and spell density. This release does not claim to fix the separately reported missing Mind Blast effect.

## Build and verification

CI checks the fork's driver, FPS, Questie, navigation, route-model, and native map-rendering tests. It also checks selected upstream input, service, launch, Home layout, character-key, loading, and memory tests.

The DNS shim is rebuilt from merged source with the pinned Android NDK. Packaging checks that the APK contains that exact CI-built binary. The runtime trim uses Python 3.13.3 and zstandard 0.25.0, matching upstream's recipe. Upstream's pinned trimmed-runtime hash remains enforced. Source provenance and license notices remain in the repository.

Install over Ext.9 without clearing app data. Do not run original DS Forever and Ext against the same game folder at the same time. Validate gameplay, controller input, driver selection, `/questie`, automatic navigation, and both bottom-screen maps on the Thor before treating this merge as device-validated.
