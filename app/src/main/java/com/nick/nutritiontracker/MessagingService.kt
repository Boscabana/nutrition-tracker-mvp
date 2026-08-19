package com.nick.nutritiontracker

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("MessagingService", "Refreshed token: $token")
        
        // Save token to Firestore if user is logged in
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(user.uid)
                .update("fcmToken", token)
                .addOnFailureListener {
                    Log.e("MessagingService", "Failed to update token", it)
                }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        // Handled by Firebase notification payload automatically if app is in background.
        // If app is in foreground, we can show a local notification.
        val data = remoteMessage.data
        if (data.isNotEmpty()) {
            val senderName = data["senderName"] ?: "Jemand"
            val type = data["type"] ?: "MESSAGE"
            NotificationHelper.showInboxNotification(this, senderName, type)
        } else {
            remoteMessage.notification?.let {
                // If it's a simple notification payload
                NotificationHelper.showInboxNotification(this, "NutriTracker", "MESSAGE")
            }
        }
    }
}
