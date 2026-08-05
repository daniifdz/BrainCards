package org.braincards.app.data.local

import androidx.room.TypeConverter
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import org.braincards.app.domain.models.CardType


class Converters {

    //cada conversion necesita dos funciones, en parejam ambas marcadas con
    //@TypeConverter

    @TypeConverter //esta convierte de la BD a kotlin, se usa cuando Room lee una fila
    fun fromTimestamp(value: Long?): Instant? {
        return value?.let { Instant.fromEpochMilliseconds(it) }
    }

    @TypeConverter //esta de kotlin a BD, se usa cuando room escribe una fila
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

    //Recibe lo que viene guardado en la base de datos "String" y devuelve el cardtype.
    //el nombre da igual solo mira los tipos entrada/salida
    @TypeConverter
    fun fromCardTypeString(value: String?): CardType? {
        // Si el valor es nulo, devuelve null
        //Busca en el enum CardType el valor cuyo nombre coincide exactamente con el texto (it).
        //Si guardaste "FLIP", te devuelve CardType.FLIP.
        return value?.let { CardType.valueOf(it) }
    }

    //
    @TypeConverter
    fun cardTypeToString(cardType: CardType?): String? {
        return cardType?.name
    }



}