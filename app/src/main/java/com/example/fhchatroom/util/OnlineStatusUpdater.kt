package com.example.fhchatroom.util

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import android.util.Log

class OnlineStatusUpdater {
    private var connectedListener: ValueEventListener? = null
    private var connRef: DatabaseReference? = null
    private var statusRef: DatabaseReference? = null

    private val auth = FirebaseAuth.getInstance()
    private val rtdb = FirebaseDatabase.getInstance()
    private val TAG = "OnlineStatusUpdater-DIAG"

    private val authStateListener = FirebaseAuth.AuthStateListener { auth ->
        val user = auth.currentUser
        if (user?.email != null) {
            Log.d(TAG, "Auth state: user signed in: ${user.email}")
            setupPresence(user.email!!)
        } else {
            Log.d(TAG, "Auth state: user signed out or email is null")
            tearDownPresence()
        }
    }

    init {
        Log.d(TAG, "OnlineStatusUpdater INIT - user=${auth.currentUser?.email}")
        auth.addAuthStateListener(authStateListener)
        auth.currentUser?.email?.let { setupPresence(it) }
    }

    private fun setupPresence(rawEmail: String) {
        Log.d(TAG, "setupPresence() called for: $rawEmail")
        tearDownPresence()

        // Prepare references
        val key = rawEmail.replace(".", ",")
        Log.d(TAG, "Email key used for RTDB: $key")
        statusRef = rtdb.getReference("status").child(key)
        connRef = rtdb.getReference(".info/connected")

        // Ensure onDisconnect is set
        statusRef?.onDisconnect()?.setValue(false)
            ?.addOnSuccessListener { Log.d(TAG, "onDisconnect set to false for $rawEmail") }
            ?.addOnFailureListener { e -> Log.e(TAG, "Failed to set onDisconnect for $rawEmail", e) }

        // Listen to connection state
        connectedListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                Log.d(TAG, ".info/connected changed: $connected")
                if (connected) {
                    Log.d(TAG, "Connected. Marking user ONLINE in RTDB for $rawEmail")
                    statusRef?.setValue(true)
                        ?.addOnSuccessListener { Log.d(TAG, "Set RTDB status = true (online) for $rawEmail") }
                        ?.addOnFailureListener { e -> Log.e(TAG, "Failed to set RTDB status for $rawEmail", e) }
                } else {
                    Log.d(TAG, "Not connected. (Will rely on onDisconnect when needed.)")
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, ".info/connected listener cancelled", error.toException())
            }
        }
        connRef?.addValueEventListener(connectedListener!!)

        // Immediately mark online
        statusRef?.setValue(true)
            ?.addOnSuccessListener { Log.d(TAG, "Immediate set RTDB status = true (online) for $rawEmail") }
            ?.addOnFailureListener { e -> Log.e(TAG, "Immediate set failed for $rawEmail", e) }
    }

    fun goOffline() {
        val email = auth.currentUser?.email
        if (email == null) {
            Log.d(TAG, "goOffline() called, but no user is signed in")
            return
        }
        Log.d(TAG, "goOffline() called for $email")
        statusRef?.setValue(false)
            ?.addOnSuccessListener { Log.d(TAG, "Set RTDB status = false (offline) for $email") }
            ?.addOnFailureListener { e -> Log.e(TAG, "Failed to set RTDB status offline for $email", e) }
    }

    private fun tearDownPresence() {
        Log.d(TAG, "tearDownPresence() called")
        connectedListener?.let { listener ->
            connRef?.removeEventListener(listener)
            Log.d(TAG, "Removed RTDB .info/connected listener")
        }
        connectedListener = null

        // Cancel onDisconnect and mark offline
        statusRef?.onDisconnect()?.cancel()
        auth.currentUser?.email?.let { email ->
            statusRef?.setValue(false)
                ?.addOnSuccessListener { Log.d(TAG, "Set RTDB status = false (offline) for $email in tearDownPresence()") }
                ?.addOnFailureListener { e -> Log.e(TAG, "Failed to set offline in tearDownPresence() for $email", e) }
        }

        // Clear refs
        connRef = null
        statusRef = null
    }

    fun cleanup() {
        Log.d(TAG, "cleanup() called - removing authStateListener and cleaning up presence")
        auth.removeAuthStateListener(authStateListener)
        tearDownPresence()
    }
}
