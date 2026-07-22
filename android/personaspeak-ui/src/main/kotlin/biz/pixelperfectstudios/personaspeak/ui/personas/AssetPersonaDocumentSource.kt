package biz.pixelperfectstudios.personaspeak.ui.personas

import android.content.res.AssetManager
import java.io.InputStream

class AssetPersonaDocumentSource(private val assets: AssetManager) : PersonaDocumentSource {

    override fun slugs(): Result<List<String>> = runCatching {
        assets.list("")
            ?.filter { it.endsWith(YAML_SUFFIX) }
            ?.map { it.removeSuffix(YAML_SUFFIX) }
            ?: emptyList()
    }

    override fun open(slug: String): Result<InputStream> = runCatching {
        val known = slugs().getOrThrow()
        require(slug in known) { "unknown persona slug '$slug'" }
        assets.open("$slug$YAML_SUFFIX")
    }

    private companion object {
        const val YAML_SUFFIX = ".yaml"
    }
}
