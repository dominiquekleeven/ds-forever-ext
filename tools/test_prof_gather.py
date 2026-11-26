"""Local Lua 5.1 gathering tests. Secure-row fixture from upstream 9170d42a/2bebb643.
No live game, NPC interaction or spell cast. Loads the actual Professions.lua.
"""
from pathlib import Path
from lupa import lua51

ROOT = Path(__file__).resolve().parents[1]
SRC = (ROOT / 'companion/app/src/main/assets/companion/addons/WowCompanionProfessions/Professions.lua').read_text()
L = lua51.LuaRuntime(unpack_returned_tuples=True)
L.execute(r'''
local stub
stub=setmetatable({}, {__index=function() return stub end,__call=function() return stub end})
setmetatable(_G,{__index=function() return stub end})
NOW=100; COMBAT=false; TRACE={}; REGISTERED={}; HANDLERS={}
function GetTime() return NOW end
function InCombatLockdown() return COMBAT end
function UnitAffectingCombat() return COMBAT end
function UnitName() return "Gatherer" end
function GetRealmName() return "Forever" end
C_Timer={NewTicker=function() return stub end,NewTimer=function() return stub end,After=function() end}
function CreateFrame(kind,name)
 local f=setmetatable({attrs={},name=name,ev={}},{__index=function() return stub end})
 function f:SetScript(k,fn) if k=="OnEvent" then HANDLERS[#HANDLERS+1]={fr=self,fn=fn} end end
 function f:RegisterEvent(e) self.ev[e]=true; REGISTERED[e]=true; return true end
 function f:SetAttribute(k,v) self.attrs[k]=v end
 function f:GetName() return self.name end
 if name then _G[name]=f end
 return f
end
function fire(e,...) for _,h in ipairs(HANDLERS) do if h.fr.ev[e] then h.fn(h.fr,e,...) end end end
for _,n in ipairs({"SetBinding","SaveBindings","DeleteMacro","CreateMacro","EditMacro","SetCVar","ShowUIPanel","HideUIPanel",
 "ToggleProfessionsBook","CloseTradeSkill","SetOverrideBinding","SetOverrideBindingClick","CastSpell","CastSpellByID","CastSpellByName"}) do
 _G[n]=function() TRACE[#TRACE+1]=n end
end
C_CVar={SetCVar=function() TRACE[#TRACE+1]="C_CVar.SetCVar" end}
PROFS={[1]={"Herbalism",136246,40,75,1,10,182,0},[2]={"Mining",136248,50,75,2,20,186,0}}
function GetProfessions() return 1,2 end
function GetProfessionInfo(i) return unpack(PROFS[i]) end
BOOK={[11]={2383,"Find Herbs",133939},[21]={2656,"Smelting",135811},[22]={2580,"Find Minerals",136025}}
C_SpellBook={GetSpellBookItemInfo=function(i) local b=BOOK[i]; return b and {spellID=b[1]} end,
 GetSpellBookItemName=function(i) local b=BOOK[i]; return b and b[2] end,
 GetSpellBookItemTexture=function(i) local b=BOOK[i]; return b and b[3] end}
C_TradeSkillUI=setmetatable({CanTradeSkillShowCraftingUI=function(sp) return sp==2656 end,IsTradeSkillReady=function() return false end},
 {__index=function() return function() return nil end end})
TRACKING={{"Find Herbs",false,2383},{"Find Minerals",true,2580}}; SETS={}
C_Minimap={GetNumTrackingTypes=function() return #TRACKING end,
 GetTrackingInfo=function(i) local t=TRACKING[i]; return t and {name=t[1],active=t[2],spellID=t[3]} end,
 SetTracking=function(i,on) SETS[#SETS+1]={i,on}; TRACKING[i][2]=on end}
SENT={}; ROWS={}; SLOTS={}; SECURE={}; local nslot={}
function build(name)
 local r=SECURE[name]; if not r or COMBAT then return false end
 r.cells={}
 for col=0,r.n-1 do local b=CreateFrame("Button"); r.init(b,col); r.cells[col]=b end
 return true
end
WowCompanion={S=function(fn) local ok,v=pcall(fn); if ok then return v end end,
 SN=function(fn) local ok,v=pcall(fn); if ok and type(v)=="number" then return v end end,
 CALL=function(fn,...) return pcall(fn,...) end,send=function(t) SENT[#SENT+1]=t end,encode=function(t) return t end,push=function() end,
 isPlain=function() return true end,ABSENT={},SECRET={},THREW={},
 pad={op=function(name,ns,h) ROWS[name]=h; return {} end,
 secureRow=function(name,n,init,onClick,opts) SECURE[name]={n=n,init=init,onClick=onClick,opts=opts}; build(name); return {} end,
 slotFor=function(ns,key) SLOTS[ns]=SLOTS[ns] or {}; if SLOTS[ns][key]==nil then nslot[ns]=(nslot[ns] or -1)+1; SLOTS[ns][key]=nslot[ns] end; return SLOTS[ns][key] end,
 describe=function() return {rows={prof=37,profopen=38}} end,onLayout=function() end,retain=function() end},
 capture={version=17,take=function() return true end,release=function() return true end,state=function() return {holder="professions"} end,
 bnext=function() end,refused=function() return true end,pass=function() return true end}}
function lastAck() for i=#SENT,1,-1 do if SENT[i].type=="profack" and SENT[i].op=="gather" then return SENT[i] end end end
function doc() return WowCompanion.collectProf() end
function card(d,id) for _,e in ipairs(d.list) do if e.id==id then return e end end end
function tap(col) SECURE.profopen.onClick(col) end
''')
L.eval('function(s) assert(loadstring(s,"@Professions.lua"))("WowCompanionProfessions") end')(SRC)
L.execute('fire("PLAYER_LOGIN"); D=doc(); D=doc()')
passes = 0

