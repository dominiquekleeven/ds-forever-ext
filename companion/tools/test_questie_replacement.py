"""Ext addon regressions. Run with Python + lupa; no WoW install or network is needed."""
import hashlib
from pathlib import Path
import re
import unittest

from lupa import lua51


ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "companion/app/src/main/assets/companion"
ADDONS = ASSETS / "addons"


class QuestieReplacement(unittest.TestCase):
    def test_manifest_and_lua_compile(self):
        lines = (ASSETS / "manifest.txt").read_text().splitlines()
        self.assertEqual("set 431", lines[0])
        listed = dict(line.split("  ", 1)[::-1] for line in lines[1:])
        actual = {p.relative_to(ADDONS).as_posix(): hashlib.md5(p.read_bytes()).hexdigest()
                  for p in ADDONS.rglob("*") if p.is_file()}
        self.assertEqual(actual, listed)
        lua = lua51.LuaRuntime()
        compile_lua = lua.eval("function(src) local f, err = loadstring(src); return err end")
        for p in ADDONS.rglob("*.lua"):
            self.assertIsNone(compile_lua(p.read_text()), str(p))
        for p in ADDONS.rglob("*.toc"):
            self.assertIn("## Version: Set 431", p.read_text())

    def test_polestar_does_not_register_hooks_or_read_user_addon(self):
        source = (ADDONS / "WowCompanion/Map.lua").read_text()
        start = source.index("-- Retired in Ext:")
        end = source.index("end -- retired Polestar integration") + len("end -- retired Polestar integration")
        lua = lua51.LuaRuntime()
        lua.execute("""WC = {}; Polestar = setmetatable({}, {__index = function() error('Polestar accessed') end})
            C_Timer = setmetatable({}, {__index = function() error('timer registered') end})
            function CreateFrame() error('frame registered') end""")
        lua.execute(source[start:end])
        self.assertIsNone(lua.globals().WC.pnav)
        self.assertIsNone(lua.globals().WC.pqol)
        self.assertIsNone(lua.globals().WC.pg)
        self.assertIsNone(lua.globals().WC.pstep)

    def test_polestar_is_not_a_bindable_menu_choice(self):
        source = (ADDONS / "WowCompanionSpells/PadMap.lua").read_text()
        start = source.index("local COMMANDS = {")
        end = source.index("\n}", start) + 2
        lua = lua51.LuaRuntime()
        commands = lua.execute(source[start:end] + "\nreturn COMMANDS")
        keys = {row.key for row in commands.values()}
        self.assertFalse(keys & {"DSPSNEXT", "DSPSPREV", "DSPSMENU"})
        self.assertIn("DSMAP", keys)
        self.assertIn("DSPTT", keys)

    def test_questie_settings_use_its_own_slash_handler(self):
        source = (ADDONS / "WowCompanionInteract/Interact.lua").read_text()
        start = source.index("function ACT.questieopen()")
        end = source.index("function ACT.qtopen()", start)
        lua = lua51.LuaRuntime()
        lua.execute("""ACT = {}; fight = false; opened = 0
            function InCombatLockdown() return fight end
            SlashCmdList = { ACECONSOLE_QUESTIE = function(input)
                assert(input == ''); opened = opened + 1 end }""")
        lua.execute(source[start:end])
        self.assertTrue(lua.globals().ACT.questieopen())
        self.assertEqual(1, lua.globals().opened)
        lua.globals().fight = True
        self.assertEqual((False, "combat"), lua.globals().ACT.questieopen())
        self.assertEqual(1, lua.globals().opened)
        self.assertIn('doc.questie = { open = slot("iact", "questieopen") }', source)

    def test_official_bundle_pin_is_separate_from_the_database(self):
        source = (ROOT / "companion/app/src/main/java/com/jrees/wowcompanion/ThirdPartyAddons.kt").read_text()
        self.assertIn('repo = "Questie/Questie", version = "12.12.0"', source)
        self.assertIn('"116eadd5833836d1b8ff30c9c8b5aaf724bc2de75eea362d6eee3d9962506b5f", listOf("Questie")', source)
        active = re.search(r"var ALL: List<Addon> = listOf\(([^\n]+)\)", source)[1]
        self.assertIn("QUESTIE", active)
        self.assertNotIn("POLESTAR", active)


if __name__ == "__main__":
    unittest.main()
