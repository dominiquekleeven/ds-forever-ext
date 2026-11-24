"""Logout-only settings preparation. Local Lua 5.1, no client/network needed."""
import json
from pathlib import Path
import sys
import unittest

from lupa.lua51 import LuaRuntime
from test_bottom_route import plain

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))
import wowstub

PADMAP = ROOT / "companion/app/src/main/assets/companion/addons/WowCompanionSpells/PadMap.lua"


class LogoutSaveTest(unittest.TestCase):
    def rig(self):
        lua = LuaRuntime()
        lua.globals().encode = lambda t: json.dumps(plain(t), sort_keys=True)
        lua.execute("""
            frames={};timers={};documents={};flushes=0;sorts=0;PV='test'
            WC={encode=encode,send=function(s) documents[#documents+1]=s end,flush=function() flushes=flushes+1 end}
            C_Timer={NewTicker=function(s,fn) timers[#timers+1]=fn end}
            function CreateFrame()
                local f={RegisterEvent=function(self,event) self.event=event end,
                    SetScript=function(self,_,fn) self.fn=fn end}
                frames[#frames+1]=f;return f
            end
            GetServerTime=function() return 1800000000 end
            inCombat=function() return false end;whoKey=function() return 'Other-Realm' end
            WowCompanionSpellsDB={dpad={['Player-Realm']=false}, keyProbe='native only', faceSwap=true,
                value=1, invalid=math.huge, unsupported=function() end}
            WowCompanionDB={chatTop={'native chat'},lay={['Player-Realm']={hud={dx=1}}}}
            originalSort=table.sort;table.sort=function(...) sorts=sorts+1;return originalSort(...) end
        """)
        code = PADMAP.read_text()
        a = code.index("-- m51 THE SETTINGS' OWN SAVE FILE")
        lua.execute(code[a:code.index("-- ---------------------------------------------------------------- m175 RESET", a)])
        return lua

    def test_no_periodic_walk_and_latest_tables_flush_only_at_logout_or_reload(self):
        for lifecycle in ("clean exit/logout", "/reload"):
            with self.subTest(lifecycle=lifecycle):
                lua = self.rig()
                lua.execute("""
                    assert(#timers==0 and sorts==0 and #documents==0, 'no recurring settings preparation')
                    WowCompanionSpellsDB.value=2;WowCompanionDB.lay['Player-Realm'].hud.dx=99
                    assert(sorts==0 and #documents==0, 'mutations must not serialize')
                    assert(WC.svKeep.send==nil, 'no forced in-play save entry point')
                    for _,f in ipairs(frames) do
                        assert(f.event=='PLAYER_LOGOUT')
                        f.fn(f,'PLAYER_LOGOUT')
                    end
                    assert(sorts>0 and #documents==2 and flushes==1)
                    assert(WowCompanionSpellsDB.keyProbe=='native only' and WowCompanionDB.chatTop[1]=='native chat',
                        'do not strip or mutate native SavedVariables')
                    -- Hub and PadMap may both receive the same event. One final snapshot only.
                    WC.svKeep.logout();assert(#documents==2 and flushes==1)
                """)
                docs = [json.loads(d) for d in lua.globals().documents.values()]
                saved = {d["var"]: d for d in docs}
                self.assertEqual(1800000000000, saved["WowCompanionSpellsDB"]["savedAt"])
                self.assertIn('["value"]=2', saved["WowCompanionSpellsDB"]["lua"])
                self.assertIn('["dx"]=99', saved["WowCompanionDB"]["lua"])
                for skipped in ("keyProbe", "faceSwap", "invalid", "unsupported"):
                    self.assertNotIn(skipped, saved["WowCompanionSpellsDB"]["lua"])
                self.assertNotIn("chatTop", saved["WowCompanionDB"]["lua"])

    def test_actual_login_reset_and_bye_ordering(self):
        lua, errors = wowstub.boot_world()
        self.assertFalse(errors, errors)
        start = wowstub.nsent(lua)
        lua.execute("""
            fire('PLAYER_LOGIN');fire('PLAYER_ENTERING_WORLD',true,false);advance(12)
            WowCompanionDB.compass=false
            WowCompanionDB.logoutLatest='latest before reset'
            tick(5);tick(30)
            WowCompanion.dsfReset(20)
        """)
        play = wowstub.docs(lua, start)
        self.assertFalse([d for d in play if d.get("type") == "svkeep"])
        self.assertTrue(any(d.get("type") == "dsfreset" and d.get("ok") for d in play))
        lua.execute("WowCompanionDB.logoutLatest='latest at logout';fire('PLAYER_LOGOUT')")
        final = wowstub.docs(lua, start)
        saves = [(i, d) for i, d in enumerate(final) if d.get("type") == "svkeep"]
        self.assertEqual(2, len(saves), "Hub and PadMap must not duplicate the save")
        bye = next(i for i, d in enumerate(final) if d.get("type") == "bye")
        self.assertTrue(all(i < bye for i, _ in saves), "save before bye marks this run inactive")
        db = next(d for _, d in saves if d["var"] == "WowCompanionDB")
        self.assertIn('["logoutLatest"]="latest at logout"', db["lua"])
        self.assertNotIn('["compass"]=', db["lua"], "the reset must survive the following /reload")

    def test_no_remaining_forced_save_callers(self):
        addons = ROOT / "companion/app/src/main/assets/companion/addons"
        for path in addons.rglob("*.lua"):
            self.assertNotIn("WC.svKeep.send", path.read_text(), str(path))


if __name__ == "__main__":
    unittest.main()
