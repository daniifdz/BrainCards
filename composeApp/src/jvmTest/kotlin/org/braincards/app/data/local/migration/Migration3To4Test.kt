package org.braincards.app.data.local.migration

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Test manual (sin MigrationTestHelper) sobre una conexion SQLite en memoria.
 * Siembra el esquema v3 REAL (commit ddd5ef3: PKs INTEGER AUTOINCREMENT, sin
 * columnas de sync ni cardType), ejecuta MIGRATION_3_4.migrate(...) directamente,
 * y verifica el resultado con aserciones de kotlin.test.
 */
class Migration3To4Test {

    private fun createV3Schema(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE decks (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `description` TEXT)"
        )
        connection.execSQL(
            "CREATE TABLE topics (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `deckId` INTEGER NOT NULL, " +
                "`name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`deckId`) REFERENCES `decks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        connection.execSQL(
            "CREATE TABLE flashcards (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `deckId` INTEGER NOT NULL, " +
                "`topicId` INTEGER, `question` TEXT NOT NULL, `answer` TEXT NOT NULL, `easeFactor` REAL NOT NULL, " +
                "`interval` INTEGER NOT NULL, `repetitions` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, `nextReview` INTEGER NOT NULL, " +
                "FOREIGN KEY(`deckId`) REFERENCES `decks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                "FOREIGN KEY(`topicId`) REFERENCES `topics`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        connection.execSQL("CREATE INDEX `index_topics_deckId` ON `topics` (`deckId`)")
        connection.execSQL("CREATE INDEX `index_flashcards_deckId` ON `flashcards` (`deckId`)")
        connection.execSQL("CREATE INDEX `index_flashcards_topicId` ON `flashcards` (`topicId`)")
    }

    private fun seedV3Data(connection: SQLiteConnection) {
        // 2 decks
        connection.execSQL("INSERT INTO decks (id, name, createdAt, updatedAt, description) VALUES (1, 'Deck A', 1000, 1000, 'desc A')")
        connection.execSQL("INSERT INTO decks (id, name, createdAt, updatedAt, description) VALUES (2, 'Deck B', 2000, 2000, 'desc B')")

        // 2 topics, en decks distintos
        connection.execSQL("INSERT INTO topics (id, deckId, name, createdAt, updatedAt) VALUES (10, 1, 'Topic A1', 1100, 1100)")
        connection.execSQL("INSERT INTO topics (id, deckId, name, createdAt, updatedAt) VALUES (20, 2, 'Topic B1', 2100, 2100)")

        // 5 flashcards: con topic, sin topic, repartidas entre los dos decks
        connection.execSQL(
            "INSERT INTO flashcards (id, deckId, topicId, question, answer, easeFactor, interval, repetitions, createdAt, updatedAt, nextReview) " +
                "VALUES (100, 1, 10, 'Q1', 'A1', 2.5, 1, 0, 1200, 1200, 1300)"
        )
        connection.execSQL(
            "INSERT INTO flashcards (id, deckId, topicId, question, answer, easeFactor, interval, repetitions, createdAt, updatedAt, nextReview) " +
                "VALUES (101, 1, NULL, 'Q2', 'A2', 2.5, 1, 0, 1210, 1210, 1310)"
        )
        connection.execSQL(
            "INSERT INTO flashcards (id, deckId, topicId, question, answer, easeFactor, interval, repetitions, createdAt, updatedAt, nextReview) " +
                "VALUES (102, 2, 20, 'Q3', 'A3', 2.5, 1, 0, 2200, 2200, 2300)"
        )
        connection.execSQL(
            "INSERT INTO flashcards (id, deckId, topicId, question, answer, easeFactor, interval, repetitions, createdAt, updatedAt, nextReview) " +
                "VALUES (103, 2, NULL, 'Q4', 'A4', 2.5, 1, 0, 2210, 2210, 2310)"
        )
        connection.execSQL(
            "INSERT INTO flashcards (id, deckId, topicId, question, answer, easeFactor, interval, repetitions, createdAt, updatedAt, nextReview) " +
                "VALUES (104, 1, 10, 'Q5', 'A5', 2.5, 1, 0, 1220, 1220, 1320)"
        )
    }

    private fun SQLiteConnection.queryCount(sql: String): Long {
        val stmt = prepare(sql)
        try {
            check(stmt.step()) { "La query no devolvio ninguna fila: $sql" }
            return stmt.getLong(0)
        } finally {
            stmt.close()
        }
    }

    private fun SQLiteConnection.queryText(sql: String): String {
        val stmt = prepare(sql)
        try {
            check(stmt.step()) { "La query no devolvio ninguna fila: $sql" }
            return stmt.getText(0)
        } finally {
            stmt.close()
        }
    }

