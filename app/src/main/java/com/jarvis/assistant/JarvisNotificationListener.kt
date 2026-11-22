package com.jarvis.assistant

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class JarvisNotificationListener : NotificationListenerService() {
    private val messagingApps = setOf(
        "com.whatsapp", "com.google.android.apps.messaging", "com.samsung.android.messaging",
        "org.telegram.messenger", "org.thoughtcrime.securesms", "com.facebook.orca", "com.microsoft.teams"
    )

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val n = sbn.notification ?: return
        if (sbn.packageName == packageName) return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val isMessage = n.category == Notification.CATEGORY_MESSAGE || sbn.packageName in messagingApps
        if (!isMessage) return
        val extras = n.extras ?: return
        val who = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return
        Inbox.add(who, text)
        if (Store(this).readAloud) {
            Speaker.init(this)
            Speaker.say("Message from $who. $text", false)
        }
    }
}
