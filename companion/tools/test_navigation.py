"""Run pinned upstream Lua, without a client or network. Requires lupa (Lua 5.1).

Inputs: SHORTEST_PATH_FOREVER_ZIP and QUESTIE_BUNDLE_ZIP. Archives stay outside the repo.
"""
import hashlib
import os
from pathlib import Path
import unittest
import zipfile

from lupa.lua51 import LuaRuntime


SPF = Path(os.environ.get("SHORTEST_PATH_FOREVER_ZIP",
    "/tmp/opencode/ds-ext-navigation/ShortestPathForever-v1.45.3-forever.zip"))
QUESTIE = Path(os.environ.get("QUESTIE_BUNDLE_ZIP", "/tmp/opencode/questie-12.12.0.zip"))


def source(archive, name):
    with zipfile.ZipFile(archive) as z:
        return z.read(name).decode("utf-8")


@unittest.skipUnless(SPF.is_file() and QUESTIE.is_file(), "provide the pinned upstream ZIP inputs")
class NavigationTest(unittest.TestCase):
    def test_pins_load_list_and_defaults(self):
        self.assertEqual(20_019_152, SPF.stat().st_size)
        self.assertEqual("09a54bb8ed17d4cff74211321202b4c66de180a7e9e53f5713a7ec52ff993ef9",
                         hashlib.sha256(SPF.read_bytes()).hexdigest())
        self.assertEqual("116eadd5833836d1b8ff30c9c8b5aaf724bc2de75eea362d6eee3d9962506b5f",
                         hashlib.sha256(QUESTIE.read_bytes()).hexdigest())
        toc = source(SPF, "ShortestPathForever/ShortestPathForever.toc")
        for field in ("## Interface: 16001", "## Version: 1.45.3", "## X-Forever-Core: v1.17.1",
                      "## X-License: GPL-3.0-or-later"):
            self.assertIn(field, toc)
        self.assertNotRegex(toc, r"(?m)^## (RequiredDeps|Dependencies):")
        with zipfile.ZipFile(SPF) as z:
            self.assertEqual({"ShortestPathForever"}, {n.split('/')[0] for n in z.namelist()})
            for line in toc.splitlines():
                if line.strip() and not line.startswith('#'):
                    self.assertIn("ShortestPathForever/" + line.strip().replace('\\', '/'), z.namelist())
        core = source(SPF, "ShortestPathForever/Core/Core.lua")
        defaults = core[core.index("local DEFAULTS = {"):core.index("ns.Defaults = DEFAULTS")]
        lua = LuaRuntime()
        db = lua.execute(defaults + "\nreturn DEFAULTS")
        for key in ("arrow", "minimapLine", "mapLine", "journey", "tomtom"):
            self.assertTrue(db[key], key)
        self.assertFalse(db.focusedQuest)
        self.assertFalse(db.tomtomFollow)
        route = source(SPF, "ShortestPathForever/UI/Route.lua")
        self.assertIn("if ns.db.minimapLine == false then", route)
        self.assertIn("Strokes.Minimap(journeyStrokes, linePaths, view)", route)
        self.assertIn("line:SetTexture(DOT_TEXTURE)", route)
        self.assertIn("return ns.db.arrow ~= false", source(SPF, "ShortestPathForever/UI/Arrow.lua"))
        input_source = source(SPF, "ShortestPathForever/Journey/JourneyInput.lua")
        self.assertIn("if not IsAltKeyDown() or IsShiftKeyDown() then", input_source)

    def test_actual_questie_target_through_actual_shim_routes_once(self):
        lua = LuaRuntime()
        lua.execute("""
            routes, cancelled, inits = {}, {}, {}
            canaccessvalue = function() return true end
            C_Map = {GetMapInfo = function(id) return {mapID=id} end}
            C_AddOns = {DoesAddOnExist = function() return false end}
            UnitGUID = function() return 'test-player' end
            function CreateFrame()
                return {RegisterEvent=function() end, UnregisterEvent=function() end,
                        SetScript=function(self, event, fn) self[event]=fn end}
            end
            function hooksecurefunc(t, k, hook)
                local original = t[k]
                t[k] = function(...) local result = original(...); hook(...); return result end
            end
            TrackerUtils = {}
            Questie = {db={char={}}}
            QuestieLoader = {ImportModule=function(_, name)
                assert(name == 'TrackerUtils'); return TrackerUtils
            end}
            TrackerMapEligibility = {RefreshAndGetCapabilities=function(id)
                return {Id=id}, {canNavigateQuest = id == 42}
            end}
            DistanceUtils = {GetNearestSpawnForQuest=function() return {25,75}, 12, 'Quest target' end}
            ZoneDB = {GetUiMapIdByAreaId=function(_, zone) assert(zone==12); return 99 end}
            ns = {
                db={tomtom=true, journey=true},
                Init=function(fn) inits[#inits+1]=fn end,
                QuestID=function(id) return type(id)=='number' and id>0 and id%1==0 and id or nil end,
                JourneyChanged=function() end, PointGuideArrow=function() end
            }
            ShortestPathForever = {API={
                NavigateRoute=function(owner, stops)
                    routes[#routes+1] = {owner=owner, stops=stops}; return true
                end,
                Cancel=function(owner) cancelled[#cancelled+1]=owner end,
                CurrentStop=function() return nil end
            }}
        """)
        tracker = source(QUESTIE, "Questie/Modules/Tracker/TrackerUtils.lua")
        # Run the real three synchronous handlers, with only game/data lookups stubbed.
        for signature in ("TrackerUtils.IsTomTomAvailable()", "TrackerUtils:SetTomTomTarget(title, zone, x, y)",
                          "TrackerUtils.SetQuestTomTomTarget(questId, expectedQuest)"):
            start = tracker.index("function " + signature)
            end = tracker.index("\nend", start) + len("\nend")
            lua.execute(tracker[start:end])
        lua.execute(source(SPF, "ShortestPathForever/Core/TomTom.lua"), "ShortestPathForever", lua.globals().ns)
        lua.execute("""
            for _, fn in ipairs(inits) do fn() end
            assert(TrackerUtils.IsTomTomAvailable())
            assert(TrackerUtils.SetQuestTomTomTarget(42) == true)
            assert(#routes == 1)
            assert(routes[1].owner == 'TomTom')
            local stop = routes[1].stops[1]
            assert(stop.map == 99 and stop.x == .25 and stop.y == .75)
            assert(stop.questID == 42 and stop.title == 'Quest target')
            TomTom:SetCrazyArrow(Questie.db.char._tom_waypoint)
            assert(#routes == 1, 'same waypoint must not plan twice')
            assert(TrackerUtils.SetQuestTomTomTarget(0) == false)
            assert(#routes == 1, 'ineligible quest must not plan')
            assert(TomTom:RemoveWaypoint(Questie.db.char._tom_waypoint))
            assert(#cancelled == 1 and cancelled[1] == 'TomTom')
            ns.db.tomtom = false
            ns.RefreshTomTom()
            assert(TomTom == nil, 'player shim opt-out must stand')
        """)

    def test_actual_minimap_walk_geometry_is_separated_breadcrumbs(self):
        lua = LuaRuntime()
        lua.execute("ns = {FlightLinePoints=function(path) return path.points end}")
        lua.execute(source(SPF, "ShortestPathForever/UI/Strokes.lua"), "ShortestPathForever", lua.globals().ns)
        lua.execute("""
            local strokes = ns.Strokes.New()
            ns.Strokes.Minimap(strokes, {
                {mode='walk', points={{map=1,x=0,y=0}, {map=1,x=0,y=-90}}}
            }, {x=0,y=0,map=1,radius=100,facing=0,width=200,height=200,scale=1,
                colors={walk={1,1,0,1}}, goalRadius=5})
            assert(strokes.n > 3, 'walk must generate breadcrumbs')
            for i=1,strokes.n do
                assert(strokes.dot[i], 'walk must use dot texture')
                assert(strokes.width[i] == 4)
                if i > 1 then
                    assert(strokes.x1[i] > strokes.x2[i-1], 'breadcrumbs must have gaps')
                end
            end
        """)


if __name__ == "__main__":
    unittest.main()
