"""Local Lua 5.1 router correctness. Adapted from upstream c59b0f3c/b1766039/37538de7.
Runs actual secure router clicks, not a copy of its decision logic.
--baseline loads this task's protected pre-change PadMap and must fail.
"""
import sys
from pathlib import Path
from wowstub import SRC, F, boot_world

if '--baseline' in sys.argv:
    root = Path(__file__).resolve().parents[1]
    SRC['padmap'] = (Path('/home/apc/infra/backups/ds-ext-ltrt-gather-20261126') / Path(F['padmap']).relative_to(root)).read_text()
SRC['padmap'] += '\n_G.__RTR = { R = R, OV = OV }\n'
PRE = r'''
C_GamePad.ButtonBindingToIndex = function(n)
    if n == "PADLTRIGGER" then return LT_I - 1 elseif n == "PADRTRIGGER" then return RT_I - 1 end
end
local pu = GamepadMainActionBarFrame.PageUnit
local function globalize(bar, prefix, base)
    bar.GetActionButtonByIndex = function(self, pos)
        local name = prefix .. pos
        local b = rawget(_G, name)
        if b then return b end
        b = setmetatable({ a = {}, refs = {}, hooks = {}, scripts = {}, wraps = {}, clicks = { "AnyDown", "AnyUp" }, _name = name,
                           _id = base + pos, _tpl = "" }, Frame)
        b.scripts.OnClick = function(_, _, down) ACTIONS[#ACTIONS + 1] = { kind = "bar", btn = name, down = down and true or false } end
        PROTECT(b); rawset(_G, name, b); return b
    end
    PROTECT(bar)
end
globalize(pu.TopCenteredAnchor.Bar, "GPTop", 0)
globalize(pu.LeftCenteredAnchor.Bar, "GPLeft", 12)
globalize(pu.RightCenteredAnchor.Bar, "GPRight", 24)
globalize(pu.BottomCenteredAnchor.Bar, "GPBottom", 36)
STANCE_AT, POSSESS_AT = "LeftCenteredAnchor", nil
local function overBar(prefix, base, where)
    local bar = { _shown = true }
    function bar:IsShown() return _G[where]() ~= nil end
    function bar:GetParent() local k = _G[where](); return k and pu[k] or nil end
    globalize(bar, prefix, base); return bar
end
STANCE_WHERE = function() return STANCE_AT end
POSSESS_WHERE = function() return POSSESS_AT end
pu.actionBars.stanceBar = overBar("GPStance", 72, "STANCE_WHERE")
pu.actionBars.possessBar = overBar("GPPossess", 120, "POSSESS_WHERE")
pu.overrideBars = { pu.actionBars.possessBar, pu.actionBars.stanceBar }
'''
L, errs = boot_world(pre=PRE)
assert not errs, errs
L.execute('__IN.ACT.trust(); advance(1); __IN.N.setMode(true); advance(1); NOW=NOW+2; tick(.1); advance(1)')
passes = fails = 0

def check(ok, name):
    global passes, fails
    print(('PASS ' if ok else 'FAIL ') + name)
    passes += bool(ok); fails += not ok

def press(lt=False, rt=False):
    L.execute('ACTIONS={}; PADSTATE={}')
    if lt: L.execute('PADSTATE[LT_I]=true')
    if rt: L.execute('PADSTATE[RT_I]=true')
    L.execute('doClick(WowCompanionCombatFace6,"LeftButton",true); doClick(WowCompanionCombatFace6,"LeftButton",false); PADSTATE={}; advance(.2)')
    return [dict(x).get('btn') for x in L.eval('ACTIONS').values() if x['kind'] == 'bar' and x['down']]

def event(e, arg=''):
    L.eval('function(e,a) fire(e,a); advance(1) end')(e, arg)

check(press(True) == ['GPStance6'], 'initial LT reaches stance')
check(press(rt=True) == ['GPRight6'], 'initial RT reaches page')
L.execute('STANCE_AT="RightCenteredAnchor"')
check(press(True) == ['GPStance6'], 'control: no event leaves old wiring')
event('GAMEPAD_STANCE_BAR_OVERRIDE_CHANGED')
check(press(True) == ['GPLeft6'] and press(rt=True) == ['GPStance6'], 'stance event refreshes both layers')
L.execute('STANCE_AT="LeftCenteredAnchor"')
event('CVAR_UPDATE', 'SoundVolume')
check(press(True) == ['GPLeft6'], 'unrelated CVar does not rewire')
for cv in ('gamepadStanceBarOverride', 'GamepadPossessBarOverride', 'GamepadUseCompactActionBar'):
    event('CVAR_UPDATE', cv)
    check(press(True) == ['GPStance6'], cv + ' refreshes layout')
