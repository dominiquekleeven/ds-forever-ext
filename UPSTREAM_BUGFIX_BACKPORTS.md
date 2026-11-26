# Selected upstream bugfixes

| Group | Upstream commits | Scope |
| --- | --- | --- |
| Crate/supply-box selection | `a6c6d975e34fda50d8074316179bc3d03dee95eb`, Bags/test portions of `9b769805a5e1e4defb89aa68d9abbb529403a393` | Keep trade goods through the whole pick; omit the crate; no duplicate consumables; kits remain gear-only. |
| Patch-server failures | `19eef43181e32450283b93855ecf0abbb415fd55`, downloader-only correction from `f8c9d480a5b6560d8a42786235b397d90a4c2042` | Three bounded direct-install attempts; reason-specific errors; setup log and es/fr display. No authentication or TLS-policy change. |
| Java frame-generation freeze mitigation | `829fcce2cbeed0f11517f2b4dc6c02194e75fab8`, `c2114eb42007d00dceead6632b46ab1e53ecd0fc` | Separate Java lock; unchanged pushes skipped; render lists deferred during shader/on-off pushes; lifecycle retains the native handle. Ext additionally fingerprints shader content because the cache writer reuses filenames. |
| Weak view/host listeners | `4319540c`, host-listener portions of `3e8acf8f` | Weak owner arguments for selected static settings listeners; preserve Ext's six host registrations. No broad lifecycle rewrite. |
| False disabled-add-on warning | `23ddf008`, warning/traffic portions of `9b769805` | Diagnostic suppression around disconnect/character select; sustained world traffic rearms genuine warnings. No authentication-policy or polling changes. |
| AH Sell | `e46b123a`, AH portions of `9b769805` | Refused-item reasons, bag counts, scrollbar and controller-follow; keep Ext auction Buy Now and tooltip protections. |
| LT/RT bar correctness | `c59b0f3c`, `b1766039`, `37538de7` | Scoped bar/stance/controller/possess rewiring, combat deferral and index invalidation; preserve Ext menu ownership and neutral gates. |
| Gathering tracking | `9170d42a`, `0d05f993`, `2bebb643` | Explicit on/off from actual native tracking state; Ext permits only one global pending tracking tap. Native minimap dots only. |

These fixes ship upstream in stable v1.0.39 and remain in v1.0.41 (`afefaf66a39b756f86d8dac5cba7ca8850282876`). This is not an upstream version merge.

Ext.9 selects the additional groups from the v1.0.39–v1.0.41 review. It excludes logpolling/response-latency, indoor, driver, native-renderer and broad UI changes. Local memory/warning harness: 33 tests; AH grid/translation harness: 13; LT/RT: 35; gathering: 21; retained combat: 530; native-menu ownership: 134. Android view collection and screenshots are CI gates, not local device evidence. Protected source backups: `/home/apc/infra/backups/ds-ext-weakviews-disconnect-20261126/`, `/home/apc/infra/backups/ds-ext-ahsell-backport-20261126/`, `/home/apc/infra/backups/ds-ext-ltrt-gather-20261126/`. Integration recovery: `/home/apc/infra/backups/ds-ext-integration-20261126/RECOVERY.md`.

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
