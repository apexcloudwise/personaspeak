package biz.pixelperfectstudios.personaspeak.providers

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NoSecretLoggingTest {

    @Test
    fun noSecretLoggingCallsInProvidersModule() {
        val srcDir = File("src/main/kotlin")
        if (!srcDir.exists()) return // skip if run from different working dir

        val forbiddenKeywords = listOf("secret", "x-api-key", "bearer", "authorization")
        val kotlinFiles = srcDir.walkTopDown().filter { it.extension == "kt" }.toList()

        for (file in kotlinFiles) {
            val lines = file.readLines()
            for ((index, line) in lines.withIndex()) {
                val lower = line.lowercase()
                if (lower.contains("log.") || lower.contains("println")) {
                    for (keyword in forbiddenKeywords) {
                        assertTrue(
                            "Found forbidden keyword '$keyword' in log statement at ${file.name}:${index + 1}: $line",
                            !lower.contains(keyword)
                        )
                    }
                }
            }
        }
    }
}
