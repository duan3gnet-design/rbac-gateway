--[[
  Token Bucket Rate Limiter — Redis Lua Script
  Chạy atomic trên Redis, không có race condition.

  KEYS:
    KEYS[1] = tokens key   (e.g. "rate_limit:user:alice.tokens")
    KEYS[2] = timestamp key (e.g. "rate_limit:user:alice.timestamp")

  ARGV:
    ARGV[1] = replenishRate    (tokens/second được nạp lại)
    ARGV[2] = burstCapacity    (max tokens trong bucket)
    ARGV[3] = now              (Unix timestamp hiện tại, seconds)
    ARGV[4] = requestedTokens  (số token request này cần, thường = 1)

  RETURNS: { allowed, tokensRemaining }
    allowed = 1 nếu cho phép, 0 nếu reject
]]

local tokens_key     = KEYS[1]
local timestamp_key  = KEYS[2]

local replenish_rate    = tonumber(ARGV[1])
local burst_capacity    = tonumber(ARGV[2])
local now               = tonumber(ARGV[3])
local requested_tokens  = tonumber(ARGV[4])

-- TTL = burst_capacity / replenish_rate * 2 (buffer)
local ttl = math.floor(burst_capacity / replenish_rate * 2)

-- Đọc trạng thái hiện tại từ Redis
local last_tokens    = tonumber(redis.call("GET", tokens_key))
local last_refreshed = tonumber(redis.call("GET", timestamp_key))

-- Khởi tạo nếu chưa có
if last_tokens == nil then
    last_tokens = burst_capacity
end
if last_refreshed == nil then
    last_refreshed = now
end

-- Tính số token được nạp lại kể từ lần cuối
local delta = math.max(0, now - last_refreshed)
local filled_tokens = math.min(burst_capacity, last_tokens + (delta * replenish_rate))

-- Kiểm tra xem có đủ token không
local allowed = 0
local new_tokens = filled_tokens

if filled_tokens >= requested_tokens then
    new_tokens = filled_tokens - requested_tokens
    allowed = 1
end

-- Lưu trạng thái mới vào Redis với TTL
redis.call("SET", tokens_key,    new_tokens,  "EX", ttl)
redis.call("SET", timestamp_key, now,         "EX", ttl)

return { allowed, new_tokens }
