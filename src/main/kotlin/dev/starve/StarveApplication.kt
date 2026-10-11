package dev.starve

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class StarveApplication

@Suppress("SpreadOperator") // Boot bootstrap: forwarding argv is the only spread
fun main(args: Array<String>) {
    runApplication<StarveApplication>(*args)
}
