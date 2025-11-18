package org.braincards.app

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform