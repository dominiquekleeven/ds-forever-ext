"""Real exporter + pinned QuestieDB API/support regression; no archive extraction or network.

QUESTIE_BUNDLE_ZIP points at the official Questie 12.12.0 release zip in CI.
Only the native game APIs/backend transport are emulated, not QuestieDB's getters.
"""
import ast
import base64
import hashlib
import json
import os
from pathlib import Path
import struct
import unittest
import zipfile

from lupa import lua51

ROOT = Path(__file__).resolve().parents[2]
ADDONS = ROOT / "companion/app/src/main/assets/companion/addons"
ARCHIVE = Path(os.environ.get("QUESTIE_BUNDLE_ZIP", "/tmp/opencode/questie-12.12.0.zip"))


def cbor(data):
    """Decode the bounded entity fixtures' native CBOR rows (no compressed ID scans)."""
    pos = 0
    def take(n):
        nonlocal pos
        b = data[pos:pos+n]; pos += n
        assert len(b) == n
        return b
    def read():
        head = take(1)[0]; major, extra = head >> 5, head & 31
        if major == 7:
            if extra in (20, 21, 22): return {20: False, 21: True, 22: None}[extra]
            if extra == 27: return struct.unpack(">d", take(8))[0]
            if extra == 26: return struct.unpack(">f", take(4))[0]
        n = extra if extra < 24 else int.from_bytes(take({24: 1, 25: 2, 26: 4, 27: 8}[extra]), "big")
        if major == 0: return n
        if major == 1: return -1-n
        if major == 2: return take(n)
        if major == 3: return take(n).decode()
        if major == 4: return [read() for _ in range(n)]
        if major == 5: return {read(): read() for _ in range(n)}
        raise AssertionError((major, extra))
    result = read()
    assert pos == len(data)
    return result


def table(lua, v):
    if isinstance(v, dict): return lua.table_from({k: table(lua, x) for k, x in v.items()})
    if isinstance(v, list): return lua.table_from([table(lua, x) for x in v])
    return v


def game():
    # Reuse the existing native-API harness, without executing its independent tests.
    path = ROOT / "tools/test_quest_rewards.py"
    tree = ast.parse(path.read_text())
    stub = next(ast.literal_eval(n.value) for n in tree.body if isinstance(n, ast.Assign)
                and any(isinstance(t, ast.Name) and t.id == "STUB" for t in n.targets))
    lua = lua51.LuaRuntime(unpack_returned_tuples=True)
    lua.execute(stub)
    lua.execute('''WowCompanion.snapshot = function() end
        function hooksecurefunc(t, key, fn)
            local old = t[key]; t[key] = function(...) old(...); fn(...) end
        end
        function UnitRace() return "Human", "Human", 1 end
        function UnitClass() return "Warrior", "WARRIOR", 1 end
        function UnitFactionGroup() return "Alliance" end
        function UnitLevel() return 3 end
        CURRENT_MAP = 1429
        C_Map.GetBestMapForUnit = function() return CURRENT_MAP end
        C_Map.GetMapWorldSize = function() return 3500, 2300 end
        C_Map.GetPlayerMapPosition = function() return {GetXY=function() return .48, .42 end} end
        C_Map.GetMapInfo = function() return {name="Elwynn Forest"} end
        C_QuestLog.IsQuestFlaggedCompleted = function(id) return id == 783 end
        C_QuestLog.IsComplete = function(id) return id == 15 end
        C_QuestLog.ReadyForTurnIn = C_QuestLog.IsComplete
        C_SuperTrack.GetSuperTrackedQuestID = function() return 33 end
        C_QuestLog.GetQuestObjectives = function(id)
            if id == 33 then return {{text="Tough Wolf Meat: 0/8", type="item", finished=false, numFulfilled=0, numRequired=8}} end
            return {}
        end
        LOG = {{id=33,title="Wolves Across the Border"}, {id=15,title="Investigate Echo Ridge"}}
    ''')
    for p in [ADDONS / "WowCompanion/Safety.lua", ADDONS / "WowCompanion/TapPad.lua", ADDONS / "WowCompanionQuests/Quests.lua"]:
        lua.execute(p.read_text())
    return lua