def check(expr, name):
    global passes
    assert L.eval(expr), name
    passes += 1; print('PASS', name)

check('card(D,182).btn[1].trk==0 and card(D,186).btn[2].trk==1', 'actual game off/on state')
check('card(D,186).btn[1].trk==nil', 'crafting button unchanged')
L.execute('H=card(D,182).btn[1]; M=card(D,186).btn[2]')
check('H.ton~=H.toff and H.ton~=M.ton and M.ton~=M.toff', 'separate explicit ON/OFF columns')
check('SECURE.profopen.cells[H.ton].attrs.type==nil and SECURE.profopen.cells[H.toff].attrs.type==nil', 'tracking has no secure spell action')
check('SECURE.profopen.cells[card(D,186).oslot].attrs.spell==2656', 'Smelting secure cast unchanged')
check('SECURE.profopen.opts.onPress==true', 'action-less row answers on press')
L.execute('tap(H.ton)')
check('#SETS==1 and SETS[1][1]==1 and SETS[1][2]==true', 'ON makes one explicit setter call')
check('lastAck().ok and lastAck().on and lastAck().name=="Find Herbs" and lastAck().id==2383', 'feedback names actual tracking')
L.execute('SETS={}; tap(H.ton)')
check('#SETS==0 and lastAck().ok', 'same ON cell again does not toggle')
L.execute('tap(H.toff)')
check('#SETS==1 and SETS[1][2]==false and card(doc(),182).btn[1].trk==0', 'same button OFF reads game state')
L.execute('SETS={}; COMBAT=true; tap(H.ton)')
check('#SETS==0 and not lastAck().ok and lastAck().why=="not in a fight"', 'combat guard, no game action')
L.execute('COMBAT=false; TRACKING[2]=nil; D=doc(); tap(M.ton)')
check('card(D,186).btn[2].trk==nil and #SETS==0 and lastAck().why=="not known"', 'unlearned/stale tracking refused')
L.execute('SAVED=C_Minimap.SetTracking; C_Minimap.SetTracking=nil; D=doc()')
check('card(D,182).btn[1].trk==nil and card(D,182).btn[1].ton==nil', 'unavailable setter greys button')
L.execute('tap(H.ton)')
check('#SETS==0 and not lastAck().ok and lastAck().why=="the game refused"', 'stale cell without setter gives refusal')
L.execute('C_Minimap.SetTracking=SAVED; TRACKING[1][3]=nil; D=doc()')
check('card(D,182).btn[1].ton==H.ton', 'name fallback retains same columns')
L.execute('OLDGET=C_Minimap.GetTrackingInfo; C_Minimap.GetTrackingInfo=function(i) local t=TRACKING[i]; if t then return t[1],1,t[2] end end; D=doc(); tap(H.ton)')
check('card(D,182).btn[1].trk==0 and #SETS==1', 'old tuple API read and switch')
L.execute('GetTrackingInfo=C_Minimap.GetTrackingInfo; SetTracking=SAVED; C_Minimap={}; SETS={}; tap(H.toff)')
check('#SETS==1 and SETS[1][2]==false', 'old global API fallback')
L.execute('C_Minimap={GetTrackingInfo=OLDGET,SetTracking=SAVED}; SetTracking=nil; GetTrackingInfo=nil; BOOK[12]={43308,"Find Fish",1}; PROFS[1][5]=2; TRACKING[2]={"Find Fish",false,43308}; COMBAT=true; D=doc()')
check('card(D,182).btn[2].trk==0 and card(D,182).btn[2].ton==nil', 'uncached secure cells withheld in combat')
L.execute('COMBAT=false; build("profopen"); D=doc()')
check('card(D,182).btn[2].ton~=nil', 'postcombat cells published')
check('REGISTERED.MINIMAP_UPDATE_TRACKING and SLOTS.profctl.ench==37 and SLOTS.profctl.gather==nil', 'tracking event and existing control columns')
check('#TRACE==0', 'no NPC, binding, macro, CVar, window or spell action')
print(f'{passes} passed, 0 failed')
