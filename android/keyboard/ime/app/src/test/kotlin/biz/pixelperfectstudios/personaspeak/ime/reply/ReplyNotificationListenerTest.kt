package biz.pixelperfectstudios.personaspeak.ime.reply

import android.app.Notification
import android.app.Person
import android.os.Bundle
import android.os.Process
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider
import biz.pixelperfectstudios.personaspeak.ui.reply.IncomingMessageStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric tests for the notification parsing rules (plan §3.2) and the
 * listener's store wiring. MessagingStyle messages are placed into the extras
 * bundle directly — the parser is tested at the bundle level (plan risk
 * table), using the framework's own bundle keys ("text", "time", "sender",
 * "sender_person") so no hidden-API parcelling can mask the rules.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReplyNotificationListenerTest {

    private val ownPackage = "biz.pixelperfectstudios.personaspeak"

    private fun messageBundle(
        text: String,
        sender: String? = null,
        senderPerson: String? = null,
    ): Bundle = Bundle().apply {
        putString("text", text)
        putLong("time", 1L)
        sender?.let { putString("sender", it) }
        senderPerson?.let {
            putBundle("sender_person", Bundle().apply { putString("name", it) })
        }
    }

    private fun messagingExtras(vararg messages: Bundle): Bundle {
        val extras = Bundle()
        extras.putParcelableArray(Notification.EXTRA_MESSAGES, messages)
        return extras
    }

    private fun parse(
        packageName: String = "com.example.messages",
        flags: Int = 0,
        category: String? = null,
        extras: Bundle = Bundle(),
        appLabel: String = "Messages",
    ) = ReplyNotificationParser.parse(
        packageName = packageName,
        ownPackage = ownPackage,
        flags = flags,
        category = category,
        extras = extras,
        appLabel = appLabel,
    )

    @Test
    fun `messaging style notification yields last message text and person sender`() {
        // Built through the real Notification.Builder + MessagingStyle path —
        // the same path a real messaging app's notification takes.
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val sam = Person.Builder().setName("Sam").build()
        val style = Notification.MessagingStyle(sam)
            .addMessage("First message", 1L, sam)
            .addMessage("Running late, start the tea without me", 2L, sam)
        val notification = Notification.Builder(context, "replies-test")
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setStyle(style)
            .build()

        val parsed = ReplyNotificationParser.parse(
            packageName = "com.example.messages",
            ownPackage = ownPackage,
            flags = 0,
            category = notification.category,
            extras = notification.extras,
            appLabel = "Messages",
        )

        assertEquals("Running late, start the tea without me", parsed!!.text)
        assertEquals("Sam", parsed.sender)
        assertEquals("Messages", parsed.appLabel)
    }

    @Test
    fun `messaging style without a person falls back to EXTRA_TITLE sender`() {
        val extras = messagingExtras(messageBundle("Hello there"))
        extras.putCharSequence(Notification.EXTRA_TITLE, "Priya")

        val context = parse(extras = extras)

        assertEquals("Hello there", context!!.text)
        assertEquals("Priya", context.sender)
    }

    @Test
    fun `messaging style falls back to EXTRA_CONVERSATION_TITLE when no person and no title`() {
        val extras = messagingExtras(messageBundle("Hello there"))
        extras.putCharSequence(Notification.EXTRA_CONVERSATION_TITLE, "Team chat")

        val context = parse(extras = extras)

        assertEquals("Hello there", context!!.text)
        assertEquals("Team chat", context.sender)
    }

    @Test
    fun `category message fallback without EXTRA_MESSAGES is parsed`() {
        val notification = Notification()
        notification.category = Notification.CATEGORY_MESSAGE
        notification.extras.putCharSequence(Notification.EXTRA_TEXT, "Are we still on for six?")
        notification.extras.putCharSequence(Notification.EXTRA_TITLE, "Elena")

        val context = ReplyNotificationParser.parse(
            packageName = "com.example.messages",
            ownPackage = ownPackage,
            flags = 0,
            category = notification.category,
            extras = notification.extras,
            appLabel = "Messages",
        )

        assertEquals("Are we still on for six?", context!!.text)
        assertEquals("Elena", context.sender)
    }

    @Test
    fun `own package notifications are never parsed`() {
        val context = parse(
            packageName = ownPackage,
            extras = messagingExtras(messageBundle("self echo", senderPerson = "Me")),
        )

        assertNull(context)
    }

    @Test
    fun `group summaries are skipped`() {
        val context = parse(
            flags = Notification.FLAG_GROUP_SUMMARY,
            category = Notification.CATEGORY_MESSAGE,
            extras = Bundle().apply {
                putCharSequence(Notification.EXTRA_TEXT, "2 new messages")
                putCharSequence(Notification.EXTRA_TITLE, "Messages")
            },
        )

        assertNull(context)
    }

    @Test
    fun `ongoing notifications are skipped`() {
        val context = parse(
            flags = Notification.FLAG_ONGOING_EVENT,
            category = Notification.CATEGORY_MESSAGE,
            extras = Bundle().apply {
                putCharSequence(Notification.EXTRA_TEXT, "Music playing")
                putCharSequence(Notification.EXTRA_TITLE, "Player")
            },
        )

        assertNull(context)
    }

    @Test
    fun `empty text is skipped`() {
        val context = parse(
            extras = messagingExtras(messageBundle("   ", senderPerson = "Sam")),
            category = Notification.CATEGORY_MESSAGE,
        )

        assertNull(context)
    }

    @Test
    fun `non-message category without messaging style is skipped`() {
        val extras = Bundle().apply {
            putCharSequence(Notification.EXTRA_TEXT, "Download complete")
            putCharSequence(Notification.EXTRA_TITLE, "Updater")
        }

        val context = parse(category = "other_category", extras = extras)

        assertNull(context)
    }

    @Test
    fun `listener routes parsed notifications into the store and forgets on disconnect`() {
        // Robolectric builds the service with a base context so getPackageName() resolves.
        val listener = Robolectric.setupService(ReplyNotificationListener::class.java)
        val store = IncomingMessageStore.instance
        store.clearAll()

        val notification = Notification()
        notification.category = Notification.CATEGORY_MESSAGE
        notification.extras.putCharSequence(Notification.EXTRA_TEXT, "Running late, start the tea without me")
        notification.extras.putCharSequence(Notification.EXTRA_TITLE, "Sam")
        val sbn = StatusBarNotification(
            "com.example.messages", "com.example.messages", 1, null,
            1000, 1, 0, notification, Process.myUserHandle(), 0L,
        )

        listener.onNotificationPosted(sbn)

        assertEquals("Running late, start the tea without me", store.peekLatest()?.text)
        assertEquals("Sam", store.peekLatest()?.sender)

        listener.onListenerDisconnected()
        assertNull(store.peekLatest())

        store.clearAll()
    }
}
