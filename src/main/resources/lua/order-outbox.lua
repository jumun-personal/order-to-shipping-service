-- KEYS[1] = outbox:stream
-- KEYS[2] = idempotency prefix

-- ARGV[1] = orderId
-- ARGV[2] = orderProductsJson
-- ARGV[3] = couponId (nullable, "nil")
-- ARGV[4] = couponDiscount
-- ARGV[5] = idempotencyKey
-- ARGV[6] = eventPayloadJson

local streamKey = KEYS[1]
local idempotencyPrefix = KEYS[2]

local orderId = ARGV[1]
local orderProductsJson = ARGV[2]
local couponId = ARGV[3]
local idempotencyKey = ARGV[5]
local eventPayload = ARGV[6]

-- 1. 멱등성 체크
local idempotencyCheckKey = idempotencyPrefix .. idempotencyKey
if redis.call("EXISTS", idempotencyCheckKey) == 1 then
    --  JSON 문자열로 반환
    return cjson.encode({err="DUPLICATE_REQUEST"})
end

-- 2. JSON 파싱
local orderProducts
local success, result = pcall(function()
    return cjson.decode(orderProductsJson)
end)

if not success then
    --  JSON 문자열로 반환
    return cjson.encode({err="JSON_PARSE_ERROR", details=tostring(result)})
end

orderProducts = result

-- 3. 재고 키 배열 생성
local stockKeys = {}
for _, product in ipairs(orderProducts) do
    table.insert(stockKeys, "bf:stock:" .. product.productId)
end

-- 4. 배치 재고 조회
local stocks = redis.call("MGET", unpack(stockKeys))

-- 5. 재고 검증
for i, product in ipairs(orderProducts) do
    local currentStock = tonumber(stocks[i] or "0")
    local required = tonumber(product.quantity)

    if currentStock < required then
        --  JSON 문자열로 반환
        return cjson.encode({
            err="INSUFFICIENT_STOCK",
            productId=product.productId,
            required=required,
            available=currentStock
        })
    end
end

-- 6. 쿠폰 검증
if couponId ~= "nil" and couponId ~= "" then
    local couponKey = "coupon:used:" .. couponId
    if redis.call("EXISTS", couponKey) == 1 then
        --  JSON 문자열로 반환
        return cjson.encode({err="COUPON_ALREADY_USED", couponId=couponId})
    end
end

-- 7. 재고 차감
for _, product in ipairs(orderProducts) do
    redis.call("DECRBY", "bf:stock:" .. product.productId, tonumber(product.quantity))
end

-- 8. 쿠폰 사용
if couponId ~= "nil" and couponId ~= "" then
    redis.call("SETEX", "coupon:used:" .. couponId, 86400, "1")
end

-- 9. 멱등성 키 & Outbox 발행
redis.call("SETEX", idempotencyCheckKey, 86400, orderId)

local messageId = redis.call("XADD", streamKey, "*",
    "orderId", orderId,
    "idempotencyKey", idempotencyKey,
    "payload", eventPayload,
    "status", "pending",
    "timestamp", redis.call("TIME")[1]
)

--  JSON 문자열로 반환
return cjson.encode({ok="SUCCESS", messageId=messageId, orderId=orderId})