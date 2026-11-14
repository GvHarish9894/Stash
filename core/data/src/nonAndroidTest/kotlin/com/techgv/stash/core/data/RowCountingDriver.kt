package com.techgv.stash.core.data

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

/**
 * Counts the rows the paged item lists actually step through (Room wraps their SQL as `SELECT * FROM ( ... ) LIMIT`),
 * so a test can assert how much of the table a page load read, and records every SQL text prepared. For in-memory
 * databases on the test scheduler: one connection, one thread, so the plain counter and list need no lock.
 */
class RowCountingDriver(private val driver: SQLiteDriver = BundledSQLiteDriver()) : SQLiteDriver {
    var listRowsRead = 0
    val prepared = ArrayList<String>()

    override fun open(fileName: String): SQLiteConnection = CountingConnection(driver.open(fileName))

    private inner class CountingConnection(private val connection: SQLiteConnection) : SQLiteConnection by connection {
        override fun prepare(sql: String): SQLiteStatement {
            val statement = connection.prepare(sql)
            prepared += sql
            return if (sql.startsWith("SELECT * FROM ( SELECT * FROM saved_item")) CountingStatement(statement) else statement
        }
    }

    private inner class CountingStatement(private val statement: SQLiteStatement) : SQLiteStatement by statement {
        override fun step(): Boolean = statement.step().also { if (it) listRowsRead++ }
    }
}