    private fun SQLiteConnection.queryTextOrNull(sql: String): String? {
        val stmt = prepare(sql)
        try {
            check(stmt.step()) { "La query no devolvio ninguna fila: $sql" }
            return if (stmt.isNull(0)) null else stmt.getText(0)
        } finally {
            stmt.close()
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun assertValidUuid(value: String, label: String) {
        val parsed = runCatching { Uuid.parse(value) }
        assertTrue(parsed.isSuccess, "$label no es un UUID valido: $value")
    }

    @Test
    fun migration3to4_migratesDataCorrectly() {
        val connection = BundledSQLiteDriver().open(":memory:")
        try {
            createV3Schema(connection)
            seedV3Data(connection)

            MIGRATION_3_4.migrate(connection)

            // --- conteos: no se perdio ni se duplico ninguna fila ---
            assertEquals(2, connection.queryCount("SELECT COUNT(*) FROM decks"))
            assertEquals(2, connection.queryCount("SELECT COUNT(*) FROM topics"))
            assertEquals(5, connection.queryCount("SELECT COUNT(*) FROM flashcards"))
            assertEquals(0, connection.queryCount("SELECT COUNT(*) FROM reviewlog"))

            // --- UUIDs de decks/topics, y remapeo correcto por identidad de fila ---
            val deckAId = connection.queryText("SELECT id FROM decks WHERE name = 'Deck A'")
            val deckBId = connection.queryText("SELECT id FROM decks WHERE name = 'Deck B'")
            assertValidUuid(deckAId, "id de Deck A")
            assertValidUuid(deckBId, "id de Deck B")
            assertTrue(deckAId != deckBId)

            val topicA1Id = connection.queryText("SELECT id FROM topics WHERE name = 'Topic A1'")
            val topicB1Id = connection.queryText("SELECT id FROM topics WHERE name = 'Topic B1'")
            assertValidUuid(topicA1Id, "id de Topic A1")
            assertValidUuid(topicB1Id, "id de Topic B1")
            assertEquals(deckAId, connection.queryText("SELECT deckId FROM topics WHERE name = 'Topic A1'"))
            assertEquals(deckBId, connection.queryText("SELECT deckId FROM topics WHERE name = 'Topic B1'"))

            // Q1 y Q5 tenian deckId=1/topicId=10 -> deben apuntar a Deck A / Topic A1
            for (question in listOf("Q1", "Q5")) {
                val fcDeckId = connection.queryText("SELECT deckId FROM flashcards WHERE question = '$question'")
                val fcTopicId = connection.queryTextOrNull("SELECT topicId FROM flashcards WHERE question = '$question'")
                assertEquals(deckAId, fcDeckId, "$question deberia apuntar a Deck A")
                assertEquals(topicA1Id, fcTopicId, "$question deberia apuntar a Topic A1")
            }

            // Q3 tenia deckId=2/topicId=20 -> Deck B / Topic B1
            assertEquals(deckBId, connection.queryText("SELECT deckId FROM flashcards WHERE question = 'Q3'"))
            assertEquals(topicB1Id, connection.queryTextOrNull("SELECT topicId FROM flashcards WHERE question = 'Q3'"))

            // Q2 (deckId=1, sin topic) y Q4 (deckId=2, sin topic): topicId debe seguir NULL
            assertEquals(deckAId, connection.queryText("SELECT deckId FROM flashcards WHERE question = 'Q2'"))
            assertNull(connection.queryTextOrNull("SELECT topicId FROM flashcards WHERE question = 'Q2'"), "Q2 no tenia topic, debe seguir NULL")

            assertEquals(deckBId, connection.queryText("SELECT deckId FROM flashcards WHERE question = 'Q4'"))
            assertNull(connection.queryTextOrNull("SELECT topicId FROM flashcards WHERE question = 'Q4'"), "Q4 no tenia topic, debe seguir NULL")

            // --- campos de sync a NULL y cardType/options por defecto en TODAS las filas ---
            assertEquals(
                0,
                connection.queryCount("SELECT COUNT(*) FROM decks WHERE deletedAt IS NOT NULL OR userId IS NOT NULL OR lastSyncedAt IS NOT NULL")
            )
            assertEquals(
                0,
                connection.queryCount("SELECT COUNT(*) FROM topics WHERE deletedAt IS NOT NULL OR userId IS NOT NULL OR lastSyncedAt IS NOT NULL")
            )
            assertEquals(
                0,
                connection.queryCount(
                    "SELECT COUNT(*) FROM flashcards WHERE deletedAt IS NOT NULL OR userId IS NOT NULL " +
                        "OR lastSyncedAt IS NOT NULL OR options IS NOT NULL OR correctOptionIndex IS NOT NULL"
                )
            )
            assertEquals(0, connection.queryCount("SELECT COUNT(*) FROM flashcards WHERE cardType != 'FLIP'"))

            // --- indices recreados tras el DROP+RENAME ---
            val expectedIndices = setOf("index_topics_deckId", "index_flashcards_deckId", "index_flashcards_topicId")
            val actualIndices = mutableSetOf<String>()
            val idxStmt = connection.prepare(
                "SELECT name FROM sqlite_master WHERE type = 'index' AND name IN " +
                    "('index_topics_deckId', 'index_flashcards_deckId', 'index_flashcards_topicId')"
            )
            try {
                while (idxStmt.step()) {
                    actualIndices.add(idxStmt.getText(0))
                }
            } finally {
                idxStmt.close()
            }
            assertEquals(expectedIndices, actualIndices)

            // --- red de seguridad generica: integridad referencial tras el remapeo ---
            val fkCheckStmt = connection.prepare("PRAGMA foreign_key_check")
            try {
                assertFalse(fkCheckStmt.step(), "PRAGMA foreign_key_check encontro violaciones de integridad referencial")
            } finally {
                fkCheckStmt.close()
            }
        } finally {
            connection.close()
        }
    }
}
