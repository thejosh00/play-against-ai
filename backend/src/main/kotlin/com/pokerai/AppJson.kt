package com.pokerai

import kotlinx.serialization.json.Json

val appJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}
