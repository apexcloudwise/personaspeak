package biz.pixelperfectstudios.personaspeak.ui.personas

import biz.pixelperfectstudios.personaspeak.personas.ValidatedPersona

val ValidatedPersona.emoji: String
    get() = when (id.value) {
        "bundled:jeeves" -> "🎩"
        "bundled:sir-humphrey" -> "🏛️"
        "bundled:dr-schultz" -> "🎯"
        "bundled:amitabh-bachchan" -> "🎬"
        else -> "🎭"
    }

val ValidatedPersona.descriptor: String
    get() = when (id.value) {
        "bundled:jeeves" -> "The impeccable valet"
        "bundled:sir-humphrey" -> "Will neither confirm nor deny"
        "bundled:dr-schultz" -> "The eloquent bounty hunter"
        "bundled:amitabh-bachchan" -> "Larger-than-life cinema presence"
        else -> {
            val raw = content.context.trim()
            raw.removePrefix("(").removeSuffix(")").trim().ifEmpty { "Persona" }
        }
    }
