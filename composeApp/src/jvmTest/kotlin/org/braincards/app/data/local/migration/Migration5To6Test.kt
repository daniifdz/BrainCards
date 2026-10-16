package org.braincards.app.data.local.migration

import androidx.room.Room
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.braincards.app.data.local.AppDatabase
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Migracion 5->6 (columnas de sync en card_scheduling). El esquema v5 NO se copia a mano:
 * se construye leyendo composeApp/schemas/.../5.json, y el esquema esperado tras migrar
 * se lee de 6.json (generado por KSP desde las entidades), asi que el test no puede
 * divergir de lo que Room espera de verdad.
 */
class Migration5To6Test {

    private val schemaDir: File = listOf(
        File("schemas/org.braincards.app.data.local.AppDatabase"),
        File("composeApp/schemas/org.braincards.app.data.local.AppDatabase")
    ).firstOrNull { it.isDirectory }
        ?: error("No encuentro composeApp/schemas/... desde ${File(".").absolutePath}")

    private fun entities(version: Int): List<JsonObject> {
        val text = File(schemaDir, "$version.json").readText()
        return Json.parseToJsonElement(text).jsonObject.getValue("database").jsonObject
            .getValue("entities").jsonArray.map { it.jsonObject }
    }

    private fun setupQueries(version: Int): List<String> {
        val text = File(schemaDir, "$version.json").readText()
        return Json.parseToJsonElement(text).jsonObject.getValue("database").jsonObject
            .getValue("setupQueries").jsonArray.map { it.jsonPrimitive.content }
    }

