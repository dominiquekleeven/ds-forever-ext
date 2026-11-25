# Selected upstream bugfixes

| Group | Upstream commits | Scope |
| --- | --- | --- |
| Crate/supply-box selection | `a6c6d975e34fda50d8074316179bc3d03dee95eb`, Bags/test portions of `9b769805a5e1e4defb89aa68d9abbb529403a393` | Keep trade goods through the whole pick; omit the crate; no duplicate consumables; kits remain gear-only. |
| Patch-server failures | `19eef43181e32450283b93855ecf0abbb415fd55`, downloader-only correction from `f8c9d480a5b6560d8a42786235b397d90a4c2042` | Three bounded direct-install attempts; reason-specific errors; setup log and es/fr display. No authentication or TLS-policy change. |
| Java frame-generation freeze mitigation | `829fcce2cbeed0f11517f2b4dc6c02194e75fab8`, `c2114eb42007d00dceead6632b46ab1e53ecd0fc` | Separate Java lock; unchanged pushes skipped; render lists deferred during shader/on-off pushes; lifecycle retains the native handle. Ext additionally fingerprints shader content because the cache writer reuses filenames. |

These fixes ship upstream in stable v1.0.39 and remain in v1.0.41 (`afefaf66a39b756f86d8dac5cba7ca8850282876`). This is not an upstream version merge.

## Limits

- The renderer is Java-only. Native source fix `2958b10c` is not included. The existing prebuilt library still has its native lock/critical-array behavior. The Java mitigation reduces the known trigger; it does not prove every freeze is fixed.
- Ext keeps its 30 game FPS + Lossless 2× defaults, independent FPS counters, driver selection, Questie/TomTom, map overlays, signing and runtime pins.
- The scoped tests cover crate behavior, local HTTP replies/error classification, and Java lock wiring/duplicate pushes. Hardware GPU and Android UI integration still require device/CI checks.
- Release version labels and the affected addon manifest hashes are maintained separately by the primary integration task.

Protected pre-edit files and recovery: `/home/apc/infra/backups/ds-ext-upstream-bugfixes-20261125/RECOVERY.md`.

## Validation

- Local: `tools/test_kits.py`, 98 checks; `RendererFreezeLocksTest`, 5 JVM source-wiring tests; existing isolated frame-default/FPS/model harness, 42 tests. All passed.
- Executable-code token comparisons matched the selected upstream downloader, Java renderer and final Bags implementation. The helper now has an Ext-specific safe dedupe adaptation: SHA-256 of up to 64 MiB on settings pushes only. Changed bytes reload even with unchanged path, size and mtime. Missing, unreadable or oversized caches always retry; no failed read is remembered as successfully identical. Native renderer source and Ext FPS defaults/manager remain unchanged.
- Pending CI: compile the Android app and run `WowPatchAnswerTest` and `FrameGenPushesTest` with the existing Gradle dependencies. Local Android SDK/JDK and OkHttp/MockWebServer are unavailable; no dependencies were installed.
- Shader-content correction: all 9 `FrameGenPushesTest` methods compile against the actual Java source symbols; the 5 Java lock-wiring tests pass. Running those 9 behavioral tests still requires CI's Java compiler. They use real temporary caches, including same-length/same-mtime content changes, failed reads, distinct paths and `forget()`.
