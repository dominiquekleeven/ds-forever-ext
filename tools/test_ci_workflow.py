"""Guard the parallel release boundary without adding a YAML dependency."""
from pathlib import Path
import re
import unittest


WORKFLOW = (Path(__file__).resolve().parents[1] /
            ".github/workflows/android-release.yml").read_text()
JOBS = dict(re.findall(r"^  (\w+):\n(.*?)(?=^  \w+:\n|\Z)",
                       WORKFLOW.split("\njobs:\n", 1)[1], re.M | re.S))


class ParallelReleaseTest(unittest.TestCase):
    def test_publication_requires_both_successful_parallel_jobs(self):
        self.assertEqual({"tests", "build", "release"}, set(JOBS))
        for job in ("tests", "build"):
            self.assertNotRegex(JOBS[job], re.compile(r"^    needs:", re.M))
            self.assertNotIn("continue-on-error:", JOBS[job])
        self.assertIn("    needs: [tests, build]\n", JOBS["release"])
        self.assertNotIn("always()", JOBS["release"])
        self.assertNotIn("!cancelled()", JOBS["release"])

    def test_tests_and_diagnostics_stay_out_of_signing_job(self):
        for command in (":app:testModernDebugUnitTest", ":dsforever:testDebugUnitTest",
                        "companion/tools/test_questie_replacement.py",
                        "companion/tools/test_navigation.py", "companion/tools/test_bottom_route.py",
                        "companion/tests/test_game_fps.py", "companion/tools/test_focus_menu.py",
                        "companion/tools/test_logout_save.py", "*NativeMenuOwnershipTest",
                        "*LogoutSavedVariablesTest"):
            self.assertIn(command, JOBS["tests"])
            self.assertNotIn(command, JOBS["build"])
        self.assertNotIn("secrets.", JOBS["tests"])
        for artifact in ("menu-ui-screenshots", "test-reports"):
            self.assertRegex(JOBS["tests"],
                             rf"if: always\(\)\n        with:\n          name: {artifact}\n")

    def test_candidate_keeps_pinned_bootstrap_and_signing_cleanup(self):
        for job in ("tests", "build"):
            self.assertIn("run: bash tools/ci_bootstrap.sh", JOBS[job])
        self.assertIn("WC_KEY_ALIAS: ds-forever-ext", JOBS["build"])
        self.assertIn(":app:assembleModernRelease", JOBS["build"])
        self.assertIn("run: python3 tools/ci_package.py", JOBS["build"])
        self.assertIn('if: always()\n        run: rm -f "$RUNNER_TEMP/ext-release.p12"',
                      JOBS["build"])


if __name__ == "__main__":
    unittest.main()