    /** Crea el esquema (tablas, indices y room_master_table) de la version dada leyendo su JSON. */
    private fun createSchemaFromJson(connection: SQLiteConnection, version: Int) {
        for (entity in entities(version)) {
            val table = entity.getValue("tableName").jsonPrimitive.content
            connection.execSQL(entity.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            entity["indices"]?.jsonArray?.forEach { index ->
                connection.execSQL(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            }
        }
        setupQueries(version).forEach { connection.execSQL(it) }
        connection.execSQL("PRAGMA user_version = $version")
    }

    private fun seedV5Data(connection: SQLiteConnection) {
        connection.execSQL("INSERT INTO decks (id, name, createdAt, updatedAt, description) VALUES ('deck-1', 'Deck A', 1000, 1000, NULL)")
        for (n in 1..3) {
            connection.execSQL(
                "INSERT INTO flashcards (id, deckId, topicId, question, answer, cardType, createdAt, updatedAt) " +
                    "VALUES ('card-$n', 'deck-1', NULL, 'Q$n', 'A$n', 'FLIP', 1100, 1100)"
            )
        }
        // Valores distintos entre filas (incl. EF decimal y nextReview != updatedAt).
        connection.execSQL("INSERT INTO card_scheduling VALUES ('card-1', 2.6, 15, 3, 1900, 1250)")
        connection.execSQL("INSERT INTO card_scheduling VALUES ('card-2', 1.3, 0, 0, 1300, 1301)")
        connection.execSQL("INSERT INTO card_scheduling VALUES ('card-3', 2.36, 6, 2, 8000, 7000)")
    }

    private fun SQLiteConnection.queryCount(sql: String): Long {
        val stmt = prepare(sql)
        try {
            stmt.step()
            return stmt.getLong(0)
        } finally {
            stmt.close()
        }
    }

    private data class ColumnInfo(val name: String, val type: String, val notNull: Boolean, val pk: Boolean)

    private fun SQLiteConnection.columns(table: String): Set<ColumnInfo> {
        val stmt = prepare("PRAGMA table_info(`$table`)")
        val result = mutableSetOf<ColumnInfo>()
        try {
            while (stmt.step()) {
                result += ColumnInfo(
                    name = stmt.getText(1),
                    type = stmt.getText(2),
                    notNull = stmt.getLong(3) != 0L,
                    pk = stmt.getLong(5) != 0L
                )
            }
        } finally {
            stmt.close()
        }
        return result
    }

    private fun SQLiteConnection.foreignKeys(table: String): Set<String> {
        val stmt = prepare("PRAGMA foreign_key_list(`$table`)")
        val result = mutableSetOf<String>()
        try {
            while (stmt.step()) {
                result += "${stmt.getText(3)}->${stmt.getText(2)}.${stmt.getText(4)}"
            }
        } finally {
            stmt.close()
        }
        return result
    }

    private fun SQLiteConnection.indexNames(table: String): Set<String> {
        val stmt = prepare("PRAGMA index_list(`$table`)")
        val result = mutableSetOf<String>()
        try {
            while (stmt.step()) {
                val name = stmt.getText(1)
                if (!name.startsWith("sqlite_autoindex")) result += name
            }
        } finally {
            stmt.close()
        }
        return result
    }

    private fun SQLiteConnection.userTables(): Set<String> {
        val stmt = prepare("SELECT name FROM sqlite_master WHERE type = 'table'")
        val result = mutableSetOf<String>()
        try {
            while (stmt.step()) {
                val name = stmt.getText(0)
                if (name != "room_master_table" && name != "android_metadata" && !name.startsWith("sqlite_")) result += name
            }
        } finally {
            stmt.close()
        }
        return result
    }

    @Test
    fun migration5to6_conserva_los_datos_y_deja_las_columnas_nuevas_en_NULL() {
        val connection = BundledSQLiteDriver().open(":memory:")
        try {
            createSchemaFromJson(connection, 5)
            seedV5Data(connection)

            MIGRATION_5_6.migrate(connection)

            assertEquals(3, connection.queryCount("SELECT COUNT(*) FROM card_scheduling"))

            val expected = mapOf(
                "card-1" to listOf(2.6, 15.0, 3.0, 1900.0, 1250.0),
                "card-2" to listOf(1.3, 0.0, 0.0, 1300.0, 1301.0),
                "card-3" to listOf(2.36, 6.0, 2.0, 8000.0, 7000.0)
            )
            for ((cardId, values) in expected) {
                val stmt = connection.prepare(
                    "SELECT easeFactor, interval, repetitions, nextReview, updatedAt, userId, lastSyncedAt " +
                        "FROM card_scheduling WHERE cardId = '$cardId'"
                )
                try {
                    assertTrue(stmt.step(), "$cardId deberia seguir existiendo")
                    assertEquals(values[0], stmt.getDouble(0))
                    assertEquals(values[1].toInt(), stmt.getLong(1).toInt())
                    assertEquals(values[2].toInt(), stmt.getLong(2).toInt())
                    assertEquals(values[3].toLong(), stmt.getLong(3))
                    assertEquals(values[4].toLong(), stmt.getLong(4))
                    assertTrue(stmt.isNull(5), "$cardId: userId debe quedar en NULL")
                    assertTrue(stmt.isNull(6), "$cardId: lastSyncedAt debe quedar en NULL")
                } finally {
                    stmt.close()
                }
            }

            // El resto de tablas no se toca.
            assertEquals(1, connection.queryCount("SELECT COUNT(*) FROM decks"))
            assertEquals(3, connection.queryCount("SELECT COUNT(*) FROM flashcards"))

            assertEquals(
                setOf("cardId", "easeFactor", "interval", "repetitions", "nextReview", "updatedAt", "userId", "lastSyncedAt"),
                connection.columns("card_scheduling").map { it.name }.toSet()
            )
        } finally {
            connection.close()
        }
    }

    @Test
    fun migration5to6_deja_un_esquema_identico_al_de_6_json() {
        val connection = BundledSQLiteDriver().open(":memory:")
        try {
            createSchemaFromJson(connection, 5)
            MIGRATION_5_6.migrate(connection)

            val expectedEntities = entities(6)
            assertEquals(
                expectedEntities.map { it.getValue("tableName").jsonPrimitive.content }.toSet(),
                connection.userTables(),
                "las tablas tras migrar deben ser exactamente las de 6.json"
            )

            for (entity in expectedEntities) {
                val table = entity.getValue("tableName").jsonPrimitive.content
                val primaryKey = entity.getValue("primaryKey").jsonObject.getValue("columnNames").jsonArray
                    .map { it.jsonPrimitive.content }.toSet()

                val expectedColumns = entity.getValue("fields").jsonArray.map { it.jsonObject }.map { field ->
                    val name = field.getValue("columnName").jsonPrimitive.content
                    ColumnInfo(
                        name = name,
                        type = field.getValue("affinity").jsonPrimitive.content,
                        // Room omite "notNull" del JSON cuando la columna es nullable.
                        notNull = field["notNull"]?.jsonPrimitive?.boolean ?: false,
                        pk = name in primaryKey
                    )
                }.toSet()
                assertEquals(expectedColumns, connection.columns(table), "columnas de $table")

                val expectedForeignKeys = (entity["foreignKeys"]?.jsonArray ?: emptyList<kotlinx.serialization.json.JsonElement>())
                    .map { it.jsonObject }
                    .map { fk ->
                        val from = fk.getValue("columns").jsonArray.single().jsonPrimitive.content
                        val to = fk.getValue("referencedColumns").jsonArray.single().jsonPrimitive.content
                        "$from->${fk.getValue("table").jsonPrimitive.content}.$to"
                    }.toSet()
                assertEquals(expectedForeignKeys, connection.foreignKeys(table), "foreign keys de $table")

                val expectedIndices = (entity["indices"]?.jsonArray ?: emptyList<kotlinx.serialization.json.JsonElement>())
                    .map { it.jsonObject.getValue("name").jsonPrimitive.content }.toSet()
                assertEquals(expectedIndices, connection.indexNames(table), "indices de $table")
            }
        } finally {
            connection.close()
        }
    }

    /**
     * Prueba con Room de verdad: BD en fichero con el esquema v5 (y user_version = 5), abierta
     * con la base de datos real y solo MIGRATION_5_6. Si el esquema migrado no coincidiera con
     * las entidades, Room lanzaria IllegalStateException al validarlo.
     */
    @Test
    fun room_abre_una_bd_v5_con_solo_MIGRATION_5_6_y_valida_el_esquema() {
        val file = File.createTempFile("braincards-v5-", ".db")
        try {
            val connection = BundledSQLiteDriver().open(file.absolutePath)
            try {
                createSchemaFromJson(connection, 5)
                seedV5Data(connection)
            } finally {
                connection.close()
            }

            val db = Room.databaseBuilder<AppDatabase>(name = file.absolutePath)
                .setDriver(BundledSQLiteDriver())
                .addMigrations(MIGRATION_5_6)
                .build()
            try {
                runBlocking {
                    val scheduling = db.cardSchedulingDao().getByCardId("card-3")
                    assertNotNull(scheduling)
                    assertEquals(2.36, scheduling.easeFactor)
                    assertEquals(6, scheduling.interval)
                    assertEquals(2, scheduling.repetitions)
                    assertEquals(null, scheduling.userId)
                    assertEquals(null, scheduling.lastSyncedAt)
                }
            } finally {
                db.close()
            }
        } finally {
            file.delete()
            File(file.absolutePath + "-wal").delete()
            File(file.absolutePath + "-shm").delete()
        }
    }

    @Test
    fun la_migracion_no_toca_las_tablas_que_no_son_card_scheduling() {
        val connection = BundledSQLiteDriver().open(":memory:")
        try {
            createSchemaFromJson(connection, 5)
            val before = connection.columns("flashcards")

            MIGRATION_5_6.migrate(connection)

            assertEquals(before, connection.columns("flashcards"))
            assertFalse(connection.columns("card_scheduling").isEmpty())
        } finally {
            connection.close()
        }
    }
}
