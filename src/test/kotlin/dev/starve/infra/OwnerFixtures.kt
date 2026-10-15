package dev.starve.infra

import org.springframework.jdbc.core.simple.JdbcClient

/** The first account, which the repository tests act as. */
const val OWNER = 1L

/** A second account, for tests that show the first one's data is out of its reach. */
const val OTHER = 2L

/** Inserts the accounts [OWNER] and [OTHER], which sources and groups need as their owner. */
fun JdbcClient.seedOwners() {
    listOf("owner", "other").forEach {
        sql("INSERT INTO users (username, password_hash, admin, created_at) VALUES (?, 'x', 0, 0)").param(it).update()
    }
}
