"""Source-only HUD guards. Android measurement is covered by PerformanceHudViewTest.

Run locally: python3 -B tools/test_performance_hud_horizontal.py
These checks do not substitute for Robolectric or device validation.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
HUD = (ROOT / "app/src/main/java/app/gamenative/ui/widget/PerformanceHudView.kt").read_text()
CALLER = (ROOT / "app/src/main/java/app/gamenative/ui/screen/xserver/XServerScreen.kt").read_text()


class HorizontalHudSourceTest(unittest.TestCase):
    def test_one_existing_wrap_renderer(self):
        self.assertIn("private val compactContainer = WrapLayout(context)", HUD)
        self.assertNotIn("stackedContainer", HUD)
        self.assertNotIn("LinearLayout.VERTICAL", HUD)
        self.assertIn("orientation = LinearLayout.HORIZONTAL", HUD)

    def test_old_true_false_and_absent_mode_preferences_all_use_same_layout(self):
        self.assertNotIn("performanceHudCompactMode", CALLER)
        self.assertNotIn("initialCompactMode", HUD + CALLER)
        self.assertNotIn("togglePerformanceHudLayout", CALLER)

    def test_width_is_bounded_and_only_overflow_wraps(self):
        self.assertIn("measureChild(child, widthMeasureSpec, heightMeasureSpec)", HUD)
        self.assertIn("lineWidth > 0 && proposedWidth > maxContentWidth", HUD)
        self.assertIn("proposedRight - paddingLeft > maxContentWidth", HUD)
        self.assertIn("textView.maxLines = 1", HUD)
        self.assertIn("textView.ellipsize = TextUtils.TruncateAt.END", HUD)
        compact = HUD.split("val compactContainer = LinearLayout(context)")[1]
        self.assertIn("ViewGroup.LayoutParams.WRAP_CONTENT,\n                    1f,", compact)

    def test_game_and_output_remain_independently_measured(self):
        self.assertIn('GameFps.label("Output", currentFps) + " (presents)"', HUD)
        self.assertIn('GameFps.label("Game", gameFpsProvider())', HUD)
        self.assertIn("LsfgVkManager.readMeasuredFps(container)", CALLER)
        self.assertIn("GameFps.read(android.os.SystemClock.elapsedRealtime())", CALLER)
        self.assertIn("const val UPDATE_INTERVAL_MS = 1_000L", HUD)

    def test_metric_and_graph_choices_still_drive_cached_attachment(self):
        for metric in ("fps", "cpu", "gpu"):
            self.assertIn(f"{metric}Metric.compactGraph?.addSample(snapshot.{metric}Value)", HUD)
        self.assertIn("if (signatures != attachedMetricSignature)", HUD)
        self.assertIn("val shouldShowGraph = showGraph && metric.supportsGraph", HUD)
        self.assertIn("val shouldShowText = enabled && hasText", HUD)

    def test_drag_and_saved_bounds_stay_in_caller(self):
        for fragment in (
            "val maxX = (host.width - hud.width).coerceAtLeast(0).toFloat()",
            "val maxY = (host.height - hud.height).coerceAtLeast(0).toFloat()",
            "hud.x = (rawX - hostLocation[0] - performanceHudDragOffsetX).coerceIn(0f, maxX)",
            "hud.y = (rawY - hostLocation[1] - performanceHudDragOffsetY).coerceIn(0f, maxY)",
            "if (!isDraggingPerformanceHud) restorePerformanceHudPosition()",
            "movePerformanceHud(event.rawX, event.rawY, save = true)",
        ):
            self.assertIn(fragment, CALLER)


if __name__ == "__main__":
    unittest.main()
