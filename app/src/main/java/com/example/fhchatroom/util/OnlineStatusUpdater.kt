package com.example.fhchatroom.util

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import android.util.Log

class OnlineStatusUpdater {
    private var connectedListener: ValueEventListener? = null
    private var connRef: DatabaseReference? = null
    private var statusRef: DatabaseReference? = null
    private var currentEmail: String? = null

    private val auth = FirebaseAuth.getInstance()
    private val rtdb = FirebaseDatabase.getInstance()
    private val TAG = "OnlineStatusUpdater-DIAG"

    private val authStateListener = FirebaseAuth.AuthStateListener { a ->
        val newEmail = a.currentUser?.email
        if (newEmail == currentEmail && connectedListener != null) return@AuthStateListener
        if (newEmail == null) {
            Log.d(TAG, "Auth state: signed out")
            tearDownPresence()
            currentEmail = null
        } else {
            Log.d(TAG, "Auth state: signed in as $newEmail")
            tearDownPresence()
            currentEmail = newEmail
            setupPresence(newEmail)
        }
    }

    init {
        Log.d(TAG, "OnlineStatusUpdater INIT - user=${auth.currentUser?.email}")
        auth.addAuthStateListener(authStateListener)
    }

    private fun setupPresence(rawEmail: String) {
        if (connectedListener != null) {
            Log.d(TAG, "setupPresence() skipped - already active for $currentEmail")
            return
        }
        Log.d(TAG, "setupPresence() starting for: $rawEmail")

        val key = rawEmail.replace(".", ",")
        statusRef = rtdb.getReference("status").child(key)
        connRef = rtdb.getReference(".info/connected")

        // The .info/connected listener is the single source of truth for marking
        // the user online. It fires on every (re)connection, so onDisconnect gets
        // re-registered against each new socket - necessary because onDisconnect
        // is bound to a specific socket and doesn't survive reconnects.
        connectedListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                Log.d(TAG, ".info/connected = $connected for $rawEmail")
                if (!connected) return
                val ref = statusRef ?: return
                // Fire both writes directly. Don't chain setValue(true) off of
                // onDisconnect's success callback - if the callback doesn't fire
                // (e.g., socket drops mid-registration), the user never appears
                // online even though they're using the app.
                ref.onDisconnect().setValue(false)
                    .addOnSuccessListener { Log.d(TAG, "onDisconnect(false) registered for $rawEmail") }
                    .addOnFailureListener { e -> Log.e(TAG, "onDisconnect register failed for $rawEmail", e) }
                ref.setValue(true)
                    .addOnSuccessListener { Log.d(TAG, "status=true written for $rawEmail") }
                    .addOnFailureListener { e -> Log.e(TAG, "status=true write failed for $rawEmail", e) }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, ".info/connected listener cancelled", error.toException())
            }
        }
        connRef?.addValueEventListener(connectedListener!!)
    }

    fun goOnline() {
        val email = auth.currentUser?.email ?: return
        Log.d(TAG, "goOnline() called for $email")
        currentEmail = email
        setupPresence(email)
    }

    fun goOffline() {
        val email = auth.currentUser?.email
        Log.d(TAG, "goOffline() called for $email")

        // Stop listening to connection state - without this, a reconnect event
        // after the user backgrounds would flip status back to true.
        connectedListener?.let { connRef?.removeEventListener(it) }
        connectedListener = null

        statusRef?.onDisconnect()?.cancel()
        statusRef?.setValue(false)
            ?.addOnSuccessListener { Log.d(TAG, "status=false written for $email") }
            ?.addOnFailureListener { e -> Log.e(TAG, "status=false write failed for $email", e) }
    }

    private fun tearDownPresence() {
        connectedListener?.let { listener ->
            connRef?.removeEventListener(listener)
        }
        connectedListener = null

        statusRef?.onDisconnect()?.cancel()
        statusRef?.setValue(false)

        connRef = null
        statusRef = null
    }

    fun cleanup() {
        Log.d(TAG, "cleanup() called")
        auth.removeAuthStateListener(authStateListener)
        tearDownPresence()
        currentEmail = null
    }
}
