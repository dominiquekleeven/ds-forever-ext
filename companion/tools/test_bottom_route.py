"""Local-only execution of our exporter and pinned SPF public API fixture slices. No client/network.

Run with the existing lupa environment. SHORTEST_PATH_FOREVER_ZIP can name the pinned ZIP.
"""
import hashlib
import json
import os
from pathlib import Path
import unittest
import zipfile

from lupa.lua51 import LuaRuntime

ROOT = Path(__file__).resolve().parents[1]
ZIP = Path(os.environ.get("SHORTEST_PATH_FOREVER_ZIP",
    "/tmp/opencode/ds-ext-navigation/ShortestPathForever-v1.45.3-forever.zip"))


def plain(t):
    if not hasattr(t, "items"):
        return t
    pairs = list(t.items())
    if pairs and all(isinstance(k, (int, float)) for k, _ in pairs):
        return [plain(t[i]) for i in range(1, len(t) + 1)]
    return {k: plain(v) for k, v in pairs}


class BottomRouteTest(unittest.TestCase):
    def rig(self, actual=True):
        lua = LuaRuntime(unpack_returned_tuples=True)
        lua.globals().encode = lambda t: json.dumps(plain(t), separators=(",", ":"), sort_keys=True)
        lua.execute("""
            time=0; documents={}; frames={}; reads=0
            function GetTime() return time end
            function CreateFrame()
                local f={RegisterEvent=function() end, SetScript=function(self, _, fn) self.event=fn end}
                frames[#frames+1]=f; return f
            end
            function CreateVector2D(x,y) return {x=x,y=y,GetXY=function(self) return self.x,self.y end} end
            function UnitPosition() return 950,1950,0,0 end
            C_Map={
                GetWorldPosFromMapPos=function(map,p)
                    if map==999 then return 1,CreateVector2D(1000,2000) end
                    if map~=42 then return nil end
                    return 0,CreateVector2D(1000-500*p.y,2000-1000*p.x)
                end,
                GetMapPosFromWorldPos=function(map,p)
                    if map~=0 then return nil end
                    return 42,CreateVector2D((2000-p.y)/1000,(1000-p.x)/500)
                end
            }
            WC={encode=encode,send=function(s) documents[#documents+1]=s end,
                collectPos=function() return 'pos' end,collectMap=function() return 'map' end}
        """)
        if actual:
            self.actual_api(lua)
        text = (ROOT / "app/src/main/assets/companion/addons/WowCompanion/Map.lua").read_text()
        lua.execute(text[text.index("-- SPF_BOTTOM_ROUTE_BEGIN"):text.index("-- SPF_BOTTOM_ROUTE_END")])
        return lua

    def actual_api(self, lua):
        self.assertEqual("09a54bb8ed17d4cff74211321202b4c66de180a7e9e53f5713a7ec52ff993ef9",
                         hashlib.sha256(ZIP.read_bytes()).hexdigest())
        with zipfile.ZipFile(ZIP) as z:
            def source(name):
                return z.read("ShortestPathForever/" + name).decode()
            lua.execute("""
                canaccessvalue=function() return true end
                ForeverCore={APIKit={}}
                ns={db={journey=true},charDB={}, StepText=function() return 'Walk' end,LegSpan=function() return 0 end,
                    JourneyPosition=function() return 950,1950,0,0 end,
                    PlanContext={LegPoints=function(leg) return leg.points end}}
                points={{map=0,x=1000,y=2000},{map=0,x=900,y=1900},
                        {map=0,x=800,y=1700},{map=0,x=600,y=1600}}
                plan={legs={{mode='boat',points={points[1],points[2]}},
                           {mode='walk',points=points},{mode='boat',points={points[4],points[1]}},
                           {mode='walk',points=points}}}
                legIndex=2; bend=3; guided=true; paused=false; loading=false; present=true
                Guide={Paused=function() return paused end,Casting=function() return false end}
                ns.GuideState=function() reads=reads+1; return points[bend],points,bend end
                ns.JourneyPlan=function() return not loading and plan or nil,legIndex end
                ns.JourneyGoal=function() return present and points[4] or nil end
                ns.JourneyOwner=function() return 'Questie' end
                ns.JourneyTitle=function() return 'Destination' end
                ns.IsJourneyGuided=function() return guided end
                ns.JourneyTime=function() return 10000 end
                ns.HasJourney=function() return present end
                ns.JourneyStatus=function() return nil,nil,nil,loading end
                API={}
            """)
            # Actual Place / Loaded / Number definitions; APIInput's other helpers are harmless until called.
            lua.execute("return function(...) " + source("Core/APIInput.lua") + " end")("SPF", lua.globals().ns)
            steps = source("Journey/JourneySteps.lua")
            lua.execute("local function Whole(ms) return math.max(0,math.ceil(ms/1000)) end\n" +
                        steps[steps.index("local function LegEnds"):steps.index("-- A teleport step,")])
            guide = source("Journey/JourneyGuide.lua")
            start = guide.index("function ns.JourneyGuidance(project)")
            end = guide.index("\nend local _, ForeverCoreHostNs", start)
            lua.execute(guide[start:end])
            api = source("Core/API.lua")
            start = api.index("function API.Journey()")
            end = api.index("-- What a consumer driving", start)
            lua.execute("local Loaded,Place=ns.APIInput.Loaded,ns.APIInput.Place\n" +
                        "local function Loading() return nil,'loading' end\n" +
                        "local function None() return nil,'none' end\n" + api[start:end] +
                        "\nfunction API.Guidance() return ns.JourneyGuidance(Place) end\nShortestPathForever={API=API}")

    def tick(self, lua, time):
        lua.globals().time = time
        lua.execute("WC.collectPos()")
        return json.loads(lua.globals().documents[len(lua.globals().documents)])

    def test_actual_api_axis_current_leg_and_current_bend(self):
        lua = self.rig()
        doc = self.tick(lua, 0)
        self.assertTrue(doc["on"])
        self.assertEqual(2, doc["leg"])
        self.assertEqual(0, doc["c"])
        self.assertEqual([900, 1900, 800, 1700, 600, 1600], doc["p"])
        self.assertEqual([800, 1700], doc["n"])
        lua.execute("legIndex=3")  # boat, even though there is a later same-continent walk
        self.assertFalse(self.tick(lua, 1)["on"])

    def test_middle_bend_signature_and_throttle_and_heartbeat(self):
        lua = self.rig()
        self.tick(lua, 0)
        for t in (0.1, 0.2, 0.9):
            self.tick(lua, t)
        self.assertEqual(1, len(lua.globals().documents))
        self.assertEqual(1, lua.globals().reads)
        self.tick(lua, 1)
        self.assertEqual(1, len(lua.globals().documents))
        lua.execute("points[3].y=1750")
        self.assertEqual(1750, self.tick(lua, 2)["p"][3])
        self.assertEqual(2, len(lua.globals().documents))
        self.tick(lua, 7)
        self.assertEqual(3, len(lua.globals().documents))

    def test_missing_paused_cancelled_loading_error_and_projection_clear(self):
        for change in ("paused=true", "present=false", "loading=true", "guided=false",
                       "ShortestPathForever=nil", "API.Guidance=function() error('bad') end",
                       "C_Map.GetWorldPosFromMapPos=function() return nil end",
                       "frames[1].event(nil,'LOADING_SCREEN_ENABLED')"):
            lua = self.rig()
            self.assertTrue(self.tick(lua, 0)["on"])
            lua.execute(change)
            self.assertFalse(self.tick(lua, 1)["on"], change)
            self.tick(lua, 20)
            self.assertEqual(2, len(lua.globals().documents), "idle is silent")
        lua = self.rig(actual=False)
        self.assertFalse(self.tick(lua, 0)["on"])
        self.tick(lua, 10)
        self.assertEqual(1, len(lua.globals().documents))

    def test_bounds_cross_continent_malformed_and_first_bend(self):
        for change in ("bend=0", "bend=0/0", "points[3].x=math.huge", "points[3].map=1",
                       "for i=1,4097 do points[i]={map=0,x=900,y=1900} end"):
            lua = self.rig()
            lua.execute(change)
            self.assertFalse(self.tick(lua, 0)["on"], change)
        lua = self.rig()
        lua.execute("bend=1")
        self.assertEqual([950, 1950, 1000, 2000], self.tick(lua, 0)["p"][:4])
        lua = self.rig()
        lua.execute("for i=1,500 do points[i]={map=0,x=1000-i/2,y=2000-i*2} end; bend=2")
        doc = self.tick(lua, 0)
        self.assertTrue(doc["on"])
        self.assertEqual(480, len(doc["p"]))
        self.assertLess(len(json.dumps(doc)), 12000)


if __name__ == "__main__":
    unittest.main()
