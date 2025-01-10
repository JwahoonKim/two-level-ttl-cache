package me.jahni.twolevelttlcache

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.EnableAspectJAutoProxy

@EnableAspectJAutoProxy
@SpringBootApplication
class TwoLevelTtlCacheApplication

fun main(args: Array<String>) {
    runApplication<TwoLevelTtlCacheApplication>(*args)
}
