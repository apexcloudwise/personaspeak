package biz.pixelperfectstudios.personaspeak.data

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Pins the shipped backup-exclusion rules against the store's real artifact
 * names, so layout drift fails in CI even where no emulator legacy pass runs.
 * Reads the actual rule files from the app module in the repository checkout.
 */
@RunWith(JUnit4::class)
class BackupRuleFilesTest {

    private fun ruleFile(name: String): File {
        // <repo>/android/personaspeak-data -> <repo>/android/personaspeak-ui/...
        val candidates = listOf(
            File("../personaspeak-ui/src/main/res/xml/$name"),
            File("personaspeak-ui/src/main/res/xml/$name"),
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("rule file $name not found from ${File(".").absolutePath}")
    }

    private fun assertExcludes(text: String, label: String) {
        assertTrue("$label: secret blob", text.contains("personaspeak_secret.bin"))
        assertTrue("$label: staging twin", text.contains("personaspeak_secret.bin.staging"))
        assertTrue(
            "$label: datastore metadata",
            text.contains("datastore/personaspeak_provider_config.preferences_pb"),
        )
    }

    @Test
    fun `extraction rules exclude all three artifacts under cloud-backup`() {
        val text = ruleFile("personaspeak_data_extraction_rules.xml").readText()
        assertTrue(text.contains("<cloud-backup>"))
        assertExcludes(text.substringAfter("<cloud-backup>").substringBefore("</cloud-backup>"), "cloud-backup")
    }

    @Test
    fun `extraction rules exclude all three artifacts under device-transfer`() {
        val text = ruleFile("personaspeak_data_extraction_rules.xml").readText()
        assertTrue(text.contains("<device-transfer>"))
        assertExcludes(
            text.substringAfter("<device-transfer>").substringBefore("</device-transfer>"),
            "device-transfer",
        )
    }

    @Test
    fun `legacy full-backup content excludes all three artifacts`() {
        assertExcludes(ruleFile("personaspeak_full_backup_content.xml").readText(), "full-backup")
    }

    @Test
    fun `manifest carries both backup attributes`() {
        val manifest = File("../keyboard/ime/app/src/main/AndroidManifest.xml")
            .takeIf { it.isFile }
            ?: File("keyboard/ime/app/src/main/AndroidManifest.xml")
        val text = manifest.readText()
        assertTrue(text.contains("android:dataExtractionRules=\"@xml/personaspeak_data_extraction_rules\""))
        assertTrue(text.contains("android:fullBackupContent=\"@xml/personaspeak_full_backup_content\""))
    }

    @Test
    fun `artifact names agree between store constants and rule files`() {
        assertEquals(DataStoreProviderConfigStore.LIVE_BLOB_NAME, "personaspeak_secret.bin")
        assertEquals(DataStoreProviderConfigStore.STAGING_BLOB_NAME, "personaspeak_secret.bin.staging")
    }

    private fun assertEquals(expected: Any?, actual: Any?) {
        org.junit.Assert.assertEquals(expected, actual)
    }
}
