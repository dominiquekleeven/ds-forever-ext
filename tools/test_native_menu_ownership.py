"""Local-only Lua 5.1 OUT-OF-COMBAT native submenu ownership.

Run: python -B tools/test_native_menu_ownership.py [--baseline]
--baseline uses the protected pre-change Lua copies and must reproduce failures.
--boundaries retains eight diagnostic checks OUTSIDE the approved scope. They
are expected to fail: arbitrary/lazy in-combat submenu ownership is not added.
Do not include that diagnostic mode as a passing out-of-combat CI check.
No installed client is inspected. Public Forever UI sources confirm SettingsPanel,
AddonList, HelpFrame, hide-main-before-open-child and Settings returning to the menu:
https://github.com/Ketho/wow-ui-source-forever/tree/forever/Interface/AddOns
Blizzard_RestrictedAddOnEnvironment/SecureHandlers.lua confirms that a pre-hook's
SECOND return value must be non-nil for a post-hook to run. This harness models
that contract. Its native frame fixtures assume combat-eligible protected frames;
it does NOT establish actual native-frame protection on a running client.
"""
import sys
from pathlib import Path

from wowstub import F, SRC, boot_world, docs

if '--baseline' in sys.argv:
    backup = Path('/home/apc/infra/backups/ds-ext-native-submenu-input-20261126')
    root = Path(__file__).resolve().parents[1]
    for name in ('interact', 'padmap'):
        SRC[name] = (backup / Path(F[name]).relative_to(root)).read_text()

# wowstub's general harness only models secure OnClick pre-hooks. Add actual
# OnShow/OnHide and post-hook execution here, without weakening STRICT checks.
PRE = r'''
mkwin("GameMenuFrame", false)
mkwin("SettingsPanel", false)
mkwin("AddonList", false)
mkwin("HelpFrame", false)
mkwin("StaticPopup1", false)
mkwin("StaticPopup2", false)
mkwin("WorldMapFrame", false)
function Frame:HasScript(s) return self._kind == "Button" and s == "OnClick" end
function Frame:GetChildren() return unpack(self._children or {}) end
NATIVE_BUTTON = CreateFrame("Button", "NativeOptionsTest", GameMenuFrame)
NATIVE_BUTTON:RegisterForClicks("AnyUp")
GameMenuFrame._children = { NATIVE_BUTTON }
CHILD_BUTTON = CreateFrame("Button", "NativeChildCloseTest", SettingsPanel)
CHILD_BUTTON:RegisterForClicks("AnyUp")
SettingsPanel._children = { CHILD_BUTTON }
function SecureHandlerWrapScript(f, script, header, pre, post)
    blocked("WrapScript")
    f.wraps[script] = f.wraps[script] or {}
    table.insert(f.wraps[script], {
        pre = compileSnippet(pre, "self, control, button, down"),
        post = post and compileSnippet(post, "self, control, message, button, down"), header = header,
    })
end
function wraps(f, s, post, button, down)
    for _, w in ipairs(f.wraps[s] or {}) do
        if not post then
            local was = IN_SECURE
            IN_SECURE = true
            local ok, _, message = pcall(w.pre, f, w.header, button, down)
            IN_SECURE = was
            w.message = message
            if not ok then SNIPERR[#SNIPERR + 1] = _ end
        elseif w.post and w.message ~= nil then
            runSecure(w.post, f, w.header, w.message, button, down)
            w.message = nil
        end
    end
end
local show, hide, click = Frame.Show, Frame.Hide, doClick
function Frame:Show()
    local edge = self._shown == false
    if edge then wraps(self, "OnShow", false) end
    show(self)
    if edge then wraps(self, "OnShow", true) end
end
function Frame:Hide()
    local edge = self._shown ~= false
    if edge then wraps(self, "OnHide", false) end
    hide(self)
    if edge then wraps(self, "OnHide", true) end
end
function doClick(f, b, down)
    if f ~= NATIVE_BUTTON and f ~= CHILD_BUTTON then return click(f, b, down) end
    wraps(f, "OnClick", false, b, down)
    local w = f.wraps.OnClick
    f.wraps.OnClick = {}
    click(f, b, down)
    f.wraps.OnClick = w
    wraps(f, "OnClick", true, b, down)
end
RESTORES = 0
function Frame:SetBindingClick(...)
    if self._name == "WowCompanionFaceRouter" then RESTORES = RESTORES + 1 end
    return SetOverrideBindingClick(self, ...)
end
function Frame:SetBinding(...) return SetOverrideBinding(self, ...) end
function Frame:ClearBindings() return ClearOverrideBindings(self) end
'''

