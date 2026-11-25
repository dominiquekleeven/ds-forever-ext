# Focused quest waypoint

## Official input

- TomTom `v4.4.1-release`, Cladhaire and Ludovicus.
- Official page: <https://www.wowinterface.com/downloads/info7032-TomTom.html>.
- Exact download: <https://cdn.wowinterface.com/downloads/file7032/TomTom-v4.4.1-release.zip?179406251815>.
- Archive size: `11790690` bytes.
- SHA-256: `68a93f8dfa4ecd3cafe28b0999b47ee62e65f0096b5e5982e6725ba83c10bffc`.
- `TomTom.toc` declares interface `16001`. `AddonCore.lua` detects Forever. The included HereBeDragons has Forever map support. The official changelog names Forever and disables world-map pins and map-click hooks in gamepad mode.
- The TOC says **All Rights Reserved**. The launcher downloads the unmodified official archive to the player's device. It does not redistribute TomTom in the repository or APK. Preserve all embedded notices. Modern artwork has its own CC BY-NC 4.0 notice and attribution in `Images/Modern/LICENSE.txt`.
- Questie remains the official `12.12.0` bundle. QuestieDB remains under its existing installer and contract checks.

## Behaviour

The companion uses Questie's `TrackerMapEligibility.RefreshAndGetCapabilities` and `DistanceUtils.GetNearestSpawnForQuest`. This is the same eligibility and destination choice used by Questie's TomTom action. Unfinished objectives come first. A completed quest points at its finisher. Only the focused quest is examined. No nearest-quest selection, navmesh, path generation or walking connector runs.

The same verified destination feeds the TomTom arrow, the native user waypoint (the game's floating destination marker), and a single diamond on both bottom maps. Each destination uses its own UiMap ID. If native waypoint support rejects or clamps the map, do not display a false native waypoint. TomTom and the bottom diamond still use the verified original goal. Native marker placement and visibility need a Forever device check.

App focus and native super-track changes are explicit focus choices. They can take the arrow from a manual destination. Other TomTom waypoints stay in its queue. A subsequent manual TomTom arrow or native waypoint change pauses following. Quest progress cannot take it back. Choose a quest again to resume. Missing destinations clear the previous owned goal; they do not relabel it.

`/wcnav off` disables following. `/wcnav on` enables it. This choice persists in `WowCompanionDB.focusNavigation`. If the old SPF table is already loaded, the bridge carries its recorded later focused-navigation opt-out once. WoW does not load a retired add-on's SavedVariables: a normal installer upgrade therefore starts this requested replacement enabled, without importing SPF's old opt-out. The old file stays intact. The bridge does not rewrite SPF or TomTom saved settings. While enabled, it uses TomTom's documented session-only suppression of POI following and closest-on-clear, to prevent duplicate sources and loops.

Quest events coalesce for 0.25 seconds. Identical destinations are not requeued. Loading screens and logout clear owned markers. Login removes an exact saved owned native marker and recalculates the current focus. Manual native pins are matched by map and coordinates before any clear. A missing goal retries only the focused quest on the existing 15-second heartbeat, so delayed Questie initialization can recover. A five-second small export heartbeat runs on the existing position path; it does not scan QuestieDB. Bottom markers expire after eight seconds without an export.

The launcher moves only an installer-recorded `ShortestPathForever` folder to unique `Interface/AddOns-disabled-dsforever/ShortestPathForever[~N]` storage, with the game closed. It does not prune that folder or touch WTF/SavedVariables. An unrecorded manual SPF install is not removed; the new focus bridge stands down while SPF is loaded. Polestar stays retired.

## Recovery and checks

Source backup: `/home/apc/infra/backups/ds-ext-waypoint-navigation-20261125/RECOVERY.md`.

Run `test_navigation.py`, `test_bottom_route.py`, and `test_focus_menu.py` with Lua 5.1 through `lupa`, `TOMTOM_ZIP`, and `QUESTIE_BUNDLE_ZIP`. The tests execute real upstream waypoint/suppression and Questie eligibility/unfinished-objective code. Engine frames, native waypoint APIs, arrow rendering, and the final spawn-distance lookup use controlled fixtures. They do not prove device performance or absence of runtime taint.

Run the JVM `NavigationStartupTest`, `ThirdPartyAddonsTest`, `SpfRouteTest`, `SettingsTabsTest`, and existing Questie/installer tests. Run the Android `BottomRouteShotsTest` and navigation card test in CI. `SpfRoute` and its painter retain their internal class names for the two map callers, but accept only `destination/dst1` and never draw a route.
