package me.jahni.twolevelttlcache

import me.jahni.twolevelttlcache.cache.TwoLevelTtlCacheable
import org.springframework.stereotype.Service

@Service
class MemberService(
    private val memberRepository: MemberRepository
) {
    @TwoLevelTtlCacheable(
        cacheName = "member",
        key = "#name",
        softTtlSeconds = 3,
        hardTtlSeconds = 5,
    )
    fun findByName(name: String): Member? {
        return memberRepository.findByName(name)
    }
}