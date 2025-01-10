package me.jahni.twolevelttlcache

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@SpringBootTest
class MemberServiceTest {

    @MockkBean
    lateinit var memberRepository: MemberRepository

    @Autowired
    lateinit var memberService: MemberService

    @Test
    fun `첫 호출시 DB에서 조회한 후 캐시에 저장되어, 연속 호출시 DB가 한 번만 호출된다`() {
        // given
        val name = "John"
        // Repository mock 세팅: findByName("John") 호출 시 Member("John") 리턴
        every { memberRepository.findByName(name) } returns Member(name = name)

        // when (첫 호출)
        val member1 = memberService.findByName(name)
        // when (두 번째 호출)
        val member2 = memberService.findByName(name)

        // then
        assertThat(member1).isNotNull
        assertThat(member2).isNotNull
        // 두 번 호출했지만, DB(Repository)는 exactly 1회만 불려야 함
        verify(exactly = 1) { memberRepository.findByName(name) }
    }

    @Test
    fun `소프트 TTL(3초) 만료 이후에는 다시 DB 조회가 일어난다`() {
        // given
        val name = "Alice"
        every { memberRepository.findByName(name) } returns Member(name = name)

        // 소프트 TTL 전 첫 호출
        val mem1 = memberService.findByName(name)
        val mem2 = memberService.findByName(name)

        // DB 호출 1회만 발생했는지 확인
        verify(exactly = 1) { memberRepository.findByName(name) }
        assertThat(mem1).isNotNull
        assertThat(mem2).isNotNull
        assertThat(mem1?.name).isEqualTo(mem2?.name)

        // 소프트 TTL(3초)이 만료되도록 4초 슬립
        Thread.sleep(4000)

        // when (소프트 TTL 만료 후 다시 호출)
        val mem3 = memberService.findByName(name)

        // then (소프트 TTL 만료로 인해 DB가 다시 불렸는지 확인)
        // 총 2회가 되어야 함 (첫 호출 시 1회 + 소프트 TTL 만료 후 1회)
        verify(exactly = 2) { memberRepository.findByName(name) }
        assertThat(mem3).isNotNull
    }

    // 이 테스트가 실패한다면 TwoLevelTtlAspect의 Lock 잡는 부분에 Thread.sleep(1000)을 추가해보세요.
    @Test
    fun `여러 스레드가 소프트 ttl이 지난 후 동시에 호출하면 DB 조회가 한번만 일어난다`() {
        val name = "Bob"
        every { memberRepository.findByName(name) } returns Member(name = name)

        val executor = ThreadPoolTaskExecutor().apply {
            corePoolSize = 2
            initialize()
        }

        // 소프트 TTL 전 첫 호출
        val mem1 = memberService.findByName(name)

        // 소프트 TTL(3초)이 만료되도록 4초 슬립
        Thread.sleep(4000)

        val futures = (1..2).map {
            executor.submit(Callable {
                memberService.findByName(name)
            })
        }

        futures.forEach { it.get() }
        executor.shutdown()

        // DB 호출 1회만 발생했는지 확인
        verify(exactly = 2) { memberRepository.findByName(name) }
    }
}