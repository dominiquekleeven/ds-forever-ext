"""Execute bundled Trail.lua hot paths in Lua 5.1, with the existing game stubs."""
from pathlib import Path
import sys
import unittest

from lupa.lua51 import LuaRuntime

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))
import test_compass as compass
import test_aggro as aggro
import test_tint as tint

TRAIL = Path(compass.TRAIL).read_text()


def runtime():
    lua = LuaRuntime()
    lua.execute(compass.STUB)
    lua.execute(aggro.WORLD)
    lua.execute(tint.PLATES)
    lua.execute(TRAIL, "WowCompanion")
    return lua


class AddonHotpaths(unittest.TestCase):
    def test_ring_wrap_drain_refill_and_stopped_samples(self):
        lua = runtime()
        lua.execute("""
            local wc, sample = WowCompanion, FRAMES[1].scripts.OnUpdate
            assert(wc.drainTrail() == nil)
            local calls = 0
            C_Map.GetPlayerMapPosition = function(map, unit)
                assert(map == GAME.map and unit == 'player')
                calls = calls + 1
                return CreateVector2D(GAME.px, GAME.py)
            end
            table.remove = function() error('front shifting is forbidden') end
            wc.trailMap(GAME.map)
            local function push(i, dt)
                GAME.px, GAME.py, GAME.facing = i / 10000, (i + 1) / 10000, i / 1000
                sample(FRAMES[1], dt)
                return string.format('%d,%d,%d,%d', math.floor(dt * 1000 + .5), i, i + 1, i)
            end
            for _, size in ipairs({237, 12, 91, 1, 180, 89}) do
                local expected = {}
                for i = 1, size do
                    local line = push(i, i % 2 == 0 and .04 or .05)
                    if i > size - 90 then expected[#expected + 1] = line end
                end
                assert(wc.drainTrail() == table.concat(expected, ';'), 'newest 90 in order, exact dt/position/facing')
                assert(wc.drainTrail() == nil, 'drain resets count/head')
            end
            for i = 1, 100 do push(i, .04) end
            local expected = {}
            for i = 1, 90 do expected[i] = push(100, .04) end
            assert(wc.drainTrail() == table.concat(expected, ';'), 'a full stopped buffer keeps all stop samples')
            local before = calls
            sample(FRAMES[1], .01); sample(FRAMES[1], .01)
            assert(calls == before, 'still sampled at 30 Hz, not every frame')
            sample(FRAMES[1], .02)
            assert(calls == before + 1 and wc.drainTrail() == '40,100,101,100', 'relative accumulated dt')
        """)

    def test_ring_map_unavailable_teleport_and_reset_behavior(self):
        lua = runtime()
        lua.execute("""
            local wc, sample = WowCompanion, FRAMES[1].scripts.OnUpdate
            C_Map.GetPlayerMapPosition = function(map)
                if GAME.missing then return nil end
                return { GetXY = function() return GAME.px, GAME.py end }
            end
            sample(FRAMES[1], .04)
            assert(wc.drainTrail() == nil, 'no samples before a map')
            wc.trailMap(1429); GAME.px, GAME.py, GAME.facing = .1, .2, nil
            sample(FRAMES[1], .04)
            wc.trailMap(1453); GAME.px, GAME.py, GAME.facing = .9, .8, 2
            sample(FRAMES[1], .07)
            wc.trailMap(nil); sample(FRAMES[1], .04)
            wc.trailMap(1453); GAME.missing = true; sample(FRAMES[1], .04)
            GAME.missing, GAME.px = false, nil; sample(FRAMES[1], .04)
            assert(wc.drainTrail() == '40,1000,2000,0;70,9000,8000,2000',
                   'map switch/teleport do not invent a reset or discard pending history')
            assert(wc.drainTrail() == nil)
            GAME.px = .3; sample(FRAMES[1], .05)
            assert(wc.drainTrail() == '50,3000,8000,2000', 'refill after skipped positions and drain')
        """)

    def test_tint_cleanup_once_reactivation_and_slot_reuse(self):
        lua = runtime()
        lua.execute("""
            local wc = WowCompanion
            local R, A = wc.rings, wc.live
            local driver = FRAMES[#FRAMES]
            local region = getmetatable(UIParent)
            local clear, hide = region.ClearAllPoints, region.Hide
            function region:ClearAllPoints() self.clears = (self.clears or 0) + 1; clear(self) end
            function region:Hide() self.hides = (self.hides or 0) + 1; hide(self) end
            function region:SetParent(p) self.parent = p end
            local m = {s=1, r=2, lv=6, a=10, b=20}
            A.last, A.slot, A.off = {m={m}}, {[1]={tok='nameplate1', at=1}}, false
            UNITS.nameplate1 = {}
            local function tick(n) for i=1,n or 1 do R.tick(.1) end end
            tick(4)
            local o = R.list[1]
            assert(o.f:IsShown() and o.a == R.ALPHA and o.plate and o.f.point)
            local function cleanOnce(change)
                local before = o.f.clears
                change(); tick()
                assert(not o.f:IsShown() and o.a == 0 and o.plate == nil and o.f.point == nil and R.est[1] == nil)
                assert(o.f.clears == before + 1)
                local hides = o.f.hides
                tick(20)
                assert(o.f.clears == before + 1 and o.f.hides == hides, 'no repeated inactive slot cleanup')
            end
            cleanOnce(function() R.set(false) end)
            local why = R.why
            -- Inactive ticks must not scan the retained pool or replace the diagnostic table.
            local originalPairs = pairs
            pairs = function(t) assert(t ~= R.list, 'inactive pool scanned'); return originalPairs(t) end
            tick(20); assert(R.why == why and R.n == 0)
            pairs = originalPairs
            R.set(true); tick(4)
            assert(o.f:IsShown() and o.f.point and R.est[1] and R.n == 1)
            -- Eligibility disappears while the master remains on.
            cleanOnce(function() m.dd = 1 end)
            assert(R.why[1] == 'dead')
            m.dd = nil; tick(4); assert(o.f:IsShown())
            cleanOnce(function() A.last.m = {} end)
            -- Same slot and token, new generation and plate: no old smoothing or anchor.
            UNITS.nameplate1 = {}; A.slot[1].at = 2; m.a, m.b = 40, 50; A.last.m = {m}
            tick(4)
            assert(o.f:IsShown() and o.plate == UNITS.nameplate1.plate and R.est[1].at == 2 and o.d == 45)
            m.cb = 1; tick(4); assert(not o.f:IsShown() and o.a == 0)
            m.cb = nil; tick(4); assert(o.f:IsShown())
            -- HUD target -> ineligible -> reused as a non-target plate.
            local hp = setmetatable({ww=166, hh=20, parent=UIParent}, region)
            local host = setmetatable({shown=true, hp=hp}, region)
            wc.uf = {target=true, tb={f=host}, flying=function() return false end}
            TARGET = 'nameplate1'; tick(4)
            assert(o.onBar and o.plate == hp and o.f.parent == UIParent)
            cleanOnce(function() m.td = 1 end)
            m.td, TARGET = nil, nil; tick(4)
            assert(not o.onBar and o.f.parent == WorldFrame and o.plate == UNITS.nameplate1.plate and o.f:IsShown())
            for _, key in ipairs({'off', 'last', 'records'}) do
                cleanOnce(function()
                    if key == 'off' then A.off = true elseif key == 'last' then A.last = nil else A.last.m = nil end
                end)
                local why = R.why; tick(20); assert(R.why == why)
                A.off, A.last = false, {m={m}}; tick(4); assert(o.f:IsShown())
            end
            -- Missing plate, changed token, dynamic plate alpha/scale, then a flying target.
            cleanOnce(function() UNITS.nameplate1 = nil end)
            A.slot[1] = {tok='nameplate2', at=3}; UNITS.nameplate2 = {pa=.4, ps=.8}
            tick(4); assert(not o.f:IsShown() and o.f.scale == .8)
            UNITS.nameplate2.pa, UNITS.nameplate2.ps = 1, 1
            tick(); assert(o.f:IsShown() and o.f.scale == 1 and R.est[1].tok == 'nameplate2')
            TARGET = 'nameplate2'; wc.uf.flying = function() return true end
            tick(4); assert(not o.f:IsShown())
            wc.uf.flying = function() return false end; tick(4)
            assert(o.onBar and o.f:IsShown() and o.plate == hp)
            local updater = driver.scripts.OnUpdate
            local originalTick = R.tick
            R.tick = function() error('simulated tick failure') end
            updater(driver, .1) -- OnUpdate still contains its error boundary.
            R.tick = originalTick
            R.set(false); tick(); R.set(true); tick(4); assert(o.f:IsShown())
        """)


if __name__ == "__main__":
    unittest.main()
