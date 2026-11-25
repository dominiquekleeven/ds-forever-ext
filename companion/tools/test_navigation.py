"""Local Lua 5.1 regressions using the pinned official TomTom and Questie archives. No client/network."""
import hashlib
import json
import os
from pathlib import Path
import unittest
import zipfile
from lupa.lua51 import LuaRuntime

ROOT = Path(__file__).resolve().parents[1]
ADDONS = ROOT / "app/src/main/assets/companion/addons"
TOMTOM = Path(os.environ.get("TOMTOM_ZIP", "/tmp/opencode/TomTom-v4.4.1-release.zip"))
QUESTIE = Path(os.environ.get("QUESTIE_BUNDLE_ZIP", "/tmp/opencode/questie-12.12.0.zip"))


def source(archive, name):
    with zipfile.ZipFile(archive) as z:
        return z.read(name).decode("utf-8")


def plain(t):
    if hasattr(t, "items"):
        return {str(k): plain(v) for k, v in t.items()}
    return t


def function(lua, code, signature):
    start = code.index(signature)
    lua.execute(code[start:code.index("\nend", start) + 4])


def rig():
    lua = LuaRuntime()
    lua.globals().encode = lambda t: json.dumps(plain(t), sort_keys=True)
    lua.execute("""
        time=0; qid=0; frames={}; timers={}; callbacks={}; docs={}; adds=0; nativeSets=0
        function emit(e) for _,f in ipairs(frames) do if f.events[e] and f.handler then f.handler(f,e) end end end
        function drain() local t=timers;timers={};for _,fn in ipairs(t) do fn() end end
        function CreateFrame() local f={events={}};frames[#frames+1]=f
            function f:RegisterEvent(e) self.events[e]=true end
            function f:SetScript(_,fn) self.handler=fn end;return f end
        C_Timer={After=function(_,fn) timers[#timers+1]=fn end}
        GetTime=function() return time end
        TN=function(fn) return fn() end; TB=TN; SlashCmdList={}
        WowCompanionDB={}; WC={encode=encode,send=function(s) docs[#docs+1]=s end,push=function() end,
            collectPos=function() end,collectMap=function() end}
        CreateVector2D=function(x,y) return {x=x,y=y,GetXY=function() return x,y end} end
        UiMapPoint={CreateFromCoordinates=function(m,x,y) return {uiMapID=m,position=CreateVector2D(x,y)} end}
        C_Map={GetUserWaypoint=function() return pin end,HasUserWaypoint=function() return pin~=nil end,
            ClearUserWaypoint=function() pin=nil;onPin=false;emit('USER_WAYPOINT_UPDATED') end,
            SetUserWaypoint=function(p) nativeSets=nativeSets+1;pin=p;emit('USER_WAYPOINT_UPDATED') end,
            CanSetUserWaypointOnMap=function() return true end,
            GetWorldPosFromMapPos=function(m,p) return m,CreateVector2D(p.x*100,p.y*100) end}
        C_SuperTrack={GetSuperTrackedQuestID=function() return qid end,
            SetSuperTrackedQuestID=function(id) qid=id;onPin=false;emit('SUPER_TRACKING_CHANGED') end,
            IsSuperTrackingUserWaypoint=function() return onPin end,
            SetSuperTrackedUserWaypoint=function(on) onPin=on;qid=0;emit('SUPER_TRACKING_CHANGED') end}
        quests={}; inLog=function(id) return quests[id]~=nil end
        superTracked=function() return WC.focusNav and WC.focusNav.point and WC.focusNav.qid or (qid>0 and qid or nil) end
        modules={};QuestieLoader={CreateModule=function(_,name) modules[name]={};return modules[name] end,
            ImportModule=function(_,name) return modules[name] end}
        modules.TrackerData={RefreshQuest=function(id) return quests[id] end}
        modules.DistanceUtils={}; modules.ZoneDB={GetUiMapIdByAreaId=function(_,zone) return zone end}
        function quest(id)
            local a={Index=1,Needed=1,Collected=0,spawnList={{id,25}}}
            local b={Index=2,Needed=1,Collected=0,spawnList={{id+10,75}}}
            a.enrichment=a;b.enrichment=b
            local q={Id=id,Objectives={a,b},SpecialObjectives={},Finisher={NPC={1}},
                IsComplete=function(self) return self.done and 1 or 0 end}
            q.enrichment=q;quests[id]=q;return q
        end
        quest(42);quest(43)
        TomTom={L=setmetatable({},{__index=function(_,k) return k end}), hbd={GetLocalizedMap=function() return 'Test' end},
            private={AddSettingsSection=function() end,TrackDistance=function() end,UntrackDistance=function() end},
            profile={persistence={cleardistance=10},arrow={arrival=15},general={}},waypointprofile={}}
        function TomTom:FireMessage(e,uid)
            if e=='TOMTOM_WAYPOINT_REMOVED' and arrow==uid then arrow=nil
                if callbacks.ArrowTargetChanged then callbacks.ArrowTargetChanged('ArrowTargetChanged',nil) end end
        end
        function TomTom:SetCrazyArrow(uid) arrow=uid
            if callbacks.ArrowTargetChanged then callbacks.ArrowTargetChanged('ArrowTargetChanged',uid) end end
        function TomTom:IsCrazyArrowEmpty() return arrow==nil end
        TomTom.RegisterCallback=function(_,e,fn) callbacks[e]=fn end
        TomTom.private.FireCallback=function(e,uid) if e=='WaypointAdded' then adds=adds+1 end end
    """)
    lua.execute(source(TOMTOM, "TomTom/core/waypoints.lua"), "TomTom", lua.globals().TomTom)
    lua.execute(source(TOMTOM, "TomTom/core/suppress.lua"), "TomTom", lua.globals().TomTom)
    lua.execute(source(QUESTIE, "Questie/Modules/Tracker/TrackerMapEligibility.lua"))
    distance = source(QUESTIE, "Questie/Modules/Libs/DistanceUtils.lua")
    lua.execute("DistanceUtils=modules.DistanceUtils")
    function(lua, distance, "function DistanceUtils.GetNearestSpawnForQuest(")
    lua.execute("""
        DistanceUtils.GetNearestObjective=function(list) return list[1],99,'Objective',list[1][1] end
        DistanceUtils.GetNearestFinisherOrStarter=function() return {90,90},99,'Turn in' end
    """)
    code = (ADDONS / "WowCompanionQuests/Quests.lua").read_text()
    lua.execute(code[code.index("-- FOCUSED_WAYPOINT_BEGIN"):code.index("-- FOCUSED_WAYPOINT_END")])
    code = (ADDONS / "WowCompanion/Map.lua").read_text()
    lua.execute(code[code.index("-- DESTINATION_BEGIN"):code.index("-- DESTINATION_END")])
    lua.execute("emit('PLAYER_LOGIN');drain()")
    return lua


