"""Focus commands plus the existing native/secure controller menu guards."""
import unittest
from lupa.lua51 import LuaRuntime
from test_navigation import ADDONS, rig, function


class FocusMenuTest(unittest.TestCase):
    def test_actual_app_focus_commands_clear_legacy_and_switch_goals(self):
        lua = rig()
        lua.execute("""
            OPS={};SN=function(fn) return fn() end;CALL=function(fn) fn();return true end
            ack=function() end;result=function() end
            WC.routeWp={active=true,clear=function() WC.routeWp.active=false end}
        """)
        code = (ADDONS / "WowCompanionQuests/Quests.lua").read_text()
        # The actual command functions share the same N and focusNavigation locals in the real file.
        lua.execute("N=WC.focusNav;focusNavigation=function(id) N.choose(id) end")
        for sig in ("function OPS.track(", "function OPS.untrack(", "function OPS.unfocus("):
            function(lua, code, sig)
        lua.execute("""
            OPS.track(42);assert(N.point and N.qid==42 and not WC.routeWp.active)
            OPS.track(43);assert(N.qid==43 and arrow[2]==.43)
            OPS.unfocus(42);assert(N.qid==43)
            OPS.unfocus(43);assert(not N.point and not arrow and not pin)
            OPS.track(42);OPS.untrack();assert(not N.point and not arrow and not pin)
        """)

    def test_native_menu_suspends_and_restores_existing_capture(self):
        code = (ADDONS / "WowCompanionInteract/Interact.lua").read_text()
        self.assertIn("function N.gameMenuSync()", code)
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
            WC={nativeMenu={visible=function() return shown end}}
        """)
        function(lua, code, "function N.gameMenuSync()")
        for signature, name in (("local function padPress(", "padPress"), ("local function direct(", "direct"), ("local function onStick(", "onStick")):
            a = code.index(signature); b = code.index("\nend", a) + 4
            lua.execute(code[a:b] + f"\n{name}_test={name}")
        lua.execute("""
            shown=true;N.gameMenuSync()
            assert(N.gmLock and hidden and buttons==false and sticks==false and next(bound)==nil)
            assert(G.holder=='pad:quests' and G.under.holder=='pad:map')
            assert(G.chord==nil and N.menuUps.PAD2 and N.menuStickWait)
            assert(N.hold10==nil and N.trd.lDir==nil)
            for _,key in ipairs({'PADDUP','PAD1','PAD2','PADLSHOULDER','PADRSHOULDER'}) do
                padPress_test(key,true,'frame');padPress_test(key,false,'bind')
                direct_test(true)(padGrab,key);direct_test(false)(padGrab,key)
            end
            assert(onStick_test(padGrab,'Right',1,1)==true)
            shown=false;N.gameMenuSync()
            assert(not N.gmLock and not hidden and buttons and bound.PAD1 and bound.PADLSHOULDER and bound.PADRSHOULDER)
            assert(G.holder=='pad:quests' and G.under.holder=='pad:map')
        """)

    def test_actual_secure_menu_snippets_clear_and_restore_router_in_lockdown(self):
        code = (ADDONS / "WowCompanionSpells/PadMap.lua").read_text()
        self.assertIn("R.MENU_SHOW", code)
        lua = LuaRuntime();lua.execute("OV={};R={}")
        function(lua, code, "function OV.fbSnip(")
        a = code.index("R.MENU_SHOW"); b = code.index("\nfunction R.menuHook", a)
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


if __name__ == "__main__":
    unittest.main()
