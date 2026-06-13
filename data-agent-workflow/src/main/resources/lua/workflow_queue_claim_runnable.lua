local queueKey = KEYS[1]
local globalRunningKey = KEYS[2]
local userRunningKey = KEYS[3]
local taskKeyPrefix = KEYS[4]
local maxGlobalRunning = tonumber(ARGV[1])
local maxUserRunning = tonumber(ARGV[2])
local scanWindow = tonumber(ARGV[3])

local globalRunning = tonumber(redis.call('GET', globalRunningKey) or '0')
local availableSlots = maxGlobalRunning - globalRunning
if availableSlots <= 0 then
    return {}
end

local headEntries = redis.call('ZRANGE', queueKey, 0, scanWindow - 1)
local selected = {}
local selectedUsers = {}

for i = 1, #headEntries do
    if #selected >= availableSlots then
        break
    end
    local queueId = headEntries[i]
    local taskKey = taskKeyPrefix .. queueId
    local userId = redis.call('HGET', taskKey, 'userId')
    if not userId then
        redis.call('ZREM', queueKey, queueId)
    else
        local userRunning = tonumber(redis.call('HGET', userRunningKey, userId) or '0')
        if userRunning < maxUserRunning and not selectedUsers[userId] then
            selectedUsers[userId] = true
            redis.call('ZREM', queueKey, queueId)
            redis.call('INCR', globalRunningKey)
            redis.call('HINCRBY', userRunningKey, userId, 1)
            table.insert(selected, queueId)
        end
    end
end

return selected
