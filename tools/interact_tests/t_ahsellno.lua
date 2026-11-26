-- Backport of e46b123a, issue 590: actual sell collector and controller under stubs only.
local N, ACT, ON, build, AH = __N, __ACT, __ON, __build, __AH
local out = {}
local function chk(n, c, got) out[#out + 1] = (c and "PASS " or "FAIL ") .. n .. ((not c and got ~= nil) and (" -> " .. tostring(got)) or "") end
GAME.money = 1000000
local BAGS = { [0] = { { id = 2589, name = "Linen Cloth", n = 20 }, { id = 6948, name = "Hearthstone", n = 1, bound = true },
    { id = 774, name = "Malachite", n = 1 }, { id = 5000, name = "Quest Thing", n = 1, quest = true },
    { id = 2488, name = "Worn Axe", n = 1, dur = { 3, 20 } }, { id = 9999, name = "Odd Thing", n = 1, refuse = true } },
    [2] = { { id = 6949, name = "Bound Ring", n = 1, bound = true } } }
C_Container = {
    GetContainerNumSlots = function(bag) return BAGS[bag] and 6 or 0 end,
    GetBagName = function(bag) return bag == 0 and "Backpack" or "Small Bag" end,
    GetContainerItemInfo = function(bag, s) local it = BAGS[bag] and BAGS[bag][s]; if it then
        return { itemID = it.id, iconFileID = 133, stackCount = it.n, quality = 1, isBound = it.bound or false,
            hyperlink = "|cff|Hitem:" .. it.id .. "|h[" .. it.name .. "]|h|r" } end end,
    GetContainerItemQuestInfo = function(bag, s) local it = BAGS[bag] and BAGS[bag][s]
        return { isQuestItem = (it and it.quest) or false } end,
    GetContainerItemDurability = function(bag, s) local it = BAGS[bag] and BAGS[bag][s]; if it and it.dur then return it.dur[1], it.dur[2] end end,
    GetContainerNumFreeSlots = function() return 0, 0 end,
}
AuctionCategories = { { name = "Weapons" } }
ItemLocation = { CreateFromBagAndSlot = function(_, bag, s) return { bag = bag, s = s } end }
local function at(loc) return BAGS[loc.bag] and BAGS[loc.bag][loc.s] end
C_Item = {
    GetItemID = function(loc) local it = at(loc); return it and it.id end,
    DoesItemExist = function(loc) return at(loc) ~= nil end,
    GetStackCount = function(loc) local it = at(loc); return it and it.n end,
    GetItemLink = function(loc) local it = at(loc); return it and ("|Hitem:" .. it.id .. "|h[" .. it.name .. "]|h") end,
    GetItemInfo = function() return "x", nil, nil, nil, nil, nil, nil, nil, nil, nil, 25 end,
    GetItemName = function(loc) local it = at(loc); return it and it.name end,
    GetItemIcon = function() return 134 end, GetItemQuality = function() return 1 end, GetCurrentItemLevel = function() return 5 end,
    GetItemInfoInstant = function(x) return tonumber(tostring(x):match("item:(%d+)")) end,
    RequestLoadItemDataByID = function() end,
}
Enum = { ItemCommodityStatus = { Unknown = 0, Item = 1, Commodity = 2 }, AuctionHouseSortOrder = { Price = 0 } }
Constants = { AuctionConstants = { DEFAULT_AUCTION_PRICE_MULTIPLIER = 1.5 } }
local posts = 0
local function noPost() posts = posts + 1; error("test must never post") end
C_AuctionHouse = {
    IsThrottledMessageSystemReady = function() return true end,
    IsSellItemValid = function(loc) local it = at(loc)
        return it ~= nil and not it.bound and not it.quest and not it.dur and not it.refuse end,
    GetItemKeyFromItem = function(loc) return { itemID = at(loc).id, itemLevel = 5 } end,
    GetItemCommodityStatus = function() return 2 end,
    GetAvailablePostCount = function(loc) return at(loc).n end,
    ShouldAutoPopulatePrice = function() return true end, SupportsCopperValues = function() return false end,
    CalculateCommodityDeposit = function() return 60 end, CalculateItemDeposit = function() return 60 end,
    SendSearchQuery = function() end, SendBrowseQuery = function() end,
    GetNumCommoditySearchResults = function() return 0 end, GetNumItemSearchResults = function() return 0 end,
    GetItemKeyInfo = function() return nil end,
    GetBrowseResults = function() return {} end, HasFullBrowseResults = function() return true end,
    GetNumOwnedAuctions = function() return 0 end,
    PostItem = noPost, PostCommodity = noPost,
}
C_Timer = { After = function() end, NewTicker = function() return { Cancel = function() end } end, NewTimer = function() return { Cancel = function() end } end }
N.mode, N.holding, N.stale, N.fight, N.frameSeen = true, true, nil, nil, true
ON.AUCTION_HOUSE_SHOW()
ACT.ahtab("sell")
local d = build()
local s = d.auction.sell
chk("the grid lists only sellable Linen and Malachite", #s.bags == 2 and s.bags[1].id == 2589 and s.bags[2].id == 774)
local byId = {}
for _, b in ipairs(s.no or {}) do byId[b.id] = b end
chk("the five refused items are listed", s.no ~= nil and #s.no == 5)
chk("bound reason comes from the slot", byId[6948] and byId[6948].no == "bound" and byId[6948].bag == 0 and byId[6948].s == 2)
chk("quest reason comes from the game", byId[5000] and byId[5000].no == "quest")
chk("damage reason comes from durability", byId[2488] and byId[2488].no == "damaged")
chk("unknown refusal uses no", byId[9999] and byId[9999].no == "no")
chk("refused tiles carry icon count and quality", byId[6948].icon == 133 and byId[6948].n == 1 and byId[6948].q == 1)
local noCells = true
for _, b in ipairs(s.no or {}) do if b.slot ~= nil then noCells = false end end
chk("no refused item has a tap cell", noCells)
chk("sellable tiles retain their cells", s.bags[1].slot ~= nil and s.bags[2].slot ~= nil)
chk("backpack counts sellable and refused", s.groups[1].bag == 0 and s.groups[1].n == 2 and s.groups[1].x == 4)
chk("refused-only bag retains its header", #s.groups == 2 and s.groups[2].bag == 2 and s.groups[2].n == 0 and s.groups[2].x == 1)
chk("report counts both lists", d.auction.sb == 2 and d.auction.sx == 5)
chk("cursor starts on first header", d.nav.cur == "ahsfold 0", d.nav.cur)
N.move("down"); chk("Down selects first sellable", N.cur == "ahsitem 0 1 2589", N.cur)
N.move("right"); chk("Right skips bound item", N.cur == "ahsitem 0 3 774", N.cur)
N.move("right"); chk("Right skips all remaining refused tiles", N.cur == "ahsitem 0 3 774", N.cur)
N.move("down"); chk("Down reaches refused-only bag header", N.cur == "ahsfold 2", N.cur)
local ok = ACT.ahsitem("0", "2", "6948")
chk("stale command cannot select a refused item", not ok and AH.sell == nil)
ok = ACT.ahsitem("0", "1", "2589")
chk("valid item still selects", ok and AH.sell ~= nil)
chk("selection and controller navigation never post", posts == 0)
AH.sell = nil
BAGS[0] = { { id = 2589, name = "Linen Cloth", n = 20 } }; BAGS[2] = nil
d = build()
chk("nothing refused omits list and refused count", d.auction.sell.no == nil and d.auction.sell.groups[1].x == nil and #d.auction.sell.groups == 1)
return table.concat(out, "\n")
