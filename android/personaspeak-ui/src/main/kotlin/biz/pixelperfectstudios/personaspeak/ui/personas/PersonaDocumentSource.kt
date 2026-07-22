package biz.pixelperfectstudios.personaspeak.ui.personas

import java.io.InputStream

interface PersonaDocumentSource {
    fun slugs(): Result<List<String>>
    fun open(slug: String): Result<InputStream>
}