fails = passes = 0


def check(ok, name, detail=''):
    global fails, passes
    print(('PASS ' if ok else 'FAIL ') + name + (f': {detail}' if not ok else ''))
    passes += bool(ok)
    fails += not ok


def world(extra=''):
    lua, errors = boot_world(pre=PRE + '\nWowCompanion.partyOk = true\n' + extra)
    check(not errors, 'all real Lua modules loaded', errors)
    lua.execute('__IN.ACT.trust(); advance(1); __IN.N.setMode(true); advance(1)')
    return lua


def clean(lua, label):
    errors = {k: list(lua.eval(k).values()) for k in
              ('VIOL', 'SPY', 'HANDLEERR', 'SNIPERR', 'ERRS') if len(lua.eval(k))}
    check(not errors, label + ': no protected-call, handle, snippet or Lua errors', errors)


if '--boundaries' in sys.argv:
    print('EXCLUDED DIAGNOSTIC: arbitrary combat submenu ownership is not part of the approved fix; failures are expected.')
    w = world()
    w.execute('fire("PLAYER_REGEN_DISABLED"); COMBAT = true; advance(0.3); '
              'GameMenuFrame:Show(); advance(0.1); '
              'mkwin("VideoOptionsFrame", false); fire("ADDON_LOADED", "Blizzard_VideoOptions"); '
              'NATIVE_BUTTON:SetScript("OnClick", function() GameMenuFrame:Hide(); VideoOptionsFrame:Show() end); '
              'doClick(NATIVE_BUTTON, "LeftButton", false); advance(0.3)')
    check(w.eval('__IN.N.gmLock') is True, 'combat-lazy capture retains native ownership')
    check(w.eval('WowCompanionFaceRouter:GetAttribute("wc-menu")') is True,
          'combat-lazy secure router retains native ownership')
    check(w.eval('GetBindingAction("PADDDOWN")') == '',
          'combat-lazy native D-pad is not reclaimed by the bottom router',
          w.eval('GetBindingAction("PADDDOWN")'))
    clean(w, 'combat-lazy')

    w = world()
    w.execute('fire("PLAYER_REGEN_DISABLED"); COMBAT = true; advance(0.3); '
              'GameMenuFrame:Show(); advance(0.1); BEFORE = RESTORES; '
              'GameMenuFrame:Hide(); SettingsPanel:Show()')
    check(w.eval('RESTORES == BEFORE'), 'unbracketed combat handoff never restores bindings between roots')
    clean(w, 'unbracketed combat handoff')
    print(f'{passes} passed, {fails} failed')
    sys.exit(1 if fails else 0)


for child in ('SettingsPanel', 'AddonList', 'HelpFrame'):
    for handoff in ('button', 'unbracketed'):
        w = world()
        w.execute('GameMenuFrame:Show(); advance(0.1)')
        w.execute('NATIVE_BUTTON:SetScript("OnClick", function() GameMenuFrame:Hide(); ' +
                  child + ':Show() end)')
        if handoff == 'button':
            w.execute('BEFORE = RESTORES; doClick(NATIVE_BUTTON, "LeftButton", false)')
        else:
            w.execute('BEFORE = RESTORES; GameMenuFrame:Hide(); ' + child + ':Show()')
        label = f'{child}, outside combat, {handoff}'
        check(w.eval('__IN.N.gmLock') is True, label + ': hide-main/show-child retains capture immediately')
        check(w.eval('RESTORES == BEFORE'), label + ': secure native action never restores between roots')
        w.execute('advance(0.4)')
        check(w.eval('WowCompanion.capture.state().gmLock') is True, label + ': capture publishes aggregate lock')
        check(w.eval('WowCompanionFaceRouter:GetAttribute("wc-menu")') is True,
              label + ': secure router remains yielded')
        check(w.eval('GetBindingAction("PADDDOWN")') == '', label + ': router polling does not reacquire native pad')
        d = [d for d in docs(w, 0, 'interact') if d.get('gmLock')]
        check(bool(d), label + ': actual interact document carries gmLock')
        if child == 'SettingsPanel':
            w.execute('CHILD_BUTTON:SetScript("OnClick", function() SettingsPanel:Hide(); GameMenuFrame:Show() end); '
                      'BEFORE = RESTORES; doClick(CHILD_BUTTON, "LeftButton", false)')
            check(w.eval('RESTORES == BEFORE'), label + ': native child close never restores before main reopens')
            w.execute('advance(0.2)')
        else:
            w.execute(child + ':Hide(); GameMenuFrame:Show(); advance(0.2)')
        check(w.eval('__IN.N.gmLock') is True, label + ': child-to-main retains ownership')
        w.execute('GameMenuFrame:Hide(); advance(0.3)')
        check(not w.eval('WowCompanion.capture.state().gmLock'), label + ': final close releases aggregate')
        check(not w.eval('WowCompanionFaceRouter:GetAttribute("wc-menu")'), label + ': final secure close restores')
        clean(w, label)

