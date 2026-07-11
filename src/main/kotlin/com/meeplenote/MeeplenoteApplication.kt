package com.meeplenote

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class MeeplenoteApplication

fun main(args: Array<String>) {
    runApplication<MeeplenoteApplication>(*args)
}
