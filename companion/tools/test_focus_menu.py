"""Local regression slices from the real companion and pinned addon sources.

DS_EXT_BASELINE=108e143e runs the same expectations against the clean baseline.
No client, network, installation, or archive modification.
"""
import json
import os
from pathlib import Path
import subprocess
import unittest

from lupa.lua51 import LuaRuntime
from test_navigation import source, SPF, QUESTIE
from test_bottom_route import plain

ROOT = Path(__file__).resolve().parents[2]
ADDONS = "companion/app/src/main/assets/companion/addons/"


def text(path):
    baseline = os.environ.get("DS_EXT_BASELINE")
    if baseline:
        return subprocess.check_output(["git", "show", f"{baseline}:{path}"], cwd=ROOT, text=True)
    return (ROOT / path).read_text()


def function(lua, code, signature):
    start = code.index(signature)
    lua.execute(code[start:code.index("\nend", start) + 4])


class FocusMenuTest(unittest.TestCase):
    def focus_rig(self, legacy=False):
        lua = LuaRuntime()
        lua.globals().encode = lambda t: json.dumps(plain(t), separators=(",", ":"), sort_keys=True)
        lua.execute("""
            qid=10; plans=0; nativePlans=0; cancelled=0; time=0; documents={}; inits={}
            canaccessvalue=function() return true end
            C_AddOns={DoesAddOnExist=function() return false end}
            UnitGUID=function() return 'player' end
            CreateFrame=function() return {RegisterEvent=function() end,UnregisterEvent=function() end,SetScript=function() end} end
            hooksecurefunc=function(t,k,hook) local old=t[k]; t[k]=function(...) local v=old(...);hook(...);return v end end
            CreateVector2D=function(x,y) return {x=x,y=y,GetXY=function() return x,y end} end
            UnitPosition=function() return 0,0,0,0 end
            GetTime=function() return time end
            C_Map={GetMapInfo=function(id) return {mapID=id} end,
                GetWorldPosFromMapPos=function(id,p) return 0,CreateVector2D(p.x*100,p.y*100) end}
            Enum={}; L={}; C_QuestLog={GetLogIndexForQuestID=function(id) return id end}
            ShortestPathForeverDB={focusedQuest=true,journey=true,tomtom=true}
            TrackerUtils={}; Questie={db={char={}},TrackerUtils=TrackerUtils}
            QuestieLoader={ImportModule=function() return TrackerUtils end}
            TrackerMapEligibility={RefreshAndGetCapabilities=function(id) return {Id=id},{canNavigateQuest=id<90} end}
            DistanceUtils={GetNearestSpawnForQuest=function(q) return {q.Id,75},12,'Quest '..q.Id end}
            ZoneDB={GetUiMapIdByAreaId=function() return 99 end}
            ns={db=ShortestPathForeverDB, Init=function(fn) inits[#inits+1]=fn end,
                QuestID=function(id) return id end, JourneyChanged=function() end,PointGuideArrow=function() end,
                JourneyOwner=function() return owner end,
                JourneyToTrackedQuest=function() nativePlans=nativePlans+1 end}
            C_SuperTrack={GetSuperTrackedQuestID=function() return qid end,
                SetSuperTrackedQuestID=function(id) qid=id;ns.FocusedTrackingChanged('SUPER_TRACKING_CHANGED') end}
            API={NavigateRoute=function(who,stops) owner=who;stop=stops[1];plans=plans+1;return true end,
                CurrentStop=function(who) return owner==who and 1 or nil end,
                Cancel=function(who) if owner==who then owner=nil;stop=nil;cancelled=cancelled+1 end end,
                JourneyState=function() return {owner=owner,busy=stop~=nil} end,
                Journey=function() if not stop then return end
                    return {owner=owner,index=1,guided=true,legs={{mode='walk'}}} end,
                Guidance=function() if not stop then return end
                    return {owner=owner,leg=1,index=1,point=stop,waypoints={stop}} end}
            ShortestPathForever={API=API}
            ShortestPathForeverClearJourneyButton={Click=function() stop=nil;cancelled=cancelled+1 end}
            WC={encode=encode,send=function(s) documents[#documents+1]=s end,
                collectPos=function() end,collectMap=function() end,
                snapshotSoon=function() if WC.spfRoute then WC.spfRoute.tick() end end}
            OPS={}; SN=function(fn) return fn() end;CALL=function(fn) fn();return true end
            ack=function() end;result=function() end
            superTracked=function() return qid>0 and qid or nil end
            inLog=function(id) return id>0 end
        """)
        tracker = source(QUESTIE, "Questie/Modules/Tracker/TrackerUtils.lua")
        for sig in ("function TrackerUtils.IsTomTomAvailable()", "function TrackerUtils:SetTomTomTarget(",
                    "function TrackerUtils.SetQuestTomTomTarget("):
            function(lua, tracker, sig)
        lua.execute(source(SPF, "ShortestPathForever/Core/TomTom.lua"), "SPF", lua.globals().ns)
        focused = source(SPF, "ShortestPathForever/Journey/JourneyInput.lua")
        lua.execute(focused[focused.index("local focusedQuest"):focused.index("-- The journey key is this addon's gesture")])
        lua.execute("for _,fn in ipairs(inits) do fn() end;ns.FocusedTrackingReset()")
        quests = text(ADDONS + "WowCompanionQuests/Quests.lua")
        a = quests.index("local OPS = {}")
        b = quests.index("\nfunction OPS.untrack()", a)
        lua.execute(quests[a:b] + "\ntrack=OPS.track")
        map_code = text(ADDONS + "WowCompanion/Map.lua")
        lua.execute(map_code[map_code.index("-- SPF_BOTTOM_ROUTE_BEGIN"):map_code.index("-- SPF_BOTTOM_ROUTE_END")])
        if legacy:
            lua.execute("""
                pin=true;WowCompanionDB={routeWp={qid=10}}
                WC.routeWp={active=true,qid=10,x=.1,y=.2,say=function() end}
                RWP=WC.routeWp;pinHere=function() return pin end
                C_Map.ClearUserWaypoint=function() pin=false end
                C_SuperTrack.SetSuperTrackedUserWaypoint=function() end
                snapshotSoon=WC.snapshotSoon
            """)
            function(lua, map_code, "function RWP.clear(")
        return lua

    def test_companion_focus_replaces_actual_questie_shim_route_and_bottom_marker(self):
        for legacy in (False, True):
            with self.subTest(legacy=legacy):
                lua = self.focus_rig(legacy)
                lua.execute("""
                    track(42)
                    assert(stop and stop.questID==42, 'companion focus must reach Questie/SPF')
                    assert(plans==1 and nativePlans==0, 'one planner, not a game POI duplicate')
                    first=documents[#documents];track(43)
                    assert(stop.questID==43 and plans==2 and nativePlans==0)
                    assert(documents[#documents]~=first, 'bottom geometry and destination must change')
                    track(43); assert(plans==2, 'same focus must not plan twice')
                    track(95);assert(stop==nil, 'missing destination must clear the old focus route')
                """)
                doc = json.loads(lua.globals().documents[len(lua.globals().documents)])
                self.assertFalse(doc["on"])
                if legacy:
                    lua.execute("assert(not pin and not WC.routeWp.active, 'legacy pin must yield the real focus event')")

    def test_focus_respects_later_opt_out_and_other_guide(self):
        lua = self.focus_rig()
        lua.execute("""
            track(42);assert(plans==1)
            owner='Other guide';track(43);assert(plans==1 and owner=='Other guide')
            owner='TomTom';Questie.db.char._tom_waypoint={};track(44)
            assert(plans==1, 'another TomTom caller is not the companion')
            owner=nil;ShortestPathForeverDB.focusedQuest=false;track(45);assert(plans==1)
            ShortestPathForeverDB.focusedQuest=true;ShortestPathForeverDB.tomtom=false;track(46);assert(plans==1)
        """)

    def test_missing_destination_clears_preexisting_native_route_with_legacy_pin(self):
        lua = self.focus_rig(legacy=True)
        lua.execute("""
            owner=nil;stop={map=99,x=.1,y=.2,questID=10}
            track(95)
            assert(stop==nil and cancelled==1, 'failed focus must not keep a preexisting native route')
            assert(not pin and not WC.routeWp.active)
        """)

    def test_native_menu_suspends_and_restores_existing_capture(self):
        code = text(ADDONS + "WowCompanionInteract/Interact.lua")
        self.assertTrue("function N.gameMenuSync()" in code, "menu must be observed outside the pull-only branch")
        lua = LuaRuntime()
        lua.execute("""
            N={holding=true,hold10={},trd={lDir='up'},npcExtra={PADLSHOULDER=true},bar={bound=true},capCheck=function() end,selSync=function() end}
            G={holder='pad:quests',under={holder='pad:map'},chord={}}
            PAD_KEYS={'PADDUP','PAD1','PAD2'};extraKeys=function() return {'PADRSHOULDER'} end
            combat=false;inCombat=function() return combat end;TB=function(fn) return fn() end
            emitSoon=function() end; padOwner={};selOwner={}; lastDirect={}
            bound={};padBind=function(k) bound[k]=true end
            ClearOverrideBindings=function() bound={} end
            padGrab={EnableGamePadButton=function(_,v) buttons=v end,EnableGamePadStick=function(_,v) sticks=v end,
                SetPropagateKeyboardInput=function() end,Hide=function() hidden=true end,Show=function() hidden=false end}
            GameMenuFrame={IsShown=function() return shown end}
        """)
        function(lua, code, "function N.gameMenuSync()")
        for signature, name in (("local function padPress(", "padPress"),
                                ("local function direct(", "direct"),
                                ("local function onStick(", "onStick")):
            a = code.index(signature)
            b = code.index("\nend", a) + 4
            lua.execute(code[a:b] + f"\n{name}_test={name}")
        lua.execute("""
            shown=true;N.gameMenuSync()
            assert(N.gmLock and hidden and buttons==false and sticks==false and next(bound)==nil)
            assert(G.holder=='pad:quests' and G.under.holder=='pad:map', 'do not lose the modal stack')
            assert(G.chord==nil and N.menuUps.PAD2 and N.menuStickWait, 'no stale held/chord input')
            assert(N.hold10==nil and N.trd.lDir==nil, 'quantity/trade repeat timers must stop too')
            for _,key in ipairs({'PADDUP','PAD1','PAD2','PADLSHOULDER','PADRSHOULDER'}) do
                padPress_test(key,true,'frame');padPress_test(key,false,'bind')
                direct_test(true)(padGrab,key);direct_test(false)(padGrab,key)
            end
            assert(onStick_test(padGrab,'Right',1,1)==true, 'native stick must propagate')
            shown=false;N.gameMenuSync()
            assert(not N.gmLock and not hidden and buttons and bound.PAD1 and bound.PADLSHOULDER and bound.PADRSHOULDER)
            assert(G.holder=='pad:quests' and G.under.holder=='pad:map')
        """)

    def test_actual_secure_menu_snippets_clear_and_restore_router_in_lockdown(self):
        code = text(ADDONS + "WowCompanionSpells/PadMap.lua")
        self.assertTrue("R.MENU_SHOW" in code, "secure router must yield in lockdown")
        lua = LuaRuntime()
        lua.execute("OV={};R={}")
        function(lua, code, "function OV.fbSnip(")
        a = code.index("R.MENU_SHOW")
        b = code.index("\nfunction R.menuHook", a)
        lua.execute(code[a:b] + "\nshow=R.MENU_SHOW;hide=R.MENU_HIDE")
        lua.execute("""
            bindings={PAD1='Room',PADDUP='Home',PADLSHOULDER='Spell'}
            attrs={['wc-menu-n']=2,['wc-menu-k-1']='PAD1',['wc-menu-b-1']='Room',
                ['wc-menu-k-2']='PADDUP',['wc-menu-a-2']='NATIVE', ['wc-fb-n']=0}
            control={GetAttribute=function(_,k) return attrs[k] end,SetAttribute=function(_,k,v) attrs[k]=v end,
                GetFrameRef=function() return nil end,ClearBindings=function() bindings={} end,
                SetBinding=function(_,_,k,v) bindings[k]=v end,SetBindingClick=function(_,_,k,v) bindings[k]=v end}
            self={}
        """)
        lua.execute(lua.globals().show)
        lua.execute("assert(attrs['wc-menu'] and next(bindings)==nil)")
        lua.execute(lua.globals().hide)
        lua.execute("assert(not attrs['wc-menu'] and bindings.PAD1=='Room' and bindings.PADDUP=='NATIVE')")

    def test_changed_addons_compile_as_lua51(self):
        lua = LuaRuntime(unpack_returned_tuples=True)
        compile_lua = lua.eval("function(s) local f,e=loadstring(s);return f~=nil,e end")
        for path in ("WowCompanionQuests/Quests.lua", "WowCompanionInteract/Interact.lua",
                     "WowCompanionSpells/PadMap.lua", "WowCompanionSpells/Radial.lua"):
            ok, error = compile_lua(text(ADDONS + path))
            self.assertTrue(ok, error)


if __name__ == "__main__":
    unittest.main()
