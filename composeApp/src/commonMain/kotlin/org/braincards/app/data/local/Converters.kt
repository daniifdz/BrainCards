package org.braincards.app.data.local

import androidx.room.TypeConverter
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import org.braincards.app.domain.models.CardType


class Converters {

    // Cada conversión necesita dos funciones, en pareja, ambas marcadas con @TypeConverter.

    // BD -> Kotlin: Room lo usa al leer una fila.
    @TypeConverter
    fun fromTimestamp(value: Long?): Instant? {
        return value?.let { Instant.fromEpochMilliseconds(it) }
    }

    // Kotlin -> BD: Room lo usa al escribir una fila.
    @TypeConverter
    fun dateToTimestamp(date: Instant?): Long? {
        return date?.toEpochMilliseconds()
    }

    @TypeConverter
    fun fromOptionsJson(value: String?): List<String>? {
        return value?.let { Json.decodeFromString<List<String>>(it) }
    }

    @TypeConverter
    fun optionsToJson(options: List<String>?): String? {
        return options?.let { Json.encodeToString(it) }
    }

    // Recibe el String guardado en la base de datos y devuelve el CardType. El nombre da igual: Room solo mira los tipos de
    // entrada y salida.
    @TypeConverter
    fun fromCardTypeString(value: String?): CardType? {
        // Nulo -> null; si no, busca el valor del enum CardType cuyo nombre coincide exactamente con el texto (p. ej. «FLIP» da
        // CardType.FLIP).
        return value?.let { CardType.valueOf(it) }
    }

    //
    @TypeConverter
    fun cardTypeToString(cardType: CardType?): String? {
        return cardType?.name
    }



}