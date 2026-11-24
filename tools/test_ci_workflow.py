"""Guard the parallel release boundary without adding a YAML dependency."""
from pathlib import Path
import re
import unittest


WORKFLOW = (Path(__file__).resolve().parents[1] /
            ".github/workflows/android-release.yml").read_text()
JOBS = dict(re.findall(r"^  (\w+):\n(.*?)(?=^  \w+:\n|\Z)",
                       WORKFLOW.split("\njobs:\n", 1)[1], re.M | re.S))

# Exact selections from the clean 6d3f0d6b workflow, before the job split.
LAUNCHER_TESTS = """
*WowGpuDriversTest *OutputFpsTest *GameFpsTest *FpsCounterTest *PerformanceTilesRoomTest
*GpuDriverMenuUiTest *WowGpuDriverControllerTest *StartMenuShotsTest
*AddonTilesRoomTest.navigationUsesTheExistingCardAndCanBeRemovedWithoutChangingQuestie
*BottomRouteShotsTest *KeyboardRepeatShiftTest *XInputQueueTest *XInputHoldTest *XInputWiringTest
*GameSessionServiceTest *WowLaunchSwitchTest *FirstRunArtTest *CrashStateLineTest *WowCdnStreamTest
*HomeMapControlsShotsTest *FrameGen*Test *FrameLimitTest
""".split()
COMPANION_TESTS = """
*QuestieReplacementTest *ExtUpdateTest *AddonsReenableTest *PolestarGuideTest *ThirdPartyAddonsTest
*ImportedAddonsTest *PcAddonsWantTest *SettingsTabsTest *PerformanceTileModelTest *GpuDriverMenuTest
*QuestieStartupTest *DependencyQuestieTest *AddonBackupsTest *NavigationStartupTest *SpfRouteTest
*NativeMenuOwnershipTest *LogoutSavedVariablesTest *HomeLayoutRulesTest *HomeLayoutStoreTest
*HomeEditModelTest *RealmKeyTest *LoadGateTest *GameVideoMemoryTest *Light8Test *Light8UnchangedTest
*FirstRunFolderTest *CdnHttpsTest *InstallFixesTest *MapHoldClaimTest *ResetPlanTest *ResetFlowTest
""".split()


