package biz.pixelperfectstudios.personaspeak.ime.reply

import android.app.Notification
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import biz.pixelperfectstudios.personaspeak.personas.IncomingMessageContext
import biz.pixelperfectstudios.personaspeak.ui.reply.IncomingMessageStore

/**
 * Phase 2 (ADR-0011): opt-in listener for incoming message notifications.
 *
 * The service runs only after the user grants notification access in system
 * settings — there is no in-app switch to do it for them. Everything here is
 * parse-and-forward: [onNotificationPosted] extracts one
 * [IncomingMessageContext] and hands it to the RAM-only [IncomingMessageStore].
 * No disk, no logs of content, no read-marking, no remote-input replies. We
 * read to draft; the user reviews, edits, and sends.
 */
class ReplyNotificationListener : NotificationListenerService() {

    private val appLabelCache = mutableMapOf<String, String>()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val context = ReplyNotificationParser.parse(
            packageName = sbn.packageName,
            ownPackage = packageName,
            flags = sbn.notification.flags,
            category = sbn.notification.category,
            extras = sbn.notification.extras,
            appLabel = resolveAppLabel(sbn.packageName),
        ) ?: return

        IncomingMessageStore.instance.put(sbn.key, context)
    }

    override fun onListenerDisconnected() {
        // Access revoked or the system unbound us — the data source is gone,
        // so the cached contexts go with it (ADR-0011 §1).
        IncomingMessageStore.instance.clearAll()
    }

    override fun onDestroy() {
        IncomingMessageStore.instance.clearAll()
        super.onDestroy()
    }

    private fun resolveAppLabel(packageName: String): String {
        appLabelCache[packageName]?.let { return it }
        val label = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(packageName, 0),
            ).toString().trim().ifEmpty { packageName }
        } catch (_: Exception) {
            packageName
        }
        appLabelCache[packageName] = label
        return label
    }
}

/**
 * Parsing rules, pinned for tests (plan §3.2). Bundle-level so Robolectric
 * tests can construct the extras directly without a parcel round-trip.
 */
object ReplyNotificationParser {

    fun parse(
        packageName: String,
        ownPackage: String,
        flags: Int,
        category: String?,
        extras: Bundle,
        appLabel: String,
    ): IncomingMessageContext? {
        if (packageName == ownPackage) return null
        if (flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
        if (flags and Notification.FLAG_ONGOING_EVENT != 0) return null

        extractMessagingStyle(extras)?.let { (sender, text) ->
            return IncomingMessageContext(sender = sender, appLabel = appLabel, text = text)
        }

        if (category == Notification.CATEGORY_MESSAGE) {
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
            if (text.isNotEmpty()) {
                val sender = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
                    ?.takeIf { it.isNotEmpty() }
                return IncomingMessageContext(sender = sender, appLabel = appLabel, text = text)
            }
        }

        return null
    }

    /**
     * MessagingStyle path: the last message wins; sender from the message's
     * person, falling back to EXTRA_TITLE / EXTRA_CONVERSATION_TITLE.
     */
    private fun extractMessagingStyle(extras: Bundle): Pair<String?, String>? {
        val parcelables = extras.getParcelableArray(Notification.EXTRA_MESSAGES) ?: return null
        val messages = Notification.MessagingStyle.Message.getMessagesFromBundleArray(parcelables)
        for (message in messages.reversed()) {
            val text = message?.text?.toString()?.trim().orEmpty()
            if (text.isEmpty()) continue

            val sender = messageSender(message)
                ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                ?: extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()?.trim()?.takeIf { it.isNotEmpty() }

            return sender to text
        }
        return null
    }

    private fun messageSender(message: Notification.MessagingStyle.Message): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            message.senderPerson?.name?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        } else {
            @Suppress("DEPRECATION")
            message.sender?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        }
}