w = world()
w.execute('SettingsPanel:Show(); AddonList:Show(); StaticPopup1:Show(); StaticPopup2:Show(); advance(0.2)')
check(w.eval('__IN.N.gmLock') is True, 'direct settings/addons openings are native-owned')
w.execute('SettingsPanel:Hide(); AddonList:Hide(); StaticPopup1:Hide(); advance(0.2)')
check(w.eval('__IN.N.gmLock') is True, 'nested native confirmation retains lock after roots close')
check(w.eval('WowCompanionFaceRouter:GetAttribute("wc-menu")') is True, 'secure aggregate retains nested confirmation')
w.execute('StaticPopup2:Hide(); advance(0.2)')
check(not w.eval('__IN.N.gmLock'), 'last nested confirmation releases lock')
clean(w, 'multiple roots and nested confirmations')

w = world('HelpFrame = nil')
w.execute('mkwin("HelpFrame", false); fire("ADDON_LOADED", "Blizzard_HelpFrame"); HelpFrame:Show(); advance(0.2)')
check(w.eval('__IN.N.gmLock') is True, 'lazy root discovered on ADDON_LOADED')
check(w.eval('WowCompanionFaceRouter:GetAttribute("wc-menu")') is True, 'lazy root yields router outside combat without child secure hooks')
w.execute('HelpFrame:Hide(); advance(0.2)')
check(not w.eval('__IN.N.gmLock'), 'lazy root close releases')
clean(w, 'lazy loading')

w = world()
w.execute('WorldMapFrame:Show(); StaticPopup1:Show(); advance(0.2)')
check(not w.eval('__IN.N.gmLock'), 'mirrored map and unrelated popup do not claim native menu ownership')
w.execute('StaticPopup1:Hide(); WorldMapFrame:Hide(); advance(0.2); hwTap("qact", 10); advance(0.4)')
# The existing map room's capture remains usable; native roots must not replace it.
check(not w.eval('__IN.N.gmLock'), 'bottom room still has no native lock')
check(w.eval('WowCompanion.capture.state().holder') == 'pad:map', 'mirrored bottom map owns its normal capture')
w.execute('SettingsPanel:Show(); advance(0.2)')
check(w.eval('WowCompanion.capture.state().holder') == 'pad:map', 'native submenu suspends but does not replace bottom room')
before = len(docs(w, 0, 'guestpad'))
w.execute('hwPress("PADDDOWN", false, false); advance(0.2)')
check(len(docs(w, 0, 'guestpad')) == before, 'native submenu pad never reaches the suspended bottom room')
w.execute('SettingsPanel:Hide(); advance(0.2)')
check(w.eval('WowCompanion.capture.state().holder') == 'pad:map', 'same bottom room resumes after final native close')
w.execute('hwPress("PADDDOWN", false, false); advance(0.2)')
check(len(docs(w, 0, 'guestpad')) > before, 'fresh pad press reaches resumed bottom room')
clean(w, 'mirrored rooms')

w = world()
w.execute('SettingsPanel:Show(); advance(0.2); '
          'WowCompanion.capture.take("queued-native-test", { press = function() end }); advance(0.3)')
