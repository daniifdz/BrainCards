package org.braincards.app.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import org.braincards.app.domain.util.IdGenerator

/**
 * Esquema real de la v3 (antes de esta migración): PKs `INTEGER PRIMARY KEY AUTOINCREMENT`,
 * sin deletedAt/userId/lastSyncedAt/cardType/options/correctOptionIndex.
 * El `3.json` de composeApp/schemas/ NO refleja esto (quedó sobrescrito con el esquema
 * nuevo durante el desarrollo) — la fuente de verdad de la v3 real es el commit `ddd5ef3`.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {

        //En los pasos 1-3
        // se recorren las filas viejas y por cada fila genera un UUID nuevo con el idgGenerator,
        // y lo guarda en un map

        // Paso 1: decks viejos -> UUID nuevo
        val deckIdMap = mutableMapOf<Long, String>()
        val decksReadStmt = connection.prepare("SELECT id, name, createdAt, updatedAt, description FROM decks")
        try {
            while (decksReadStmt.step()) {
                val oldId = decksReadStmt.getLong(0)
                deckIdMap[oldId] = IdGenerator.newId()
            }
        } finally {
            decksReadStmt.close()
        }

        // Paso 2: topics viejos -> UUID nuevo, remapeando deckId
        val topicIdMap = mutableMapOf<Long, String>()
        val topicsReadStmt = connection.prepare("SELECT id, deckId, name, createdAt, updatedAt FROM topics")
        try {
            while (topicsReadStmt.step()) {
                val oldId = topicsReadStmt.getLong(0)
                topicIdMap[oldId] = IdGenerator.newId()
            }
        } finally {
            topicsReadStmt.close()
        }

        // Paso 3: flashcards viejas -> UUID nuevo (solo se generan los ids aqui;
        // deckId/topicId se remapean con los mapas de arriba en el paso 5)
        val flashcardIdMap = mutableMapOf<Long, String>()
        val flashcardsIdReadStmt = connection.prepare("SELECT id FROM flashcards")
        try {
            while (flashcardsIdReadStmt.step()) {
                val oldId = flashcardsIdReadStmt.getLong(0)
                flashcardIdMap[oldId] = IdGenerator.newId()
            }
        } finally {
            flashcardsIdReadStmt.close()
        }

        // Paso 4: tablas _new con el SQL exacto de composeApp/schemas/.../4.json

        //se crean nuevas tablas en paralelo

        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `decks_new` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `description` TEXT, " +
                "`deletedAt` INTEGER, `userId` TEXT, `lastSyncedAt` INTEGER, PRIMARY KEY(`id`))"
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `topics_new` (`id` TEXT NOT NULL, `deckId` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                "`deletedAt` INTEGER, `lastSyncedAt` INTEGER, `userId` TEXT, PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`deckId`) REFERENCES `decks`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )"
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `flashcards_new` (`id` TEXT NOT NULL, `deckId` TEXT NOT NULL, " +
                "`topicId` TEXT, `question` TEXT NOT NULL, `answer` TEXT NOT NULL, `easeFactor` REAL NOT NULL, " +
                "`interval` INTEGER NOT NULL, `repetitions` INTEGER NOT NULL, `cardType` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `nextReview` INTEGER NOT NULL, " +
                "`deletedAt` INTEGER, `lastSyncedAt` INTEGER, `userId` TEXT, `options` TEXT, " +
                "`correctOptionIndex` INTEGER, PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`deckId`) REFERENCES `decks`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , " +
                "FOREIGN KEY(`topicId`) REFERENCES `topics`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )"
        )

        // Paso 5: insertar filas remapeadas en las _new

        //para cada tabla se abre un read y un insert, y por cada flipa que lee
        //traduce los IDs usando los mapas de antes


        val decksRead = connection.prepare(
            "SELECT id, name, createdAt, updatedAt, description FROM decks"
        )
        val decksInsert = connection.prepare(
            "INSERT INTO decks_new (id, name, createdAt, updatedAt, description, deletedAt, userId, lastSyncedAt) " +
                "VALUES (?, ?, ?, ?, ?, NULL, NULL, NULL)"
        )
        try {
            while (decksRead.step()) {
                val oldId = decksRead.getLong(0)
                decksInsert.bindText(1, deckIdMap.getValue(oldId))
                decksInsert.bindText(2, decksRead.getText(1))
                decksInsert.bindLong(3, decksRead.getLong(2))
                decksInsert.bindLong(4, decksRead.getLong(3))
                if (decksRead.isNull(4)) decksInsert.bindNull(5) else decksInsert.bindText(5, decksRead.getText(4))
                decksInsert.step()
                decksInsert.reset()
            }
        } finally {
            decksInsert.close()
            decksRead.close()
        }

        val topicsRead = connection.prepare(
            "SELECT id, deckId, name, createdAt, updatedAt FROM topics"
        )
        val topicsInsert = connection.prepare(
            "INSERT INTO topics_new (id, deckId, name, createdAt, updatedAt, deletedAt, lastSyncedAt, userId) " +
                "VALUES (?, ?, ?, ?, ?, NULL, NULL, NULL)"
        )
        try {
            while (topicsRead.step()) {
                val oldId = topicsRead.getLong(0)
                val oldDeckId = topicsRead.getLong(1)
                topicsInsert.bindText(1, topicIdMap.getValue(oldId))
                topicsInsert.bindText(2, deckIdMap.getValue(oldDeckId))
                topicsInsert.bindText(3, topicsRead.getText(2))
                topicsInsert.bindLong(4, topicsRead.getLong(3))
                topicsInsert.bindLong(5, topicsRead.getLong(4))
                topicsInsert.step()
                topicsInsert.reset()
            }
        } finally {
            topicsInsert.close()
            topicsRead.close()
        }

        val flashcardsRead = connection.prepare(
            "SELECT id, deckId, topicId, question, answer, easeFactor, interval, repetitions, " +
                "createdAt, updatedAt, nextReview FROM flashcards"
        )
        val flashcardsInsert = connection.prepare(
            "INSERT INTO flashcards_new (id, deckId, topicId, question, answer, easeFactor, interval, " +
                "repetitions, cardType, createdAt, updatedAt, nextReview, deletedAt, lastSyncedAt, userId, " +
                "options, correctOptionIndex) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'FLIP', ?, ?, ?, NULL, NULL, NULL, NULL, NULL)"
        )
        try {
            while (flashcardsRead.step()) {
                val oldId = flashcardsRead.getLong(0)
                val oldDeckId = flashcardsRead.getLong(1)

                flashcardsInsert.bindText(1, flashcardIdMap.getValue(oldId))
                flashcardsInsert.bindText(2, deckIdMap.getValue(oldDeckId))

                // topicId es nullable en la v3 real: si viene NULL, se bindea NULL,
                // nunca un string vacio ni "null"
                if (flashcardsRead.isNull(2)) {
                    flashcardsInsert.bindNull(3)
                } else {
                    val oldTopicId = flashcardsRead.getLong(2)
                    flashcardsInsert.bindText(3, topicIdMap.getValue(oldTopicId))
                }

                flashcardsInsert.bindText(4, flashcardsRead.getText(3))
                flashcardsInsert.bindText(5, flashcardsRead.getText(4))
                flashcardsInsert.bindDouble(6, flashcardsRead.getDouble(5))
                flashcardsInsert.bindLong(7, flashcardsRead.getLong(6))
                flashcardsInsert.bindLong(8, flashcardsRead.getLong(7))
                flashcardsInsert.bindLong(9, flashcardsRead.getLong(8))
                flashcardsInsert.bindLong(10, flashcardsRead.getLong(9))
                flashcardsInsert.bindLong(11, flashcardsRead.getLong(10))
                flashcardsInsert.step()
                flashcardsInsert.reset()
            }
        } finally {
            flashcardsInsert.close()
            flashcardsRead.close()
        }

        // Paso 6: fuera las viejas, dentro las nuevas con su nombre definitivo,
        // y recrear los indices (el RENAME no los conserva)

        //borrado de tablas viejas y se renombran las nuevas sin datos que migrar.


        connection.execSQL("DROP TABLE decks")
        connection.execSQL("DROP TABLE topics")
        connection.execSQL("DROP TABLE flashcards")

        connection.execSQL("ALTER TABLE decks_new RENAME TO decks")
        connection.execSQL("ALTER TABLE topics_new RENAME TO topics")
        connection.execSQL("ALTER TABLE flashcards_new RENAME TO flashcards")

        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_topics_deckId` ON `topics` (`deckId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_flashcards_deckId` ON `flashcards` (`deckId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_flashcards_topicId` ON `flashcards` (`topicId`)")

        // Paso 7: reviewlog es tabla nueva, sin datos que migrar
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `reviewlog` (`id` TEXT NOT NULL, `cardId` TEXT NOT NULL, " +
                "`timestamp` INTEGER NOT NULL, `grade` INTEGER NOT NULL, `previousInterval` INTEGER NOT NULL, " +
                "`newInterval` INTEGER NOT NULL, `userId` TEXT, PRIMARY KEY(`id`))"
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_reviewlog_cardId` ON `reviewlog` (`cardId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_reviewlog_userId` ON `reviewlog` (`userId`)")
    }
}

/**
 * v4 -> v5: separa el scheduling del SM-2 (easeFactor, interval, repetitions, nextReview)
 * de flashcards a su propia tabla card_scheduling. A diferencia de la 3->4, aqui no hay que
 * remapear ningun id -- los cardId ya son UUID desde la migracion anterior, es solo mover
 * columnas de sitio. SQL de las tablas copiado literal de composeApp/schemas/.../5.json.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(connection: SQLiteConnection) {

        // Paso 1: card_scheduling nueva
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `card_scheduling` (`cardId` TEXT NOT NULL, " +
                "`easeFactor` REAL NOT NULL, `interval` INTEGER NOT NULL, " +
                "`repetitions` INTEGER NOT NULL, `nextReview` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`cardId`), " +
                "FOREIGN KEY(`cardId`) REFERENCES `flashcards`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )"
        )

        // Paso 2: volcar el scheduling de cada flashcard vieja a card_scheduling,
        // usando el id de la flashcard como cardId y su updatedAt como updatedAt inicial.
        // Tiene que pasar antes de tocar la tabla flashcards vieja -- es la unica fuente.
        val flashcardsRead = connection.prepare(
            "SELECT id, easeFactor, interval, repetitions, nextReview, updatedAt FROM flashcards"
        )
        val schedulingInsert = connection.prepare(
            "INSERT INTO card_scheduling (cardId, easeFactor, interval, repetitions, nextReview, updatedAt) " +
                "VALUES (?, ?, ?, ?, ?, ?)"
        )
        try {
            while (flashcardsRead.step()) {
                schedulingInsert.bindText(1, flashcardsRead.getText(0))
                schedulingInsert.bindDouble(2, flashcardsRead.getDouble(1))
                schedulingInsert.bindLong(3, flashcardsRead.getLong(2))
                schedulingInsert.bindLong(4, flashcardsRead.getLong(3))
                schedulingInsert.bindLong(5, flashcardsRead.getLong(4))
                schedulingInsert.bindLong(6, flashcardsRead.getLong(5))
                schedulingInsert.step()
                schedulingInsert.reset()
            }
        } finally {
            schedulingInsert.close()
            flashcardsRead.close()
        }

        // Paso 3: flashcards_new con el esquema reducido (sin las 4 columnas de scheduling)
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `flashcards_new` (`id` TEXT NOT NULL, `deckId` TEXT NOT NULL, " +
                "`topicId` TEXT, `question` TEXT NOT NULL, `answer` TEXT NOT NULL, `cardType` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, " +
                "`lastSyncedAt` INTEGER, `userId` TEXT, `options` TEXT, `correctOptionIndex` INTEGER, " +
                "PRIMARY KEY(`id`), FOREIGN KEY(`deckId`) REFERENCES `decks`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , " +
                "FOREIGN KEY(`topicId`) REFERENCES `topics`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )"
        )

        // Paso 4: copiar el resto de columnas (todo menos las 4 de scheduling) a flashcards_new
        val oldRead = connection.prepare(
            "SELECT id, deckId, topicId, question, answer, cardType, createdAt, updatedAt, " +
                "deletedAt, lastSyncedAt, userId, options, correctOptionIndex FROM flashcards"
        )
        val newInsert = connection.prepare(
            "INSERT INTO flashcards_new (id, deckId, topicId, question, answer, cardType, createdAt, " +
                "updatedAt, deletedAt, lastSyncedAt, userId, options, correctOptionIndex) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        )
        try {
            while (oldRead.step()) {
                newInsert.bindText(1, oldRead.getText(0))
                newInsert.bindText(2, oldRead.getText(1))
                if (oldRead.isNull(2)) newInsert.bindNull(3) else newInsert.bindText(3, oldRead.getText(2))
                newInsert.bindText(4, oldRead.getText(3))
                newInsert.bindText(5, oldRead.getText(4))
                newInsert.bindText(6, oldRead.getText(5))
                newInsert.bindLong(7, oldRead.getLong(6))
                newInsert.bindLong(8, oldRead.getLong(7))
                if (oldRead.isNull(8)) newInsert.bindNull(9) else newInsert.bindLong(9, oldRead.getLong(8))
                if (oldRead.isNull(9)) newInsert.bindNull(10) else newInsert.bindLong(10, oldRead.getLong(9))
                if (oldRead.isNull(10)) newInsert.bindNull(11) else newInsert.bindText(11, oldRead.getText(10))
                if (oldRead.isNull(11)) newInsert.bindNull(12) else newInsert.bindText(12, oldRead.getText(11))
                if (oldRead.isNull(12)) newInsert.bindNull(13) else newInsert.bindLong(13, oldRead.getLong(12))
                newInsert.step()
                newInsert.reset()
            }
        } finally {
            newInsert.close()
            oldRead.close()
        }

        // Paso 5: fuera la vieja, dentro la nueva con su nombre definitivo, recrear indices
        connection.execSQL("DROP TABLE flashcards")
        connection.execSQL("ALTER TABLE flashcards_new RENAME TO flashcards")

        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_flashcards_deckId` ON `flashcards` (`deckId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_flashcards_topicId` ON `flashcards` (`topicId`)")
    }
}

/**
 * v5 -> v6: columnas de sync en card_scheduling (Sprint 12), mismas que ya tienen decks,
 * topics y flashcards. A diferencia de la 3->4 y la 4->5, aqui NO hay que reconstruir la
 * tabla ni copiar datos: ADD COLUMN de columnas nullables basta, y las filas existentes
 * quedan con userId = NULL (las adopta el backfill) y lastSyncedAt = NULL (pendientes de
 * subir). SQLite solo admite una columna por ALTER TABLE ... ADD COLUMN, de ahi las dos
 * sentencias. SQL equivalente a composeApp/schemas/.../6.json.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `card_scheduling` ADD COLUMN `userId` TEXT")
        connection.execSQL("ALTER TABLE `card_scheduling` ADD COLUMN `lastSyncedAt` INTEGER")
    }
}
