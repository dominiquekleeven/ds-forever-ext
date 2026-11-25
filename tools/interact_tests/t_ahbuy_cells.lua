-- The real exporter and TapPad allocator, with no game transactions (only recording stubs).
local N, ACT, ON, AH, build = __N, __ACT, __ON, __AH, __build
local out = {}
local function chk(name, yes) out[#out + 1] = (yes and "PASS " or "FAIL ") .. name end
local f = assert(io.open(__ROOT .. "/companion/app/src/main/assets/companion/addons/WowCompanion/TapPad.lua"))
local src = f:read("*a"); f:close()
local allocator = assert(src:match("(function pad.slotFor.-)%-%- %-%-+ cells"))
local p = assert(loadstring("local pad = { ns = {} }; local COLS, QUARANTINE = 40, 30\n" .. allocator .. "\nreturn pad"))()
WowCompanion.pad.slotFor, WowCompanion.pad.retain = p.slotFor, p.retain
-- The quantity row is claimed once at addon initialization, before any window opens.
for i = 1, 40 do p.slotFor("iqty", "qty " .. i) end
local now = 1000
GetTime = function() return now end
local results, rows, calls = {}, {}, {}
for i = 1, 60 do results[i] = { itemKey = { itemID = 100 + i }, minPrice = 300, totalQuantity = 1 } end
for i = 1, 40 do rows[i] = { auctionID = 9000 + i, buyoutAmount = 300 + i, quantity = 1 } end
AuctionCategories = {}
Enum = { AuctionHouseSortOrder = { Price = 0, Name = 1, Buyout = 2, Bid = 3 } }
C_AuctionHouse = {
  IsThrottledMessageSystemReady = function() return true end,
  GetBrowseResults = function() return results end,
  GetItemKeyInfo = function() return { itemName = "Blade", isCommodity = false } end,
  GetNumItemSearchResults = function() return #rows end,
  GetItemSearchResultInfo = function(_, i) return rows[i] end,
  GetNumOwnedAuctions = function() return 0 end,
  SendSearchQuery = function() end,
  PlaceBid = function(id, price) calls[#calls + 1] = { id, price } end,
}
N.mode, N.holding, N.stale, N.fight, N.frameSeen = true, true, nil, nil, true
ON.AUCTION_HOUSE_SHOW()
AH.results = true
local d = build()
chk("browse exports the 60-result boundary", #d.auction.results == 60)
chk("browse fills all 40 ibuy cells", p.ns.ibuy.byCol[39] == "ahres 40" and p.slotFor("ibuy", "overflow") == nil)
ACT.ahres("1")
d = build()
local first, last = d.auction.item.rows[1], d.auction.item.rows[40]
chk("normal selected listing has a usable tap cell immediately after full browse", first.slot ~= nil)
chk("listing 40 also has a usable tap cell", last.slot ~= nil)
chk("selected exporter row retains exact auction ID and price", first.i == 1 and first.auction == 9001 and first.price == 301)
chk("export and allocation never purchase", #calls == 0)
__AH_BUY_DOC = d
local function tap(ns, col) ROWS[ns](p.ns[ns].byCol[col], col) end
local function ack()
  for i = #SENT, 1, -1 do if SENT[i].type == "interactack" then return SENT[i] end end
end
local function select(i)
  local doc = build()
  tap("iqty", doc.auction.item.rows[i].slot)
  return build()
end
d = select(1)
chk("first physical selection only arms the shown auction and total", AH.buy and AH.buy.auction == 9001 and AH.buy.total == 301 and #calls == 0)
chk("selection acknowledges the app command", ack() and ack().cmd == "ahrow 1" and ack().ok)
chk("controller cursor moves to the separate confirmation", d.nav.cur == "ahbuy")
tap("iqty", first.slot)
chk("a repeated or stale quantity tap cannot buy", #calls == 0 and AH.buy and AH.buy.auction == 9001)
tap("iact", d.auction.act.buy)
chk("second explicit confirmation calls only the exact stubbed ID and price", #calls == 1 and calls[1][1] == 9001 and calls[1][2] == 301 and AH.buy == nil)
tap("iact", d.auction.act.buy)
chk("confirmation cannot repeat a purchase", #calls == 1 and AH.buy == nil)

local function refused(name, mutate, why)
  AH.buy = nil; rows[1] = { auctionID = 9001, buyoutAmount = 301, quantity = 1 }; GAME.money = 100000
  d = select(1)
  mutate()
  local n = #calls
  tap("iact", d.auction.act.buy)
  chk(name, #calls == n and AH.buy == nil and ack() and not ack().ok and ack().why == why)
end
refused("moved listing cannot buy a different auction at the same index", function() rows[1].auctionID = 12345 end, "the listings changed")
refused("price changes require a new confirmation", function() rows[1].buyoutAmount = 302 end, "the listings changed")
refused("quantity changes require a new confirmation", function() rows[1].quantity = 2 end, "the listings changed")
refused("removed listing refuses confirmation", function() rows[1] = nil end, "the listings changed")
refused("changed item key refuses confirmation", function() AH.key = { itemID = 102 } end, "the listings changed")
refused("own auction refuses confirmation", function() rows[1].containsOwnerItem = true end, "that one is yours")
refused("bid-only listing refuses confirmation", function() rows[1].buyoutAmount = 0 end, "no buyout on this one")
refused("lost funds refuse confirmation", function() GAME.money = 300 end, "not enough money")
refused("expired confirmation refuses instead of buying", function() now = now + 11 end, "the listings changed")
refused("combat refuses confirmation", function() N.fight = true end, "in combat"); N.fight = nil
refused("game combat lockdown refuses confirmation", function() InCombatLockdown = function() return true end end, "in combat")
InCombatLockdown = function() return false end
refused("released capture refuses confirmation", function() N.holding = false end, "the companion is not holding this window"); N.holding = true
refused("native window mode refuses confirmation", function() N.mode = false end, "the companion is not holding this window"); N.mode = true
refused("protected game call failure is reported without retries", function() C_AuctionHouse.PlaceBid = function() error("protected") end end, "the game call failed")

local function cannotArm(name, change, why)
  AH.buy = nil; rows[1] = { auctionID = 9001, buyoutAmount = 301, quantity = 1 }; GAME.money = 100000
  d = build(); change()
  tap("iqty", d.auction.item.rows[1].slot)
  chk(name, AH.buy == nil and ack() and not ack().ok and ack().why == why)
end
cannotArm("stale displayed row refuses before arming", function() rows[1].auctionID = 9999 end, "the listings changed")
cannotArm("stale displayed price refuses before arming", function() rows[1].buyoutAmount = 600 end, "the listings changed")
cannotArm("own auction cannot arm", function() rows[1].containsOwnerItem = true end, "that one is yours")
cannotArm("bid-only auction cannot arm", function() rows[1].buyoutAmount = 0 end, "no buyout on this one")
cannotArm("insufficient funds cannot arm", function() GAME.money = 1 end, "not enough money")

-- Rapid tab/back/list changes do not consume or quarantine the permanently allocated selection cells.
rows[1] = { auctionID = 9001, buyoutAmount = 301, quantity = 1 }; GAME.money = 100000
local all = true
local browseReady = true
for round = 1, 3 do
  ACT.ahback(); d = build()
  for i = 1, 40 do if d.auction.results[i].slot ~= i - 1 then browseReady = false end end
  ACT.ahres("1"); d = build()
  for _, row in ipairs(d.auction.item.rows) do if row.slot ~= row.i - 1 then all = false end end
end
chk("all 40 listing cells stay usable during rapid browse/back transitions", all)
chk("browse cells stay claimed while listings are open, so Back needs no quarantine wait", browseReady)
for i = 1, 40 do p.slotFor("quarantine-check", "old " .. i) end
p.retain("quarantine-check", {})
chk("shared allocator quarantine is not bypassed", p.slotFor("quarantine-check", "new key") == nil)
-- The item route must not claim commodity quantity cells or alter the existing commodity protocol.
local starts, confirms = 0, 0
C_AuctionHouse.GetItemKeyInfo = function() return { itemName = "Cloth", isCommodity = true } end
C_AuctionHouse.GetNumCommoditySearchResults = function() return 1 end
C_AuctionHouse.GetCommoditySearchResultInfo = function() return { unitPrice = 10, quantity = 100 } end
C_AuctionHouse.StartCommoditiesPurchase = function(id, qty) starts = starts + 1 end
C_AuctionHouse.ConfirmCommoditiesPurchase = function(id, qty) confirms = confirms + 1 end
d = build(); AH.buy = nil
tap("iqty", 2)
chk("commodity quantity cell still selects a row without arming an item", AH.pick and AH.pick.k == 3 and AH.buy == nil and d.auction.item.rowTap == nil)
tap("iact", d.auction.act.buy)
chk("commodity first press still starts pricing only", starts == 1 and confirms == 0)
ON.COMMODITY_PRICE_UPDATED(10, 10)
tap("iact", d.auction.act.buy)
chk("commodity second press still confirms the quote", starts == 1 and confirms == 1)
return table.concat(out, "\n")
