package me.jahni.twolevelttlcache.cache

import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
class RedisCacheService(
    private val redisTemplate: RedisTemplate<String, String>
) {
    private val ops = redisTemplate.opsForValue()

    fun getCachedData(key: String): CachedData? {
        val cachedValue = ops.get(key) ?: return null
        return CachedData.fromJson(cachedValue)
    }

    fun putCachedData(key: String, cachedData: CachedData, hardTtlSeconds: Long) {
        val json = CachedData.toJson(cachedData)
        ops.set(key, json, hardTtlSeconds, TimeUnit.SECONDS)
    }

    fun tryLock(key: String, ttl: Long): Boolean {
        return ops.setIfAbsent(key, "lock", ttl, TimeUnit.SECONDS) ?: false
    }

    fun unlock(key: String) {
        redisTemplate.delete(key)
    }
}