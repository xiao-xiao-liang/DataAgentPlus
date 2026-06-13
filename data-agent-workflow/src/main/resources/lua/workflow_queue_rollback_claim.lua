local queueKey = KEYS[1]
local globalRunningKey = KEYS[2]
local userRunningKey = KEYS[3]
local taskKey = KEYS[4]
local queueId = ARGV[1]

local userId = redis.call('HGET', taskKey, 'userId')
local score = redis.call('HGET', taskKey, 'score')
if userId then
    local globalRunning = tonumber(redis.call('GET', globalRunningKey) or '0')
    if globalRunning > 0 then
        redis.call('DECR', globalRunningKey)
    end
    local userRunning = tonumber(redis.call('HGET', userRunningKey, userId) or '0')
    if userRunning > 1 then
        redis.call('HINCRBY', userRunningKey, userId, -1)
    elseif userRunning == 1 then
        redis.call('HDEL', userRunningKey, userId)
    end
end
if score then
    redis.call('ZADD', queueKey, score, queueId)
end
return 1