check(w.eval('WowCompanion.capture.state().holder') != 'queued-native-test', 'new capture guest stays queued in native submenu')
w.execute('SettingsPanel:Hide(); advance(1)')
check(w.eval('WowCompanion.capture.state().holder') == 'queued-native-test', 'queued guest granted after final native close')
clean(w, 'queued capture')

# Combat is a scope boundary, not a new submenu guarantee. Keep the baseline
# GameMenuFrame guard, drop only the new submenu suspension before lockdown,
# and re-evaluate still-visible native roots after combat ends.
for child in ('SettingsPanel', 'AddonList', 'HelpFrame'):
    w = world()
    w.execute(child + ':Show(); advance(0.2)')
    check(w.eval('__IN.N.gmLock') is True, child + ': outside-combat lock armed')
    w.execute('fire("PLAYER_REGEN_DISABLED"); COMBAT = true; advance(0.3)')
    check(not w.eval('WowCompanion.capture.state().gmLock'), child + ': combat start returns to baseline main-menu-only ownership')
    check(not w.eval('WowCompanionFaceRouter:GetAttribute("wc-menu")'), child + ': no submenu secure lock carried into combat')
    check(not (docs(w, 0, 'interact')[-1].get('gmFight')), child + ': submenu is not a new gmFight menu-at-pull')
    check(w.eval('#(' + child + '.wraps.OnShow or {})') == 0 and
          w.eval('#(' + child + '.wraps.OnHide or {})') == 0,
          child + ': no secure hooks installed on native child frames')
    w.execute(child + ':Hide(); ' + child + ':Show(); advance(0.2)')
    check(not w.eval('WowCompanion.capture.state().gmLock'), child + ': combat-only show/hide adds no new submenu lock')
    w.execute('COMBAT = false; fire("PLAYER_REGEN_ENABLED"); advance(0.3)')
    check(w.eval('WowCompanion.capture.state().gmLock') is True, child + ': still-visible submenu is locked after combat')
    w.execute(child + ':Hide(); advance(0.2)')
    check(not w.eval('WowCompanion.capture.state().gmLock'), child + ': final outside-combat close restores ownership')
    clean(w, child + ' combat scope boundary')

w = world()
w.execute('fire("PLAYER_REGEN_DISABLED"); COMBAT = true; advance(0.3); GameMenuFrame:Show(); advance(0.2)')
check(w.eval('WowCompanion.capture.state().gmLock') is True, 'baseline main GameMenuFrame still locks during combat')
check(w.eval('WowCompanionFaceRouter:GetAttribute("wc-menu")') is True, 'baseline main-menu secure show guard retained')
w.execute('GameMenuFrame:Hide()')
check(not w.eval('WowCompanion.capture.state().gmLock'), 'baseline combat main-menu hide is immediate, not deferred')
check(not w.eval('WowCompanionFaceRouter:GetAttribute("wc-menu")'), 'baseline main-menu secure hide guard retained')
clean(w, 'baseline main menu in combat')

# Execute the existing Lua neutral gates, not a duplicate of their logic.
SRC['interact'] += '\n_G.__nativePadPress = padPress; _G.__nativeStick = onStick\n'
w = world()
w.execute('RELEASES = 0; WowCompanion.capture.take("neutral-test", { both = { "PAD2" }, '
          'press = function(k, down) if not down then RELEASES = RELEASES + 1 end end }); '
          'SettingsPanel:Show(); advance(0.2); SettingsPanel:Hide(); advance(0.2); '
          '__nativePadPress("PAD2", false, "binding")')
check(w.eval('RELEASES') == 0, 'closing held button release does not activate resumed guest')
w.execute('__nativePadPress("PAD2", true, "binding"); __nativePadPress("PAD2", false, "binding")')
check(w.eval('RELEASES') == 1, 'fresh button press/release reaches the same resumed guest')
w.execute('__nativeStick(nil, "Right", 1, 0)')
check(w.eval('__IN.N.menuStickWait') is True, 'held stick stays gated after native submenu closes')
w.execute('__nativeStick(nil, "Right", 0, 0)')
check(not w.eval('__IN.N.menuStickWait'), 'stick rearms only after its neutral report')
clean(w, 'actual Lua held button and stick neutral gates')

print(f'{passes} passed, {fails} failed')
sys.exit(1 if fails else 0)
