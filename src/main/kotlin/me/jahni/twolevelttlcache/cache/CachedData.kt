package me.jahni.twolevelttlcache.cache

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

data class CachedData(
    val data: String,
    val softTtl: Long,
) {
    companion object {
        private val objectMapper = jacksonObjectMapper()

        fun toJson(cachedData: CachedData): String {
            return objectMapper.writeValueAsString(cachedData)
        }

        fun fromJson(json: String): CachedData {
            return objectMapper.readValue(json, CachedData::class.java)
        }
    }
}