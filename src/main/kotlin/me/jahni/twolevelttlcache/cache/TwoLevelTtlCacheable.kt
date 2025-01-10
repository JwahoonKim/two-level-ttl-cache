package me.jahni.twolevelttlcache.cache

import java.lang.annotation.ElementType

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class TwoLevelTtlCacheable(
    /**
     * 캐시 이름 (Redis key prefix)
     */
    val cacheName: String,

    /**
     * 캐시 key
     */
    val key: String,

    /**
     * 소프트 TTL(논리 TTL) - 초 단위
     */
    val softTtlSeconds: Long,

    /**
     * 하드 TTL(물리 TTL) - 초 단위
     */
    val hardTtlSeconds: Long
)