def publish(lua):
    assert hashlib.sha256(ARCHIVE.read_bytes()).hexdigest() == "116eadd5833836d1b8ff30c9c8b5aaf724bc2de75eea362d6eee3d9962506b5f"
    z = zipfile.ZipFile(ARCHIVE)
    metadata = dict(line[3:].split(": ", 1) for line in z.read("QuestieDB/QuestieDB_Forever.toc").decode().splitlines()
                    if line.startswith("## ") and ": " in line)
    lib = lua.table()
    load = lua.eval('function(s,t) return assert(loadstring(s))("QuestieDB",t) end')
    def module(n): load(z.read("QuestieDB/" + n).decode(), lib)
    for n in ["src/config.lua", "src/meta/normalize.lua", "src/meta/questMeta.lua", "src/meta/npcMeta.lua",
              "src/meta/itemMeta.lua", "src/meta/objectMeta.lua", "src/read/shared.lua", "src/corrections/enum/constants.lua",
              "src/corrections/enum/expansions.lua", "src/support/data.lua"]:
        module(n)
    lua.globals().provider = lib
    lua.execute('provider.Support.Install(provider.config.flavorByName.Forever)')
    module("support/Forever/Zones/areaIdToUiMapId.lua")
    lua.execute('provider.Support.Remove()')
    # Native backend transport: exact rows from the pinned TOC, with bounded ID lists.
    ids = {"Quest": [7, 15, 33, 3100], "Npc": [197, 196, 6, 257, 80, 299, 69, 525], "Object": [], "Item": [750]}
    def row(kind, id, field):
        key = f"X-{kind}-{int(id)}-{field}"
        value = metadata.get(key)
        if value is None: return None
        if value.startswith("~"):
            value = "".join(metadata[key + "-" + str(i)] for i in range(1, int(value[1:])+1))
        return table(lua, cbor(base64.b64decode(value)))
    lua.globals().entityIds = table(lua, ids)
    lua.globals().nativeRow = row
    lua.execute('''provider.flavor = provider.config.flavorByName.Forever
        provider.Enum.byExpansion.Forever = {raceKeys={ALL_ALLIANCE=77, ALL_HORDE=178}}
        provider.Enum.byExpansion.Classic = {classKeys={}, npcFlags={}}
        provider.read = {source={CreateBackend=function(meta)
            local ids = entityIds[meta.entity]; local map = {}; for _,id in ipairs(ids) do map[id]=true end
            return {getAllIds=function() return ids,map end,
                readField=function(id,field)
                    local r = nativeRow(meta.entity,id,"S")
                    if meta.types[field] == "table" then return nativeRow(meta.entity,id,tostring(field)) end
                    return r and r[field]
                end}
        end}}
        provider.Corrections = {ApplyRegisteredCorrections=function() end}
    ''')
    module("src/api.lua")
    module("src/support/eraToForever.lua")


def documents(lua):
    return [json.loads(lua.globals().SENT[i]) for i in range(1, len(lua.globals().SENT)+1)]


