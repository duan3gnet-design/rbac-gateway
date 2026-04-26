--[[
  Token Bucket Rate Limiter — Redis Lua Script (optimized)

  Tối ưu so với version cũ:
    - MGET thay vì 2x GET riêng lẻ  → 1 round-trip thay vì 2
    - MSET thay vì 2x SET riêng lẻ  → 1 round-trip thay vì 2
    - PEXPIRE thay vì EX trong SET   → millisecond precision, tránh TTL bị tính sai
  Tổng: 4 commands → 3 commands (MGET + MSET + 2x PEXPIRE)

  KEYS:
    KEYS[1] = tokens key    (e.g. "rate_limit:user:alice.tokens")
    KEYS[2] = timestamp key (e.g. "rate_limit:user:alice.timestamp")

  ARGV:
    ARGV[1] = replenishRate    (tokens/second)
    ARGV[2] = burstCapacity    (max tokens)
    ARGV[3] = now              (Unix timestamp, seconds)
    ARGV[4] = requestedTokens  (thường = 1)

  RETURNS: { allowed, tokensRemaining }
    allowed = 1 nếu cho phép, 0 nếu reject
]]

local tokens_key    = KEYS[1]
local timestamp_key = KEYS[2]

local replenish_rate   = tonumber(ARGV[1])
local burst_capacity   = tonumber(ARGV[2])
local now              = tonumber(ARGV[3])
local requested_tokens = tonumber(ARGV[4])

-- TTL tính bằng milliseconds (dùng cho PEXPIRE)
local ttl_ms = math.floor(burst_capacity / replenish_rate * 2 * 1000)

-- MGET: 1 round-trip thay vì 2x GET
local values     = redis.call("MGET", tokens_key, timestamp_key)
local last_tokens    = tonumber(values[1])
local last_refreshed = tonumber(values[2])

if last_tokens == nil    then last_tokens    = burst_capacity end
if last_refreshed == nil then last_refreshed = now            end

-- Tính token được nạp lại
local delta        = math.max(0, now - last_refreshed)
local filled_tokens = math.min(burst_capacity, last_tokens + (delta * replenish_rate))

local allowed    = 0
local new_tokens = filled_tokens

if filled_tokens >= requested_tokens then
    new_tokens = filled_tokens - requested_tokens
    allowed    = 1
end

-- MSET: 1 round-trip thay vì 2x SET
redis.call("MSET", tokens_key, new_tokens, timestamp_key, now)

-- PEXPIRE cho cả 2 key (millisecond precision)
redis.call("PEXPIRE", tokens_key,    ttl_ms)
redis.call("PEXPIRE", timestamp_key, ttl_ms)

return { allowed, new_tokens }
