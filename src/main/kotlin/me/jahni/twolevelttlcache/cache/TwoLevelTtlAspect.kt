package me.jahni.twolevelttlcache.cache

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.slf4j.LoggerFactory
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.expression.spel.support.StandardEvaluationContext
import org.springframework.stereotype.Component
import java.time.Instant

@Aspect
@Component
class TwoLevelTtlAspect(
    private val redisCacheService: RedisCacheService
) {

    private val parser = SpelExpressionParser()

    @Around("@annotation(TwoLevelTtlCacheable)")
    fun around(pjp: ProceedingJoinPoint): Any? {
        val signature = pjp.signature as MethodSignature
        val method = signature.method
        val annotation = method.getAnnotation(TwoLevelTtlCacheable::class.java)
        val returnType = method.returnType
        val args = pjp.args

        val softTtlSeconds = annotation.softTtlSeconds
        val hardTtlSeconds = annotation.hardTtlSeconds

        val redisKey = generateKey(signature, annotation, args)
        val cachedData = redisCacheService.getCachedData(redisKey)

        if (cachedData == null) {
            logger.info("Cache miss: key = $redisKey")
            return fillCacheAndReturn(pjp, redisKey, softTtlSeconds, hardTtlSeconds)
        }

        val now = Instant.now().toEpochMilli()
        if (now > cachedData.softTtl) {
            logger.info("Cache soft expired: key = $redisKey")
            val lockKey = "$redisKey::lock"
            val tryLock = redisCacheService.tryLock(lockKey, 3)
            return if (tryLock) {
                try {
                    logger.info("Lock Acquired. Cache soft filled : key = $redisKey")
                    return fillCacheAndReturn(pjp, redisKey, softTtlSeconds, hardTtlSeconds)
                } finally {
                    redisCacheService.unlock(lockKey)
                }
            } else {
                logger.info("Another thread is filling cache: key = $redisKey return cached data")
                objectMapper.readValue(cachedData.data, returnType)
            }
        }

        logger.info("Cache hit. Return cached data: key = $redisKey")
        return objectMapper.readValue(cachedData.data, returnType)
    }

    //SpEL을 이용하여 key를 생성
    private fun generateKey(
        signature: MethodSignature,
        annotation: TwoLevelTtlCacheable,
        args: Array<Any>
    ): String {
        val parameterNames = signature.parameterNames
        val cacheName = annotation.cacheName
        val keyExpression = annotation.key
        val context = StandardEvaluationContext()
        parameterNames.forEachIndexed { index, name ->
            context.setVariable(name, args[index])
        }
        val key = parser.parseExpression(keyExpression).getValue(context, String::class.java)
        return "$cacheName::$key"
    }

    private fun fillCacheAndReturn(
        pjp: ProceedingJoinPoint,
        redisKey: String,
        softTtlSeconds: Long,
        hardTtlSeconds: Long
    ): Any? {
        val result = pjp.proceed()

        val now = Instant.now().toEpochMilli()
        val softTtlExpiredTime = now + softTtlSeconds * 1000

        val cachedData = CachedData(
            data = objectMapper.writeValueAsString(result),
            softTtl = softTtlExpiredTime
        )

        redisCacheService.putCachedData(redisKey, cachedData, hardTtlSeconds)

        logger.info("Cache filled. key = $redisKey, softTtl = $softTtlExpiredTime")
        return result
    }

    companion object {
        private val objectMapper = jacksonObjectMapper()
        private val logger = LoggerFactory.getLogger(TwoLevelTtlAspect::class.java)
    }
}