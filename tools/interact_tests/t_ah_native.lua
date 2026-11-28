-- Shipping policy: Blizzard owns AH. No query, purchase, post or mirror capture.
local N, ACT, ON = __N, __ACT, __ON
local out = {}
local function chk(name, yes) out[#out + 1] = (yes and "PASS " or "FAIL ") .. name end
local calls = 0
C_AuctionHouse = setmetatable({}, { __index = function() return function() calls = calls + 1 end end })
N.mode, N.frameSeen, N.trusted = true, true, true
chk("native AH is the shipping default", WowCompanion.interactWindows.auction == false)
ON.AUCTION_HOUSE_SHOW()
N.early("auction")
local d = __build()
chk("AH opening makes no companion auction API calls", calls == 0)
chk("AH opening publishes no mirrored AH", not d.auction)
chk("AH opening does not take NPC input", not N.holding)
local ok = ACT.ahrow("1", "confirm")
chk("dormant AH purchase commands refuse native mode", ok == false and calls == 0)
AuctionHouseFrame = { IsShown = function() return true end }
chk("native tooltip exemption follows visible AH", WowCompanion.nativeAuctionOpen() == true)
AuctionHouseFrame.IsShown = function() return false end
chk("native tooltip exemption ends when AH closes", WowCompanion.nativeAuctionOpen() == false)
chk("other mirrored windows stay enabled", WowCompanion.interactWindows.mail and WowCompanion.interactWindows.merchant ~= false
    and WowCompanion.interactWindows.trainer and WowCompanion.interactWindows.trade)
return table.concat(out, "\n")
