"""Local: /tmp/opencode/questie-tests-venv/bin/python -B companion/tests/test_game_fps.py"""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))
from wowstub import boot_world, docs, nsent, F

L, errs = boot_world(pre="FPS_CALLS = 0; function GetFramerate() return 39.5 end; function ToggleFramerate() FPS_CALLS = FPS_CALLS + 1 end")
assert not errs, errs
assert L.eval("FPS_CALLS") == 0, "never toggle at login"
n = nsent(L)
L.execute("WowCompanion.hub.fpsTick()")
sample = [d for d in docs(L, n) if d.get("type") == "gamefps"][-1]
assert sample["fps"] == 39.5
print("PASS actual GetFramerate sample on existing tick")
for expr in ("0/0", "math.huge", "-1", "0", "10001", "'bad'"):
    n = nsent(L)
    L.execute("function GetFramerate() return " + expr + " end; WowCompanion.hub.fpsTick()")
    assert "fps" not in [d for d in docs(L, n) if d.get("type") == "gamefps"][-1]
L.execute("GetFramerate = nil; WowCompanion.hub.fpsTick()")
print("PASS invalid and missing samples are unavailable")

# Load the real pause action code; no extra tap-pad row is allocated.
radial = ROOT / "companion/app/src/main/assets/companion/addons/WowCompanionSpells/Radial.lua"
L.execute(radial.read_text())
L.execute("WowCompanion.pad.rows.pause.handler(nil, 28)")
assert L.eval("FPS_CALLS") == 1
L.execute("WowCompanion.pad.rows.pause.handler(nil, 29)")
assert L.eval("FPS_CALLS") == 1
print("PASS one pause:28 player action toggles once; unrelated columns do not")

# A client with the native binding but no ToggleFramerate global must still work.
L.execute("ToggleFramerate = nil; BIND_CALLS = 0; function RunBinding(command) assert(command == 'TOGGLEFPS'); BIND_CALLS = BIND_CALLS + 1 end")
L.execute("WowCompanion.pad.rows.pause.handler(nil, 28)")
assert L.eval("BIND_CALLS") == 1
assert L.eval("FPS_CALLS") == 1, "never send both native routes"
print("PASS missing Lua global uses native TOGGLEFPS binding once")

# Execute the actual controller command, not a source-string assertion.
import re
padmap = Path(F["padmap"]).read_text()
body = re.search(r'key = "DSFPS".*?body = "([^"]+)"', padmap).group(1)
assert body.startswith("/run ")
L.execute(body[5:])
assert L.eval("BIND_CALLS") == 2
L.execute("RunBinding = nil; function ToggleFramerate() FPS_CALLS = FPS_CALLS + 1 end")
L.execute(body[5:])
assert L.eval("FPS_CALLS") == 2
L.execute("ToggleFramerate = nil; FPS_MESSAGE = nil; function print(message) FPS_MESSAGE = message end")
L.execute(body[5:])
assert L.eval("FPS_MESSAGE") == "WoW FPS counter unavailable"
L.execute("FPS_MESSAGE = nil; WowCompanion.pad.rows.pause.handler(nil, 28)")
assert L.eval("FPS_MESSAGE") == "WoW FPS counter unavailable"
print("PASS controller command routes once on modern/legacy clients; unavailable routes explain why")
