## DS Forever Ext — AYN Thor test build

Community fork of [DS Forever](https://github.com/Jreese702/DS-Forever). Not an official upstream release.

- Updated to upstream **1.0.38** (`7552e1f3`), with its Home editor, controller situations, sticky character settings, loading/input fixes, HTTPS downloads, and memory improvements. Fork-specific integration notes: [UPSTREAM_1_0_38_EXT.md](UPSTREAM_1_0_38_EXT.md).
- This is **1.0.38-ext.4**, version code **1524**, signed with the existing Ext key. Install over an earlier Ext build; do not uninstall or clear data. Questie, navigation, bottom-screen breadcrumbs, GPU driver selections, and independent FPS measurements remain part of this fork. Polestar is not restored.
- On the first start after this update, the default changes to a **30 FPS game cap with Lossless frame generation enabled at 2×**, targeting 60 output FPS. It applies to existing Ext installs as well as new installs. The prior settings are retained privately for recovery. It applies to the next game launch, not by forcibly restarting a running game. Later manual changes persist across launches and future updates.
- Supply your own licensed **Lossless.dll** if it is not already imported. The DLL is never bundled or downloaded. Without it, frame generation stays inactive and the game keeps the 30 FPS cap. A 60 FPS target is not a promise of measured output; the HUD continues to report actual measurements.
- Upstream now applies extra low-memory graphics reductions on 12 GB models as well as 8 GB models, and Light 8 automatically on 8 GB devices. This can reduce spell/particle detail. It also enables a video-memory cap and native BC textures on detected Adreno devices. Physical-device results remain unverified for this merged release.
- Choose the bundled GPU driver, Android's system driver, or an imported compatible ARM64 Adrenotools driver ZIP. Turnip is not required in its name: Qualcomm Adreno and other compatible packages are accepted too.
- Existing driver imports and saved selections remain usable across this same-key update.
- GPU driver controls now belong in the existing launcher **Menu → GPU driver**, with the app's own theme and controller navigation. The separate top-screen button and Material driver dialog are removed.
- **WoW FPS counter (Ctrl+R)**: bottom-screen **Settings → Performance → Frame limit → Toggle**. The same action is available in the controller command chooser. It does not change existing bindings or frame limits.
- Ext.6 fixes the silent FPS action: the local button sends Ctrl+R directly without an add-on handshake. It shows an unavailable reason if local input is not ready. Keep WoW's default Ctrl+R FPS binding.
- The existing **Performance HUD** now separates **Game FPS** (WoW's `GetFramerate()`, before interpolation) from **Output FPS (presents)** (measured renderer/layer output, including generated frames when active). Enable Performance HUD in the launcher's quick menu. Missing or stale measurements say **unavailable**, never game FPS multiplied by a configured factor. Successful renderer presents are not a physical display scanout measurement.
- Use Questie instead of the Polestar add-on. Questie supplies quest markers and objectives, not Polestar's step-by-step guide.
- Questie 12.12.0 downloads from its official release during setup. Questie settings open on the game screen, not as a mirrored bottom-screen panel.
- Ext.6 waits for Questie's initial download, repairs a missing Forever TOC, checks QuestieDB contract compatibility, and enables both add-ons each startup. A verified official DB fallback replaces failed cached checks. Failures say **Questie not ready** instead of implying success. Existing replaced add-ons and AddOns.txt receive recovery backups; SavedVariables remain unchanged.
- Ext.7 adds **Shortest Path Forever 1.45.3** for a direction arrow and minimap breadcrumb route. It downloads from the author's official release during setup; TomTom is not required. Routes can use walking paths and transport rather than just pointing in a straight line. The existing shared QuestieDB stays under its one installer.
- Ext.8 enables **Route to the focused quest** once, automatically. When the game's focused quest changes, Shortest Path starts its route without a map click. It does not choose the nearest quest or a levelling order. Existing guide-owned journeys are left alone. To opt out, use `/path` → Guidance → Route to the focused quest; later choices persist. The previous setting is retained in ShortestPathForeverDB.dsForeverExtPreviousFocusedQuest.
- Manual destinations remain available: Alt-click a Questie world-map icon, or use Questie's tracker waypoint action. `/path` opens navigation settings. Routes can be imperfect, and pathfinding adds workload.
- Ext.9 mirrors Shortest Path's active walking route onto DS Forever's bottom-screen Home minimap and Map room. It reads the existing journey rather than running another planner. Missing, cancelled, paused or stale guidance must not leave an old trail on the map. Cross-map and transport gaps are not drawn as walking paths.

### Ext.4 add-on efficiency fixes

- The 30 Hz trail buffer uses circular indexing instead of shifting up to 89 entries per sample when full. It keeps the same newest 90 samples, order, timing, and stop behavior.
- Aggro tint cleans inactive frames once instead of repeating cleanup every frame. Re-enabling tint and reusing nameplate slots restores normal updates. No visible update rate is reduced. Device FPS impact remains unmeasured.
- Installer set 426 updates the bundled add-on files on existing installs. The separate HUD collection optimization is not included.

### Retained Ext.3 fixes

- Companion quest focus uses Questie's nearest eligible destination through Shortest Path's existing TomTom bridge. A legacy companion pin no longer hides focus changes. Changing focus replaces the companion's prior destination, or clears it when no replacement is available. Later navigation opt-outs and other guide ownership remain respected. The dotted walking trail and direction/destination symbol are unchanged.
- The native WoW Game Menu yields the bottom screen's shared controller capture, direct input, sticks, and secure router bindings, in and out of combat. Closing it restores the existing room and modal stack. Held inputs must return to rest before bottom-screen input resumes. Physical-device verification is still required.
- Lua settings recovery copies are prepared and flushed only on clean exit, logout, or `/reload`, before the logout notice. There is no periodic, login, or settings-change serialization. Reset uses the following normal reload save. Native SavedVariables remain in use; a force-kill or crash can lose unsaved changes. Android preference saves are unchanged.
- CI runs the test suite and signed-candidate build in parallel. Publication requires both to pass; signing and release verification remain unchanged.

- Separate app: **DS Forever Ext**, package `app.dsforever.ext`. Leave the original app installed.
- Fork-only signing key and update channel. No upstream report-worker endpoint.

### Test safely

1. Download the APK and check `SHA256SUMS`.
2. Install it beside DS Forever. Do not uninstall the original app.
3. Select a **copy** of your WoW game folder. A shared folder also shares AddOns and WTF settings.
4. Test the bundled driver first. Then open the launcher's **Menu → GPU driver** to import a driver ZIP, choose an imported driver, or choose System. Do not extract the ZIP yourself. Choose Bundled default if it fails.
   For Questie and navigation setup, fully close WoW and start it through this Ext build with internet access. Wait for setup to finish. Then check `/questie`, `/path`, and WoW's add-on settings. Focus a quest to start automatic guidance. Do not clear app data or uninstall to repair it.
5. Report the Thor model, driver name, reproduction steps, and symptoms. Never attach login files or credentials.

Driver ZIPs need Adrenotools `meta.json` and an ARM64 shared library. Windows/Linux installers, arbitrary Mesa source archives and drivers for incompatible GPUs are not supported. An accepted ZIP is not proof of hardware compatibility. System selection does not replace or modify Android's driver.

Built and signature-checked by GitHub Actions. Not yet tested on a physical Thor. An imported or system GPU driver can crash the game; reset to the bundled default if needed.

The named `Polestar` and `Polestar_Nav` folders, plus recorded installer-managed Polestar folders, move into `Interface/AddOns-disabled-dsforever` without deletion. SavedVariables and other user add-ons remain. To restore Polestar, close WoW and move the reviewed folders back into `Interface/AddOns` without overwriting existing folders. Do not use both apps against the same game folder.

Upstream runtime components and credits are retained. Upstream documents unresolved component source/notice gaps in `RELEASE_NOTICES.md`; this test build does not assert those gaps are resolved. Supply your own authorised game installation and account.
