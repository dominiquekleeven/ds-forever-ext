-- Actual AH exporter/selection/tooltip reader. All game APIs are read-only recording stubs.
local N, ACT, ON, AH, build = __N, __ACT, __ON, __AH, __build
local out = {}
local function chk(name, yes) out[#out + 1] = (yes and "PASS " or "FAIL ") .. name end
local linkA = "|cff1eff00|Hitem:500:0:0:0:0:0:17:0:10|h[Mail of Strength]|h|r"
local linkB = "item:501:0:0:0:0:0:18:0:10"
local rows = {
  { auctionID = 7001, itemLink = linkA, buyoutAmount = 1000, quantity = 1 },
  { auctionID = 7002, itemLink = linkB, buyoutAmount = 1200, quantity = 2 },
}
local timers, requests, reads, searches = {}, {}, {}, 0
local pending = {}
C_Timer.After = function(_, fn) timers[#timers + 1] = fn end
AuctionCategories = {}
Enum = { AuctionHouseSortOrder = { Price = 0, Name = 1, Buyout = 2, Bid = 3 }, TooltipDataLineType = { SellPrice = 11 } }
C_Item = {
  GetItemInfoInstant = function(x) return tonumber(tostring(x):match("item:(%d+)")) or tonumber(x), nil, nil, "INVTYPE_CHEST" end,
  RequestLoadItemDataByID = function(id) requests[#requests + 1] = id end,
  IsItemDataCachedByID = function(id) return not pending[id] end,
}
C_AuctionHouse = {
  IsThrottledMessageSystemReady = function() return true end,
  GetItemKeyInfo = function() return { itemName = "Mail", isCommodity = false } end,
  GetItemKeyRequiredLevel = function() return 10 end,
  GetBrowseResults = function() return { { itemKey = { itemID = 500, itemLevel = 20, itemSuffix = 17 } } } end,
  GetNumOwnedAuctions = function() return 0 end,
  GetNumItemSearchResults = function() return #rows end,
  GetItemSearchResultInfo = function(_, i) return rows[i] end,
  SendSearchQuery = function() searches = searches + 1 end,
}
local function rich(link)
  local a = link == linkA
  return { hyperlink = link, lines = {
    { leftText = a and "Mail of Strength" or "Mail of Stamina" },
    { leftText = a and "87 Armor" or "93 Armor", rightText = "Mail" },
    { leftText = a and "+5 Strength" or "+7 Stamina", leftColor = { r = 0, g = 1, b = 0 } },
    { leftText = "Requires Level 10", leftColor = { r = 1, g = 0, b = 0 } },
    { type = 11, price = 123 },
  } }
end
C_TooltipInfo = {
  GetHyperlink = function(link)
    reads[#reads + 1] = link
    local id = C_Item.GetItemInfoInstant(link)
    if pending[id] then return nil end
    return rich(link)
  end,
  GetItemKey = function() return { lines = { { leftText = "Generic base mail (not the listing)" } } } end,
}
N.mode, N.holding, N.stale, N.fight, N.frameSeen = true, true, nil, nil, true
ON.AUCTION_HOUSE_SHOW(); AH.results = true; build(); ACT.ahres("1")
local d = build()
N.cur = "ahrow 1"; d = build()
chk("D-pad equipment tooltip exports actual armor, attribute, requirement and sell price", d.tip and d.tip.lines and #d.tip.lines == 5
  and d.tip.lines[2].t == "87 Armor" and d.tip.lines[2].r == "Mail" and d.tip.lines[3].t == "+5 Strength"
  and d.tip.lines[4].t == "Requires Level 10" and d.tip.lines[5].m == 123)
chk("listing publishes a read-only look cell on the retained browse row", d.auction.item.rows[2].look ~= nil and d.auction.item.rows[2].lookRow == "ibuy")
if d.auction.item.rows[2].look then
  local before = searches
  ROWS.ibuy("ahres 2", d.auction.item.rows[2].look)
  d = build()
  chk("touch selection asks for row 2 stats, never opens another browse result or arms a purchase", searches == before and N.cur == "ahrow 2" and AH.buy == nil)
else chk("touch selection asks for row 2 stats, never opens another browse result or arms a purchase", false) end
local t = N.tipOf("ahrow 2")
chk("raw listing hyperlinks keep the exact suffix/level variant instead of a base item", t and t.lines and t.lines[3] and t.lines[3].t == "+7 Stamina" and reads[#reads] == linkB)
chk("tooltip identity follows the selected auction", t and t.auction == 7002)
chk("price and count remain in the listing document", d.auction.item.rows[2].price == 1200 and d.auction.item.rows[2].count == 2)

-- Uncached data only for the tooltip being viewed; repeated documents must coalesce its load request.
pending[500] = true; requests = {}; timers = {}; N.cur = "ahrow 1"
d = build(); local again = build()
chk("uncached gear clearly shows loading", d.tip and d.tip.loading and d.tip.lines and d.tip.lines[#d.tip.lines].t == "Loading item information...")
chk("pending reads coalesce to one item-data request", #requests == 1 and requests[1] == 500)
chk("only the selected pending item is requested", #requests == 1)
local oldTimers = timers; timers = {}
N.cur = "ahrow 2"; d = build(); pending[500] = nil
local oldEvent = N.ahTipLoaded and N.ahTipLoaded(500, true)
for _, fn in ipairs(oldTimers) do fn() end
d = build()
chk("late data/timer for row 1 does not replace row 2 stats", not oldEvent and d.tip and d.tip.key == "ahrow 2" and d.tip.lines[3] and d.tip.lines[3].t == "+7 Stamina")
pending[500] = true; N.cur = "ahrow 1"; d = build(); pending[500] = nil
local loaded = N.ahTipLoaded and N.ahTipLoaded(500, true)
d = build()
chk("item-data completion recovers the current rich tooltip", loaded and d.tip and not d.tip.loading and d.tip.lines[3].t == "+5 Strength")
__AH_TOOLTIP_DOC = d
local hyperlink = C_TooltipInfo.GetHyperlink
C_TooltipInfo.GetHyperlink = function() return { lines = { { leftText = "Mail of Strength" }, { leftText = "Retrieving item information" } } } end
d = build()
chk("a cached base item with a multi-line retrieval placeholder still loads its variant stats", d.tip and d.tip.loading)
C_TooltipInfo.GetHyperlink = hyperlink
N.ahTipLoaded(500, true); d = build()
chk("placeholder completion restores actual gear stats", d.tip and not d.tip.loading and d.tip.lines[3].t == "+5 Strength")
-- Retry state is scoped to an item identity, not a row number reused by another result.
pending[500] = true; requests = {}; timers = {}; N.ahTipState = nil
for i = 1, 12 do
  N.tipOf("ahrow 1")
  local q = timers; timers = {}; for _, fn in ipairs(q) do fn() end
end
chk("missing data retry requests remain bounded", #requests <= N.AH_TIP_TRIES)
rows[1].auctionID, rows[1].itemLink = 7999, "item:502:0:0:0:0:0:19"
pending[502] = true
N.cur = "ahrow 1"; d = build()
chk("a different auction at the same row gets a fresh load request", requests[#requests] == 502 and d.tip and d.tip.auction == 7999 and d.tip.loading)
-- A loaded commodity with a valid one-line tooltip does not acquire invented gear stats or loading retries.
local before = #requests
C_AuctionHouse.GetItemKeyInfo = function() return { itemName = "Cloth", isCommodity = true } end
C_AuctionHouse.GetNumCommoditySearchResults = function() return 1 end
C_AuctionHouse.GetCommoditySearchResultInfo = function() return { unitPrice = 5, quantity = 20 } end
AH.key = { itemID = 600 }; N.cur = "ahbuy"
C_TooltipInfo.GetItemKey = function() return { lines = { { leftText = "Cloth" } } } end
d = build()
chk("loaded commodity keeps its own tooltip without gear stats or unnecessary loading", d.tip and not d.tip.loading and #d.tip.lines == 1 and #requests == before)
return table.concat(out, "\n")
