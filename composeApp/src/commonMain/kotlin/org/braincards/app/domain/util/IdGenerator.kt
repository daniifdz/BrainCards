package org.braincards.app.domain.util

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid


@OptIn(ExperimentalUuidApi::class)
object IdGenerator{ //singleton
    fun newId() : String = Uuid.random().toString()

}