class ParallelReleaseTest(unittest.TestCase):
    def test_publication_requires_all_successful_parallel_jobs(self):
        self.assertEqual({"launcher_tests", "companion_tests", "lua", "build", "release"}, set(JOBS))
        for job in ("launcher_tests", "companion_tests", "lua", "build"):
            self.assertNotRegex(JOBS[job], re.compile(r"^    needs:", re.M))
            self.assertNotIn("continue-on-error:", JOBS[job])
        self.assertIn("    needs: [launcher_tests, companion_tests, lua, build]\n", JOBS["release"])
        self.assertNotIn("always()", JOBS["release"])
        self.assertNotIn("!cancelled()", JOBS["release"])

    def test_tests_and_diagnostics_stay_out_of_signing_job(self):
        for job, task, selections in (
            ("launcher_tests", ":app:testModernDebugUnitTest", LAUNCHER_TESTS),
            ("companion_tests", ":dsforever:testDebugUnitTest", COMPANION_TESTS),
        ):
            self.assertIn(task, JOBS[job])
            self.assertEqual(selections, re.findall(r"--tests '([^']+)'", JOBS[job]))
            for other in set(JOBS) - {job}:
                self.assertNotIn(task, JOBS[other])
            self.assertNotIn("secrets.", JOBS[job])
        for command in ("companion/tools/test_questie_replacement.py",
                        "companion/tools/test_navigation.py", "companion/tools/test_bottom_route.py",
                        "companion/tests/test_game_fps.py", "companion/tools/test_focus_menu.py",
                        "companion/tools/test_logout_save.py"):
            self.assertIn(command, JOBS["lua"])
            self.assertNotIn(command, JOBS["launcher_tests"])
            self.assertNotIn(command, JOBS["companion_tests"])
            self.assertNotIn(command, JOBS["build"])
        self.assertNotIn("secrets.", JOBS["lua"])
        for job, artifact in (("launcher_tests", "launcher-menu-ui-screenshots"),
                              ("launcher_tests", "launcher-test-reports"),
                              ("companion_tests", "companion-test-reports")):
            self.assertRegex(JOBS[job],
                              rf"if: always\(\)\n        with:\n          name: {artifact}\n")
        for path in ("app/build/test-results/", "app/build/reports/tests/",
                     "${{ runner.temp }}/ds-ext-menu-shots/"):
            self.assertIn(path, JOBS["launcher_tests"])
        for path in ("dsforever/build/test-results/", "dsforever/build/reports/tests/"):
            self.assertIn(path, JOBS["companion_tests"])
        for variable in ("TRADE_SHOTS_DIR", "ADDON_TILES_SHOTS_DIR", "BOTTOM_ROUTE_SHOTS_DIR"):
            self.assertIn(f"{variable}: ${{{{ runner.temp }}}}/ds-ext-menu-shots", JOBS["launcher_tests"])
        names = [name for job in ("launcher_tests", "companion_tests", "lua", "build")
                 for name in re.findall(r"^          name: (.+)$", JOBS[job], re.M)]
        self.assertEqual(len(names), len(set(names)), "artifact names must be unique")

    def test_companion_uses_only_jvm_prerequisites_and_verifies_all_archives(self):
        companion = JOBS["companion_tests"]
        for required in ("actions/setup-java@v4", "gradle/actions/setup-gradle@v4",
                         '"platforms;android-36"', '"build-tools;36.1.0"'):
            self.assertIn(required, companion)
        for forbidden in ("ci_bootstrap", "actions/cache@", '"ndk;', '"cmake;',
                          "assembleModernRelease", "WC_KEY"):
            self.assertNotIn(forbidden, companion)
        for archive, digest in (
            ("QUESTIE_BUNDLE_ZIP", "116eadd5833836d1b8ff30c9c8b5aaf724bc2de75eea362d6eee3d9962506b5f"),
            ("QUESTIEDB_FOREVER_ZIP", "77560ed5a425de989b45bbaddb3fd0eb2173df25f43c4d5c3ef8b4622985486c"),
            ("SHORTEST_PATH_FOREVER_ZIP", "09a54bb8ed17d4cff74211321202b4c66de180a7e9e53f5713a7ec52ff993ef9"),
        ):
            self.assertIn(f'--output "${archive}"', companion)
            check = f'echo "{digest}  ${archive}" | sha256sum --check'
            self.assertIn(check, companion)
            self.assertLess(companion.index(check), companion.index("bash gradlew"))

    def test_lua_has_its_own_verified_inputs_without_android(self):
        lua = JOBS["lua"]
        for forbidden in ("setup-java", "setup-gradle", "sdkmanager", "ci_bootstrap", "gradlew"):
            self.assertNotIn(forbidden, lua)
        for archive, digest in (
            ("QUESTIE_BUNDLE_ZIP", "116eadd5833836d1b8ff30c9c8b5aaf724bc2de75eea362d6eee3d9962506b5f"),
            ("SHORTEST_PATH_FOREVER_ZIP", "09a54bb8ed17d4cff74211321202b4c66de180a7e9e53f5713a7ec52ff993ef9"),
        ):
            self.assertIn(f'--output "${archive}"', lua)
            check = f'echo "{digest}  ${archive}" | sha256sum --check'
            self.assertIn(check, lua)
            self.assertLess(lua.index(check), lua.index("python3 -m venv"))
            self.assertIn(f'test -s "${archive}"', lua)
        for test in ("test_controller_situations", "test_chooser_grid", "test_realm_key",
                     "test_roombar_mirror", "test_combat_top_flag", "test_pad_order",
                     "test_map_stale_pend", "test_aura_container"):
            self.assertIn(test, lua)
        self.assertIn("'lupa==2.6'", lua)

    def test_download_cache_is_narrow_exact_and_identical_in_android_jobs(self):
        caches = []
        for job in ("launcher_tests", "build"):
            cache = JOBS[job].split("      - uses: actions/cache@v4\n", 1)[1].split("      - name:", 1)[0]
            caches.append(cache)
            paths = re.findall(r"^            (\S+)$", cache, re.M)
            self.assertEqual([
                ".bootstrap/component-downloads/proton-11.0-90624-arm64ec.wcp",
                ".bootstrap/component-downloads/dxvk-2.4.1-wow-aarch64-test.wcp",
                ".bootstrap/component-downloads/turnip-wow-scheduler-test.zip",
                ".bootstrap/component-downloads/turnip-V32-RP6sched.zip",
                ".bootstrap/bridge-toolchain/llvm-mingw-20261104-ucrt-ubuntu-22.04-x86_64.tar.xz",
            ], paths)
            self.assertNotIn("restore-keys:", cache)
            self.assertIn("${{ runner.os }}-${{ runner.arch }}", cache)
            for path in ("tools/ci_bootstrap.sh", "tools/fetch_components.sh", "tools/components.sha256",
                         "tools/wow-session-bridge/build_dsf.sh", "tools/wow-session-bridge/build.sh",
                         "tools/wow-session-bridge/wow-session-bridge.sha256"):
                self.assertIn(f"'{path}'", cache)
            self.assertNotIn("cache-hit", JOBS[job])
        self.assertEqual(*caches)

    def test_candidate_keeps_pinned_bootstrap_and_signing_cleanup(self):
        for job in ("launcher_tests", "build"):
            self.assertIn("run: bash tools/ci_bootstrap.sh", JOBS[job])
            self.assertLess(JOBS[job].index("verify_addon_manifest(Path"),
                            JOBS[job].index("actions/setup-java"))
        self.assertLess(JOBS["companion_tests"].index("verify_addon_manifest(Path"),
                        JOBS["companion_tests"].index("actions/setup-java"))
        self.assertIn("WC_KEY_ALIAS: ds-forever-ext", JOBS["build"])
        self.assertIn(":app:assembleModernRelease", JOBS["build"])
        self.assertIn("run: python3 tools/ci_package.py", JOBS["build"])
        self.assertIn('if: always()\n        run: rm -f "$RUNNER_TEMP/ext-release.p12"',
                      JOBS["build"])


if __name__ == "__main__":
    unittest.main()