@unittest.skipUnless(TOMTOM.is_file() and QUESTIE.is_file(), "provide pinned official archive inputs")
class NavigationTest(unittest.TestCase):
    def test_official_pins_and_forever_gamepad_support(self):
        self.assertEqual(11790690, TOMTOM.stat().st_size)
        self.assertEqual("68a93f8dfa4ecd3cafe28b0999b47ee62e65f0096b5e5982e6725ba83c10bffc", hashlib.sha256(TOMTOM.read_bytes()).hexdigest())
        self.assertEqual("116eadd5833836d1b8ff30c9c8b5aaf724bc2de75eea362d6eee3d9962506b5f", hashlib.sha256(QUESTIE.read_bytes()).hexdigest())
        toc = source(TOMTOM, "TomTom/TomTom.toc")
        self.assertIn("16001", toc.splitlines()[0])
        self.assertIn("## Version: v4.4.1-release", toc)
        self.assertIn("## License: All Rights Reserved", toc)
        for name in ("worldmap_pins", "map_click"):
            self.assertIn("IsGamepadUI", source(TOMTOM, f"TomTom/modules/{name}.lua"))
        self.assertIn("WoWForever", source(TOMTOM, "TomTom/libs/HereBeDragons/HereBeDragons-2.0.lua"))

    def test_focus_progress_finisher_missing_and_unfocus(self):
        lua = rig()
        lua.execute("""
            N=WC.focusNav;N.choose(42);assert(arrow[2]==.42 and pin.uiMapID==99 and nativeSets==1)
            N.refresh();N.choose(42);assert(adds==1 and nativeSets==1, 'identical point is not requeued')
            N.choose(43);assert(arrow[2]==.43 and adds==2)
            quests[43].Objectives[1].Collected=1;emit('QUEST_LOG_UPDATE');emit('QUEST_LOG_UPDATE')
            assert(#timers==1);drain();assert(arrow[2]==.53 and adds==3)
            quests[43].done=true;emit('QUEST_WATCH_UPDATE');drain();assert(arrow.title=='Turn in' and arrow[2]==.9)
            quests[43]=nil;emit('QUEST_TURNED_IN');drain();assert(not arrow and not pin and not N.point)
            N.choose(42);N.choose(95);assert(not arrow and not pin)
            N.choose(42);N.choose(nil);assert(not arrow and not pin)
        """)

    def test_native_focus_and_manual_destinations_do_not_loop_or_disappear(self):
        lua = rig()
        lua.execute("""
            N=WC.focusNav;C_SuperTrack.SetSuperTrackedQuestID(42);assert(arrow[2]==.42)
            manual=TomTom:AddWaypoint(12,.1,.2,{title='Manual',crazy=true,persistent=false,silent=true})
            assert(N.paused and not N.point and arrow==manual and not pin)
            emit('QUEST_LOG_UPDATE');drain();assert(arrow==manual)
            N.choose(43);assert(arrow~=manual and TomTom:IsValidWaypoint(manual))
            C_Map.SetUserWaypoint(UiMapPoint.CreateFromCoordinates(12,.2,.3))
            assert(N.paused and pin.uiMapID==12 and not N.point)
            emit('QUEST_LOG_UPDATE');drain();assert(pin.uiMapID==12)
            N.choose(42);C_SuperTrack.SetSuperTrackedQuestID(0);assert(not N.point and not pin)
            assert(TomTom:IsFeatureSuppressed('poi'))
        """)

    def test_loading_logout_reload_and_opt_out(self):
        lua = rig()
        lua.execute("""
            N=WC.focusNav;N.choose(42);emit('LOADING_SCREEN_ENABLED');assert(not pin and not arrow)
            emit('QUEST_LOG_UPDATE');drain();assert(not arrow)
            emit('LOADING_SCREEN_DISABLED');drain();assert(arrow and N.qid==42)
            emit('PLAYER_LOGOUT');assert(not pin and not arrow and not WowCompanionDB.focusWaypoint)
            N.loading=false;N.choose(42);stale=WowCompanionDB.focusWaypoint
            emit('PLAYER_LOGIN');assert(not pin and not WowCompanionDB.focusWaypoint)
            drain();assert(arrow and N.qid==42)
            SlashCmdList.WCNAV('off');assert(not arrow and not pin and not N.enabled)
            emit('PLAYER_ENTERING_WORLD');drain();N.choose(43);assert(not arrow and WowCompanionDB.focusNavigation==false)
            assert(not TomTom:IsFeatureSuppressed('poi'))
        """)

    def test_old_saved_opt_out_migrates_once_without_rewriting_old_settings(self):
        lua = rig()
        lua.execute("""
            WowCompanionDB.focusNavigation=nil;ShortestPathForeverDB={dsForeverExtAutoQuest=1,focusedQuest=false,custom=123}
            emit('PLAYER_LOGIN');assert(not WC.focusNav.enabled)
            SlashCmdList.WCNAV('on');emit('PLAYER_LOGIN');assert(WC.focusNav.enabled)
            assert(ShortestPathForeverDB.focusedQuest==false and ShortestPathForeverDB.custom==123)
        """)

    def test_changed_lua_compiles_and_no_planner_hooks(self):
        lua = LuaRuntime(unpack_returned_tuples=True)
        compile_lua = lua.eval("function(s) local f,e=loadstring(s);return f~=nil,e end")
        for path in ("WowCompanionQuests/Quests.lua", "WowCompanion/Map.lua", "WowCompanionHub/Hub.lua"):
            code = (ADDONS / path).read_text()
            ok, error = compile_lua(code)
            self.assertTrue(ok, error)
        code = (ADDONS / "WowCompanionQuests/Quests.lua").read_text()
        bridge = code[code.index("-- FOCUSED_WAYPOINT_BEGIN"):code.index("-- FOCUSED_WAYPOINT_END")]
        for forbidden in ("OnUpdate", "NewTicker", "OpenWorldMap", "SetClosestWaypoint", "ShortestPathForever.API", "QuestieDB"):
            self.assertNotIn(forbidden, bridge)

    def test_manual_uid_collision_clamped_native_and_manual_spf_fail_closed(self):
        lua = rig()
        lua.execute("""
            N=WC.focusNav
            manual=TomTom:AddWaypoint(99,.42,.25,{title='Objective',from='Other',crazy=true,persistent=false,silent=true})
            N.choose(42);assert(not N.uid and N.paused and TomTom:IsValidWaypoint(manual))
            N.choose(nil);assert(TomTom:IsValidWaypoint(manual))
            C_Map.SetUserWaypoint=function(p) pin=UiMapPoint.CreateFromCoordinates(12,.5,.5) end
            N.choose(43);assert(N.point and arrow[2]==.43 and not pin, 'clamped native coordinates must not survive')
            ShortestPathForever={};N.refresh();assert(not N.point and not N.uid)
            assert(TomTom:IsValidWaypoint(manual))
        """)
        focus = source(QUESTIE, "Questie/Modules/Tracker/LinePool/QuestFocusButton.lua")
        self.assertIn("QuestieCompat.SetSuperTrackedQuestID(self.questId)", focus)

    def test_existing_heartbeat_recovers_delayed_questie_without_taking_manual_goal(self):
        lua = rig()
        lua.execute("""
            hooksecurefunc=function(_,_,fn) snapshotHook=fn end
            WC.snapshot=function() end;emitQuests=function() end;pinsBeat=function() end;areasBeat=function() end
            local original=inLog;inLog=function(id) return id==44 or id==45 or original(id) end
        """)
        code = (ADDONS / "WowCompanionQuests/Quests.lua").read_text()
        a = code.index("local RIDE = { heartbeat")
        lua.execute(code[a:code.index("-- ---------------------------------------------------------------- commands", a)])
        lua.execute("""
            N=WC.focusNav;N.choose(44);assert(not N.point)
            quest(44);snapshotHook('heartbeat');assert(N.point and N.qid==44)
            before=adds;snapshotHook('heartbeat');assert(adds==before)
            N.choose(45);manual=TomTom:AddWaypoint(12,.1,.2,{title='Manual',crazy=true,persistent=false,silent=true})
            quest(45);snapshotHook('heartbeat');assert(N.paused and arrow==manual and not N.point)
        """)


if __name__ == "__main__":
    unittest.main()
