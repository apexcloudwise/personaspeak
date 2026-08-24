package biz.pixelperfectstudios.personaspeak.ui.settings

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PersonaSpeakSettingsActivityTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test
    fun `createIntent configures destination and personaId extras`() {
        val intent = PersonaSpeakSettingsActivity.createIntent(
            context = context,
            destination = PersonaSpeakSettingsActivity.DESTINATION_PERSONAS,
            personaId = "bundled:jeeves",
        )

        assertEquals(
            PersonaSpeakSettingsActivity.DESTINATION_PERSONAS,
            intent.getStringExtra(PersonaSpeakSettingsActivity.EXTRA_DESTINATION),
        )
        assertEquals(
            "bundled:jeeves",
            intent.getStringExtra(PersonaSpeakSettingsActivity.EXTRA_PERSONA_ID),
        )
    }

    @Test
    fun `activity launches successfully with default home destination`() {
        val controller = Robolectric.buildActivity(PersonaSpeakSettingsActivity::class.java)
        val activity = controller.setup().get()

        assertNotNull(activity)
    }

    @Test
    fun `activity launches successfully with personas destination intent`() {
        val intent = PersonaSpeakSettingsActivity.createIntent(
            context = context,
            destination = PersonaSpeakSettingsActivity.DESTINATION_PERSONAS,
        )
        val controller = Robolectric.buildActivity(PersonaSpeakSettingsActivity::class.java, intent)
        val activity = controller.setup().get()

        assertNotNull(activity)
    }

    @Test
    fun `activity launches successfully with persona detail destination intent`() {
        val intent = PersonaSpeakSettingsActivity.createIntent(
            context = context,
            destination = PersonaSpeakSettingsActivity.DESTINATION_PERSONA_DETAIL,
            personaId = "bundled:jeeves",
        )
        val controller = Robolectric.buildActivity(PersonaSpeakSettingsActivity::class.java, intent)
        val activity = controller.setup().get()

        assertNotNull(activity)
    }
}
