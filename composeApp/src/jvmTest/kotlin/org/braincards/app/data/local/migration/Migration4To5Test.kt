package org.braincards.app.data.local.migration

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Test manual (sin MigrationTestHelper) sobre una conexion SQLite en memoria.
 * Siembra el esquema v4 real (cardId ya son UUID/TEXT, con easeFactor/interval/
 * repetitions/nextReview todavia en flashcards), ejecuta MIGRATION_4_5.migrate(...)
 * directamente, y verifica el resultado con aserciones de kotlin.test.
 */
class Migration4To5Test {

    private fun createV4Schema(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE `decks` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `description` TEXT, " +
                "`deletedAt` INTEGER, `userId` TEXT, `lastSyncedAt` INTEGER, PRIMARY KEY(`id`))"
        )
        connection.execSQL(
            "CREATE TABLE `topics` (`id` TEXT NOT NULL, `deckId` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, " +
                "`lastSyncedAt` INTEGER, `userId` TEXT, PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`deckId`) REFERENCES `decks`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )"
        )
        connection.execSQL(
            "CREATE TABLE `flashcards` (`id` TEXT NOT NULL, `deckId` TEXT NOT NULL, `topicId` TEXT, " +
                "`question` TEXT NOT NULL, `answer` TEXT NOT NULL, `easeFactor` REAL NOT NULL, " +
                "`interval` INTEGER NOT NULL, `repetitions` INTEGER NOT NULL, `cardType` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `nextReview` INTEGER NOT NULL, " +
                "`deletedAt` INTEGER, `lastSyncedAt` INTEGER, `userId` TEXT, `options` TEXT, " +
                "`correctOptionIndex` INTEGER, PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`deckId`) REFERENCES `decks`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , " +
                "FOREIGN KEY(`topicId`) REFERENCES `topics`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )"
        )
        connection.execSQL("CREATE INDEX `index_topics_deckId` ON `topics` (`deckId`)")
        connection.execSQL("CREATE INDEX `index_flashcards_deckId` ON `flashcards` (`deckId`)")
        connection.execSQL("CREATE INDEX `index_flashcards_topicId` ON `flashcards` (`topicId`)")
    }

    private fun seedV4Data(connection: SQLiteConnection) {
        connection.execSQL("INSERT INTO decks (id, name, createdAt, updatedAt, description) VALUES ('deck-1', 'Deck A', 1000, 1000, 'desc A')")
        connection.execSQL("INSERT INTO topics (id, deckId, name, createdAt, updatedAt) VALUES ('topic-1', 'deck-1', 'Topic A1', 1100, 1100)")

        // Q1: con topic, valores de scheduling "avanzados"
        connection.execSQL(
            "INSERT INTO flashcards (id, deckId, topicId, question, answer, easeFactor, interval, " +
                "repetitions, cardType, createdAt, updatedAt, nextReview, deletedAt, lastSyncedAt, userId, " +
                "options, correctOptionIndex) VALUES ('card-1', 'deck-1', 'topic-1', 'Q1', 'A1', 2.6, 15, 3, " +
                "'FLIP', 1200, 1250, 1900, NULL, NULL, NULL, NULL, NULL)"
        )
        // Q2: sin topic, tarjeta nueva (valores por defecto tipicos de recien creada)
        connection.execSQL(
            "INSERT INTO flashcards (id, deckId, topicId, question, answer, easeFactor, interval, " +
                "repetitions, cardType, createdAt, updatedAt, nextReview, deletedAt, lastSyncedAt, userId, " +
                "options, correctOptionIndex) VALUES ('card-2', 'deck-1', NULL, 'Q2', 'A2', 2.5, 0, 0, " +
                "'FLIP', 1300, 1300, 1300, NULL, NULL, NULL, NULL, NULL)"
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

    private fun SQLiteConnection.columnNames(table: String): Set<String> {
        val stmt = prepare("PRAGMA table_info($table)")
        val names = mutableSetOf<String>()
        try {
            while (stmt.step()) {
                // PRAGMA table_info: columnas cid(0), name(1), type(2), notnull(3), dflt_value(4), pk(5)
                names.add(stmt.getText(1))
            }
        } finally {
            stmt.close()
        }
        return names
    }

    @Test
    fun migration4to5_mueve_el_scheduling_a_card_scheduling_y_lo_quita_de_flashcards() {
        val connection = BundledSQLiteDriver().open(":memory:")
        try {
            createV4Schema(connection)
            seedV4Data(connection)

            MIGRATION_4_5.migrate(connection)

            // --- card_scheduling tiene una fila por cada flashcard, con los valores correctos ---
            assertEquals(2, connection.queryCount("SELECT COUNT(*) FROM card_scheduling"))

            val schedStmt1 = connection.prepare(
                "SELECT easeFactor, interval, repetitions, nextReview, updatedAt FROM card_scheduling WHERE cardId = 'card-1'"
            )
            try {
                assertTrue(schedStmt1.step(), "card-1 deberia tener fila en card_scheduling")
                assertEquals(2.6, schedStmt1.getDouble(0))
                assertEquals(15, schedStmt1.getLong(1).toInt())
                assertEquals(3, schedStmt1.getLong(2).toInt())
                assertEquals(1900, schedStmt1.getLong(3))
                // updatedAt del scheduling = updatedAt de la flashcard, no uno nuevo generado
                assertEquals(1250, schedStmt1.getLong(4))
            } finally {
                schedStmt1.close()
            }

            val schedStmt2 = connection.prepare(
                "SELECT easeFactor, interval, repetitions, nextReview, updatedAt FROM card_scheduling WHERE cardId = 'card-2'"
            )
            try {
                assertTrue(schedStmt2.step(), "card-2 deberia tener fila en card_scheduling")
                assertEquals(2.5, schedStmt2.getDouble(0))
                assertEquals(0, schedStmt2.getLong(1).toInt())
                assertEquals(0, schedStmt2.getLong(2).toInt())
                assertEquals(1300, schedStmt2.getLong(3))
                assertEquals(1300, schedStmt2.getLong(4))
            } finally {
                schedStmt2.close()
            }

            // --- flashcards conserva las filas y el contenido, pero ya no tiene las 4 columnas ---
            assertEquals(2, connection.queryCount("SELECT COUNT(*) FROM flashcards"))

            val flashcardsColumns = connection.columnNames("flashcards")
            assertFalse(flashcardsColumns.contains("easeFactor"), "flashcards no deberia tener easeFactor")
            assertFalse(flashcardsColumns.contains("interval"), "flashcards no deberia tener interval")
            assertFalse(flashcardsColumns.contains("repetitions"), "flashcards no deberia tener repetitions")
            assertFalse(flashcardsColumns.contains("nextReview"), "flashcards no deberia tener nextReview")
            assertEquals(
                setOf(
                    "id", "deckId", "topicId", "question", "answer", "cardType",
                    "createdAt", "updatedAt", "deletedAt", "lastSyncedAt", "userId",
                    "options", "correctOptionIndex"
                ),
                flashcardsColumns
            )

            val contentStmt = connection.prepare(
                "SELECT question, answer, cardType, topicId FROM flashcards WHERE id = 'card-1'"
            )
            try {
                assertTrue(contentStmt.step())
                assertEquals("Q1", contentStmt.getText(0))
                assertEquals("A1", contentStmt.getText(1))
                assertEquals("FLIP", contentStmt.getText(2))
                assertEquals("topic-1", contentStmt.getText(3))
            } finally {
                contentStmt.close()
            }

            val card2TopicStmt = connection.prepare("SELECT topicId FROM flashcards WHERE id = 'card-2'")
            try {
                assertTrue(card2TopicStmt.step())
                assertTrue(card2TopicStmt.isNull(0), "card-2 no tenia topic, debe seguir NULL")
            } finally {
                card2TopicStmt.close()
            }

            // --- indices recreados tras el DROP+RENAME ---
            val expectedIndices = setOf("index_flashcards_deckId", "index_flashcards_topicId")
            val actualIndices = mutableSetOf<String>()
            val idxStmt = connection.prepare(
                "SELECT name FROM sqlite_master WHERE type = 'index' AND " +
                    "name IN ('index_flashcards_deckId', 'index_flashcards_topicId')"
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
