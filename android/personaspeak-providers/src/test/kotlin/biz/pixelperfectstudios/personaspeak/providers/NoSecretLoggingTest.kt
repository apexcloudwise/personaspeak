package biz.pixelperfectstudios.personaspeak.providers

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class NoSecretLoggingTest {

    @Test
    fun noSecretLoggingCallsInProvidersModule() {
        val candidates = listOf(
            File("src/main/kotlin"),
            File("android/personaspeak-providers/src/main/kotlin"),
            File("personaspeak-providers/src/main/kotlin"),
            File("../personaspeak-providers/src/main/kotlin")
        )
        val srcDir = candidates.firstOrNull { it.isDirectory }
            ?: fail("Could not locate personaspeak-providers source directory; checked: $candidates")

        val forbiddenKeywords = listOf("secret", "x-api-key", "bearer", "authorization", "credential", "token", "password")
        val kotlinFiles = (srcDir as File).walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue("No kotlin files found in $srcDir", kotlinFiles.isNotEmpty())

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