L.execute('STANCE_AT="RightCenteredAnchor"')
L.execute('POSSESS_AT="BottomCenteredAnchor"')
event('GAMEPAD_POSSESS_BAR_OVERRIDE_CHANGED')
check(press(True, True) == ['GPPossess6'], 'possess event refreshes LT+RT')
L.execute('POSSESS_AT=nil'); event('GAMEPAD_POSSESS_BAR_OVERRIDE_CHANGED')
check(press(True, True) == ['GPBottom6'], 'possess removal restores page')
L.execute('fire("PLAYER_REGEN_DISABLED"); COMBAT=true; STANCE_AT="LeftCenteredAnchor"')
event('GAMEPAD_STANCE_BAR_OVERRIDE_CHANGED')
check(L.eval('__RTR.R.rewireFight') is True, 'combat marks deferred rewire')
check(press(True) == ['GPLeft6'], 'combat retains existing refs')
L.execute('COMBAT=false'); event('PLAYER_REGEN_ENABLED')
check(press(True) == ['GPStance6'], 'postcombat gets current stance')

# Real routerWire refreshes all routed buttons and the Party pad's own indices.
L.execute('RELINKS=0; REAL_LINK=__RTR.OV.pm.link; __RTR.OV.pm.link=function() RELINKS=RELINKS+1; return REAL_LINK() end')
for e in ('GAME_PAD_CONNECTED', 'GAME_PAD_DISCONNECTED', 'GAME_PAD_CONFIGS_CHANGED', 'GAME_PAD_ACTIVE_CHANGED'):
    L.execute('LT_I=LT_I+2; RT_I=RT_I+2')
    event(e)
    check(L.eval('WowCompanionCombatFace6:GetAttribute("wc-lt")==LT_I and WowCompanionCombatFace6:GetAttribute("wc-rt")==RT_I'), e+' reads trigger indices')
    check(press(True) == ['GPStance6'] and press(rt=True) == ['GPRight6'], e+' actual press uses current indices')
    check(L.eval('RELINKS') > 0, e+' relinks Party')
    check(L.eval('__RTR.OV.pm.pad():GetAttribute("wc-lt")==LT_I and __RTR.OV.pm.pad():GetAttribute("wc-rt")==RT_I'), e+' Party uses current indices')
    L.execute('RELINKS=0')
event('GAME_PAD_ACTIVE_CHANGED')
check(L.eval('RELINKS') == 0, 'same active indices do not rewire/relink')
L.execute('SAVED_WIRED=__RTR.R.trWired; __RTR.R.trWired=nil')
event('GAME_PAD_ACTIVE_CHANGED')
check(L.eval('RELINKS') == 0 and L.eval('__RTR.R.trWired') is None, 'before first wiring active switch has no effect')
L.execute('__RTR.R.trWired=SAVED_WIRED; COMBAT=true; LT_I=LT_I+2; RT_I=RT_I+2')
old = L.eval('WowCompanionCombatFace6:GetAttribute("wc-lt")')
event('GAME_PAD_CONNECTED')
check(L.eval('WowCompanionCombatFace6:GetAttribute("wc-lt")') == old and L.eval('RELINKS') == 0, 'controller combat change defers attributes and Party')
L.execute('COMBAT=false'); event('PLAYER_REGEN_ENABLED')
check(L.eval('WowCompanionCombatFace6:GetAttribute("wc-lt")==LT_I') and L.eval('RELINKS') > 0, 'controller postcombat rewires and relinks Party')
check(press(True) == ['GPStance6'], 'postcombat trigger index actually routes correct bar')
check(all(L.eval('#'+k) == 0 for k in ('VIOL','SPY','HANDLEERR','SNIPERR','ERRS')), 'no blocked, spied, snippet, handle or Lua errors')
print(f'{passes} passed, {fails} failed')
sys.exit(bool(fails))