class QuestMarkers(unittest.TestCase):
    def test_delayed_publication_exports_all_three_and_clears_after_changes(self):
        lua = game()
        lua.execute('WowCompanion.questiePins.start(); WowCompanion.snapshot("heartbeat"); runTimers(NOW+1)')
        self.assertNotEqual("off", lua.eval('WowCompanion.questiePins.state()'))
        publish(lua)
        # No repeated contract/support work in a burst before the 15-second retry.
        lua.execute('WowCompanion.snapshot("heartbeat"); runTimers(NOW+1)')
        self.assertIsNone(lua.eval('WowCompanion.questiePins.state()'))
        lua.execute('NOW=1016; WowCompanion.snapshot("heartbeat"); runTimers(NOW+1)')
        self.assertEqual("ready", lua.eval('WowCompanion.questiePins.state()'))
        docs = documents(lua)
        self.assertTrue(any(d.get("type") == "qdbpins" and d.get("m") == 1429 and "err" not in d for d in docs))
        available = next(d for d in reversed(docs) if d.get("type") == "qdbavail")
        self.assertEqual([7], [q["i"] for q in available["q"]])  # 33/15 are active.
        quests = next(d["q"] for d in reversed(docs) if d.get("type") == "quests")
        self.assertTrue(next(q for q in quests["list"] if q["id"] == 33)["qdb"]["obj"])
        self.assertTrue(next(q for q in quests["list"] if q["id"] == 15)["qdb"]["turnin"])
        areas = next(d for d in reversed(docs) if d.get("type") == "qdbareas")
        self.assertTrue(areas["q"])
        fixture = {"available": available, "quests": quests, "areas": areas}
        fixture_path = ROOT / "app/src/test/resources/questie-markers.json"
        if os.environ.get("WRITE_QUEST_MARKER_FIXTURE") == "1":
            print("FIXTURE=" + json.dumps(fixture, separators=(",", ":")))
        else:
            self.assertEqual(json.loads(fixture_path.read_text()), fixture)
        lua.execute('C_SuperTrack.GetSuperTrackedQuestID=function() return 15 end; NOW=1032; WowCompanion.snapshot("heartbeat"); runTimers(NOW+1)')
        refocused = next(d["q"] for d in reversed(documents(lua)) if d.get("type") == "quests")
        self.assertEqual(15, refocused["focus"])
        lua.execute('C_QuestLog.GetQuestObjectives=function() return {{text="Tough Wolf Meat: 8/8",type="item",finished=true,numFulfilled=8,numRequired=8}} end; NOW=1032; WowCompanion.snapshot("heartbeat"); runTimers(NOW+1)')
        progressed = next(d for d in reversed(documents(lua)) if d.get("type") == "qdbareas")
        self.assertFalse(progressed["q"])
        lua.execute('CURRENT_MAP=1436; NOW=1032; WowCompanion.snapshot("heartbeat"); runTimers(NOW+1)')
        # Zone events request the new map pair; the existing event handler owns this path.
        lua.execute('for _,f in ipairs(FRAMES) do if rawget(f,"ev_ZONE_CHANGED_NEW_AREA") and rawget(f,"onOnEvent") then f.onOnEvent(f,"ZONE_CHANGED_NEW_AREA") end end; runTimers(NOW+1)')
        self.assertTrue(any(d.get("type") == "qdbavail" and d.get("m") == 1436 and not d.get("q") for d in documents(lua)))
        lua.execute('LOG={}; NOW=1048; WowCompanion.snapshot("heartbeat"); runTimers(NOW+1)')
        latest = next(d for d in reversed(documents(lua)) if d.get("type") == "qdbareas")
        self.assertFalse(latest["q"])

    def test_real_prerequisite_race_level_and_faction_gates_are_not_broadened(self):
        lua = game(); publish(lua)
        lua.execute('WowCompanion.snapshot("heartbeat"); runTimers(NOW+1)')
        self.assertIsNotNone(lua.eval('WowCompanion.questieAvail.flags(7)'))
        lua.execute('C_QuestLog.IsQuestFlaggedCompleted = function() return false end')
        self.assertIsNone(lua.eval('WowCompanion.questieAvail.flags(7)'))
        lua.execute('C_QuestLog.IsQuestFlaggedCompleted = function(id) return id == 783 end; function UnitRace() return "Orc","Orc",2 end')
        self.assertIsNone(lua.eval('WowCompanion.questieAvail.flags(7)'))
        lua.execute('function UnitRace() return "Human","Human",1 end; function UnitLevel() return 70 end')
        flags = lua.eval('WowCompanion.questieAvail.flags(7)')
        self.assertEqual(1, flags[0] & 1)  # Low-level visibility remains the saved layer's decision.
        lua.execute('function UnitLevel() return 3 end; C_QuestLog.IsQuestFlaggedCompleted=function(id) return id==783 or id==7 end')
        self.assertIsNotNone(lua.eval('WowCompanion.questieAvail.flags(3100)'))
        lua.execute('function UnitClass() return "Mage","MAGE",8 end')
        self.assertIsNone(lua.eval('WowCompanion.questieAvail.flags(3100)'))
        lua.execute('function UnitClass() return "Warrior","WARRIOR",1 end; function UnitFactionGroup() return "Horde" end; WowCompanion.questieAvail.send(true)')
        self.assertFalse(next(d for d in reversed(documents(lua)) if d.get("type") == "qdbavail")["q"])

    def test_incompatible_contract_is_not_retried_in_a_heartbeat_loop(self):
        lua = game(); publish(lua)
        lua.execute('''local realContract = provider.RequireContract; contractCalls=0
            provider.RequireContract=function() contractCalls=contractCalls+1; return realContract(99) end
            for i=1,4 do NOW=NOW+16; WowCompanion.snapshot("heartbeat"); runTimers(NOW+1) end''')
        self.assertEqual(1, lua.globals().contractCalls)
        self.assertFalse(lua.eval('WowCompanion.questieProbe().ok'))

    def test_host_fans_out_both_documents(self):
        source = (ROOT / "companion/app/src/main/java/com/jrees/wowcompanion/CompanionHost.kt").read_text()
        for kind, method in [("qdbavail", "onQdbAvail"), ("qdbareas", "onQdbAreas")]:
            line = next(l for l in source.splitlines() if '"' + kind + '" ->' in l)
            self.assertIn("mapView." + method + "(msg)", line)
            self.assertIn("worldMapRoom." + method + "(msg)", line)


if __name__ == "__main__":
    unittest.main()
