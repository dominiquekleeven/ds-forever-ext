## DS Forever Ext — AYN Thor test build

Community fork of [DS Forever](https://github.com/Jreese702/DS-Forever). Not an official upstream release.

- Choose the bundled GPU driver, Android's system driver, or an imported compatible ARM64 Adrenotools driver ZIP. Turnip is not required in its name: Qualcomm Adreno and other compatible packages are accepted too.
- Existing driver imports and saved selections remain usable. Ext.8 is a same-key update; do not uninstall an earlier Ext build.
- GPU driver controls now belong in the existing launcher **Menu → GPU driver**, with the app's own theme and controller navigation. The separate top-screen button and Material driver dialog are removed.
- **WoW FPS counter (Ctrl+R)**: bottom-screen **Settings → Performance → Frame limit → Toggle**. The same action is available in the controller command chooser. It does not change existing bindings or frame limits.
- Ext.6 fixes the silent FPS action: the local button sends Ctrl+R directly without an add-on handshake. It shows an unavailable reason if local input is not ready. Keep WoW's default Ctrl+R FPS binding.
- The existing **Performance HUD** now separates **Game FPS** (WoW's `GetFramerate()`, before interpolation) from **Output FPS (presents)** (measured renderer/layer output, including generated frames when active). Enable Performance HUD in the launcher's quick menu. Missing or stale measurements say **unavailable**, never game FPS multiplied by a configured factor. Successful renderer presents are not a physical display scanout measurement.
- Use Questie instead of the Polestar add-on. Questie supplies quest markers and objectives, not Polestar's step-by-step guide.
- Questie 12.12.0 downloads from its official release during setup. Questie settings open on the game screen, not as a mirrored bottom-screen panel.
- Ext.6 waits for Questie's initial download, repairs a missing Forever TOC, checks QuestieDB contract compatibility, and enables both add-ons each startup. A verified official DB fallback replaces failed cached checks. Failures say **Questie not ready** instead of implying success. Existing replaced add-ons and AddOns.txt receive recovery backups; SavedVariables remain unchanged.
- Ext.7 adds **Shortest Path Forever 1.45.3** for a direction arrow and minimap breadcrumb route. It downloads from the author's official release during setup; TomTom is not required. Routes can use walking paths and transport rather than just pointing in a straight line. The existing shared QuestieDB stays under its one installer.
- Ext.8 enables **Route to the focused quest** once, automatically. When the game's focused quest changes, Shortest Path starts its route without a map click. It does not choose the nearest quest or a levelling order. Existing guide-owned journeys are left alone. To opt out, use `/path` → Guidance → Route to the focused quest; later choices persist. The previous setting is retained in ShortestPathForeverDB.dsForeverExtPreviousFocusedQuest.
- Manual destinations remain available: Alt-click a Questie world-map icon, or use Questie's tracker waypoint action. `/path` opens navigation settings. The trail and arrow are on WoW's game screen, not a new bottom-screen route display. Routes can be imperfect, and pathfinding adds workload.
- Separate app: **DS Forever Ext**, package `app.dsforever.ext`. Leave the original app installed.
- Fork-only signing key and update channel. No upstream report-worker endpoint.

### Test safely

1. Download the APK and check `SHA256SUMS`.
2. Install it beside DS Forever. Do not uninstall the original app.
3. Select a **copy** of your WoW game folder. A shared folder also shares AddOns and WTF settings.
4. Test the bundled driver first. Then open the launcher's **Menu → GPU driver** to import a driver ZIP, choose an imported driver, or choose System. Do not extract the ZIP yourself. Choose Bundled default if it fails.
   For Questie and navigation setup, fully close WoW and start it through Ext.8 with internet access. Wait for setup to finish. Then check `/questie`, `/path`, and WoW's add-on settings. Focus a quest to start automatic guidance. Do not clear app data or uninstall to repair it.
5. Report the Thor model, driver name, reproduction steps, and symptoms. Never attach login files or credentials.

Driver ZIPs need Adrenotools `meta.json` and an ARM64 shared library. Windows/Linux installers, arbitrary Mesa source archives and drivers for incompatible GPUs are not supported. An accepted ZIP is not proof of hardware compatibility. System selection does not replace or modify Android's driver.

Built and signature-checked by GitHub Actions. Not yet tested on a physical Thor. An imported or system GPU driver can crash the game; reset to the bundled default if needed.

The named `Polestar` and `Polestar_Nav` folders, plus recorded installer-managed Polestar folders, move into `Interface/AddOns-disabled-dsforever` without deletion. SavedVariables and other user add-ons remain. To restore Polestar, close WoW and move the reviewed folders back into `Interface/AddOns` without overwriting existing folders. Do not use both apps against the same game folder.

Upstream runtime components and credits are retained. Upstream documents unresolved component source/notice gaps in `RELEASE_NOTICES.md`; this test build does not assert those gaps are resolved. Supply your own authorised game installation and account.
