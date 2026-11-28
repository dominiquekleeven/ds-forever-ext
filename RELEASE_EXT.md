## DS Forever Ext — AYN Thor test build

Community fork of [DS Forever](https://github.com/Jreese702/DS-Forever). Not an official upstream release.

- Updated to upstream **1.0.38** (`7552e1f3`), with its Home editor, controller situations, sticky character settings, loading/input fixes, HTTPS downloads, and memory improvements. Fork-specific integration notes: [UPSTREAM_1_0_38_EXT.md](UPSTREAM_1_0_38_EXT.md).
- This is **1.0.38-ext.11**, version code **1531**, signed with the existing Ext key. Install over an earlier Ext build; do not uninstall or clear data. Questie, simple waypoint navigation, GPU driver selections, and independent FPS measurements remain part of this fork. Polestar is not restored.
- The existing one-time Ext migration uses a **30 FPS game cap with Lossless frame generation enabled at 2×**, targeting 60 output FPS. Ext.10 does not repeat that migration or reset later manual choices. The prior settings are retained privately for recovery. Settings apply to the next game launch, not by forcibly restarting a running game.
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
### Ext.11 Blizzard native Auction House

- The Auction House now stays in Blizzard's native UI on the game screen. The bottom-screen AH mirror is disabled. Other mirrored windows are unchanged.
- Outside combat, the native AH suspends bottom-screen gamepad capture; its confirmation dialogs retain gamepad ownership. Bottom-screen touch stays usable. Settings/AddOns/Help/Game Menu retain their existing touch blocker when open. The previous bottom room resumes gamepad control after the native windows close. Combat keeps the existing GameMenuFrame-only isolation policy; no new combat binding hooks are added.
- Native AH item/comparison tooltips remain visible while the AH is open, even with Hide game tooltips enabled. The saved setting is unchanged; hidden-buff protection stays active and ordinary tooltip hiding resumes when the AH closes.
- The companion no longer opens Favorites searches or publishes AH controls on this route. No real auction transaction was used for testing. Installer set 432 updates the add-ons: close WoW completely and relaunch through Ext. Device acceptance is still needed.

### Ext.11 large-map quest selection

- The large bottom map's quest list now locates quests through the same native-first/Questie-fallback markers that it draws. Selecting a known marker centers the camera on that point, including another quest in the same zone, instead of the zone midpoint. Unknown locations retain the existing zone-name fallback. Questie/TomTom data and the Home minimap are unchanged; device acceptance is still needed.

### Retained Ext.10 Play startup path

- When the current game build's required art is already valid, Play reuses that result instead of starting an optional-only harvest and waiting for its game-archive open. Ordinary art/icon initialization still runs. Missing or stale required art, required failures, update stops and pending rebuilds retain the normal wait path.
- An already-running art worker is not forgotten or forcibly interrupted. The existing Play hold, native-file release check and 20-second maximum wait remain unchanged. Optional background work remains available through its existing scheduling rules.
- Removes one unused installed-component scan during bundled-component setup. Both Wine-stage scans remain, so intervening installs and driver/profile changes are still seen. No drivers, native binaries, login policy, FPS choices, logpolling or response-time changes.
- This targets Play-to-WoW startup, not character-to-world loading. No seconds-saved claim has been measured on a Thor. Installer set 431 is unchanged; all Ext.9 features, saved choices and logout-only settings serialization remain intact.

### Retained Ext.9 selected upstream fixes and gathering

- Home/map/cast/chat and host settings listeners hold their owners weakly. This addresses the selected view-retention paths, not every possible leak.
- Disconnect/character-select traffic no longer falsely reports disabled add-ons. Sustained world traffic rearms the warning when add-ons really stop responding. Authentication policy is unchanged.
- AH Sell shows refused items with reasons and bag counts. Scrollbars and controller-follow behavior improve navigation. Refused items cannot post; existing Buy Now and tooltip safeguards remain.
- LT/RT wiring follows stance, action-bar, controller and possess changes, with safe combat deferral and cached-index invalidation.
- Professions offers explicit gathering tracking on/off controls using actual game state. Dots appear on the native top-screen minimap, not our bottom map. Only one tracking tap can be pending.
- Installer set 431 updates the bundled add-ons. Fully close WoW and relaunch through Ext. No logpolling, response-time, indoor, driver or native-renderer changes are included. Questie/TomTom, horizontal HUD, logout-only saves and user settings remain intact. No FPS/battery gain is claimed; Thor validation is still required.

### Retained Ext.8 native submenu input outside combat

- Outside combat, the bottom screen yields gamepad input while native Settings, AddOns, Help, or the Game Menu is open. Main-menu/submenu transitions and associated native confirmation popups retain ownership until all relevant windows close. Held buttons and sticks must return to rest before bottom-screen control resumes.
- Existing bottom rooms, modal state, and queued guests stay intact. Ordinary mirrored map, character, chat, and NPC windows are not added to the native-menu block list.
- This extension is explicitly out-of-combat only. Combat keeps the existing GameMenuFrame-only policy; arbitrary native submenu ownership during combat is not guaranteed. No insecure combat binding changes or game-protection bypasses are added.
- Physical-device behavior still needs verification. The Ext.8 submenu policy remains unchanged.

### Retained Ext.7 auction Buy Now and tooltip fixes

- Fixes the bottom-screen item-listing Buy button reporting "The game gave this no tap cell" after Browse filled the shared action row. Listing selection uses the existing quantity row to arm only; a separate reserved action confirms the purchase.
- Confirmation rechecks the auction ID, item key, quantity, buyout price, funds, expiry, combat, and window ownership. A moved or changed listing is refused. Your own auctions, listings without a buyout, and insufficient funds remain blocked. No automatic purchase or live transaction was used for testing.
- Touching or selecting an item listing now requests its rich tooltip, preserving the actual item link, suffix, and level context. Loading data retries are bounded and use current-item completion events. A different auction reusing a row cannot display stale tooltip data. Item stats come from the game, not guessed database values.
- Fully close WoW and relaunch through Ext to update the bundled auction bridge. Device acceptance of the physical confirmation still needs testing.

### Retained Ext.6 quest markers and selected upstream fixes

- Both bottom map callers now receive Questie available-giver and objective-area exports. The full Map screen draws available givers and uses Questie objective/finisher spots when native points are missing. Saved filters and the Home minimap's proximity rules remain unchanged.
- A transient early QuestieDB-readiness failure no longer disables exports for the whole session. Recovery uses the existing 15-second heartbeat; invalid contracts remain rejected. Marker changes and map transitions have regression coverage. These defects are verified in tests, but the complete reported Thor symptom still needs an on-device check.
- Selected bugfixes from stable upstream releases through 1.0.41: crate/supply-box item selection, bounded transient patch-server retries with specific errors, and Java-side frame-generation freeze mitigation. This is not a full 1.0.41 merge. See [backport provenance and limits](UPSTREAM_BUGFIX_BACKPORTS.md).
- Unchanged frame-generation pushes are skipped. Shader identity checks content as well as its path, so a replaced DLL/cache still reloads. The native prebuilt lock issue is not repaired by this Java mitigation. Your 30 FPS + 2× defaults, independent FPS measurements, Questie/TomTom, horizontal HUD, and driver choices remain unchanged.
- Fully close WoW and relaunch through Ext to load the bundled fixes; do not uninstall or clear data.

### Retained Ext.5 horizontal HUD and waypoint navigation

- Performance HUD is now a shallow horizontal strip. It retains the existing metrics, graphs, size, colors, and draggable position. It wraps when the available width is insufficient; tapping no longer switches back to vertical.
- **TomTom v4.4.1-release replaces Shortest Path Forever.** It downloads unmodified from the official WoWInterface source during setup. Questie and its shared QuestieDB remain unchanged. See [navigation details and pinned source](NAVIGATION_EXT.md).
- Focusing a quest selects its nearest eligible Questie objective. Quest updates refresh the destination; completion points at the turn-in. The same goal feeds TomTom's arrow, the game's floating waypoint where supported, and a diamond on both bottom maps. No walking-path generation or dotted walking route remains.
- Missing destinations, loading, and logout clear owned markers. Repeated identical destinations do not requeue. A manual destination pauses following until another explicit focus choice. Use `/wcnav off` or `/wcnav on` to change following; `/tomtom` opens arrow settings. The native floating marker needs a physical-device check.
- With WoW closed, installer-recorded Shortest Path moves to unique disabled storage without pruning. Its SavedVariables stay intact. An unrecorded manual install is left alone, and the new bridge stands down while it is loaded. Old SPF opt-outs remain on disk but cannot be imported when that retired addon is not loaded; the requested replacement starts enabled.
- Close WoW and relaunch through Ext with internet access for the navigation dependency setup; do not uninstall or clear data.

### Ext.4 add-on efficiency fixes

- The 30 Hz trail buffer uses circular indexing instead of shifting up to 89 entries per sample when full. It keeps the same newest 90 samples, order, timing, and stop behavior.
- Aggro tint cleans inactive frames once instead of repeating cleanup every frame. Re-enabling tint and reusing nameplate slots restores normal updates. No visible update rate is reduced. Device FPS impact remains unmeasured.
- These efficiency fixes remain included. The separate HUD collection optimization is not included.

### Retained Ext.3 fixes

- The old focus-to-planner bridge is superseded by Ext.5's destination-only navigation above.
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
   For Questie and navigation setup, fully close WoW and start it through this Ext build with internet access. Wait for setup to finish. Then check `/questie`, `/tomtom`, and WoW's add-on settings. Focus a quest to start automatic guidance. Do not clear app data or uninstall to repair it.
5. Report the Thor model, driver name, reproduction steps, and symptoms. Never attach login files or credentials.

Driver ZIPs need Adrenotools `meta.json` and an ARM64 shared library. Windows/Linux installers, arbitrary Mesa source archives and drivers for incompatible GPUs are not supported. An accepted ZIP is not proof of hardware compatibility. System selection does not replace or modify Android's driver.

Built and signature-checked by GitHub Actions. Not yet tested on a physical Thor. An imported or system GPU driver can crash the game; reset to the bundled default if needed.

The named `Polestar` and `Polestar_Nav` folders, plus recorded installer-managed Polestar folders, move into `Interface/AddOns-disabled-dsforever` without deletion. SavedVariables and other user add-ons remain. To restore Polestar, close WoW and move the reviewed folders back into `Interface/AddOns` without overwriting existing folders. Do not use both apps against the same game folder.

Upstream runtime components and credits are retained. Upstream documents unresolved component source/notice gaps in `RELEASE_NOTICES.md`; this test build does not assert those gaps are resolved. Supply your own authorised game installation and account.
