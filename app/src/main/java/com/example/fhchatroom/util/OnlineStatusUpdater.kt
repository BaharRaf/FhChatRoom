package com.example.fhchatroom.util

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.firestore.FirebaseFirestore
import android.util.Log

class OnlineStatusUpdater {
    private var connectedListener: ValueEventListener? = null
    private var connRef: DatabaseReference? = null
    private var statusRef: DatabaseReference? = null

    private val auth = FirebaseAuth.getInstance()
    private val rtdb = FirebaseDatabase.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val TAG = "OnlineStatusUpdater"

    private val authStateListener = FirebaseAuth.AuthStateListener { auth ->
        auth.currentUser?.email?.let { email ->
            Log.d(TAG, "User signed in: $email")
            setupPresence(email)
        } ?: run {
            Log.d(TAG, "User signed out or email null")
            tearDownPresence()
        }
    }

    init {
        auth.addAuthStateListener(authStateListener)
        auth.currentUser?.email?.let { setupPresence(it) }
    }

    private fun setupPresence(rawEmail: String) {
        // Clean up any previous listener
        tearDownPresence()

        // Prepare references
        val key = rawEmail.replace(".", ",")
        statusRef = rtdb.getReference("status").child(key)
        connRef = rtdb.getReference(".info/connected")

        // Ensure onDisconnect is set
        statusRef?.onDisconnect()?.setValue(false)
            ?.addOnSuccessListener { Log.d(TAG, "onDisconnect set to false for $rawEmail") }
            ?.addOnFailureListener { e -> Log.e(TAG, "Failed onDisconnect for $rawEmail", e) }

        // Listen to connection state
        connectedListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                if (connected) {
                    Log.d(TAG, "Connected -> marking online: $rawEmail")
                    statusRef?.setValue(true)
                    updateFirestore(rawEmail, true)
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, ".info/connected listener cancelled", error.toException())
            }
        }
        connRef?.addValueEventListener(connectedListener!!)

        // Immediately mark online
        statusRef?.setValue(true)
        updateFirestore(rawEmail, true)
    }

    private fun updateFirestore(email: String, online: Boolean) {
        firestore.collection("users").document(email)
            .update("isOnline", online)
            .addOnSuccessListener { Log.d(TAG, "Firestore isOnline=$online for $email") }
            .addOnFailureListener { e -> Log.e(TAG, "Failed Firestore update for $email", e) }
    }

    fun goOffline() {
        auth.currentUser?.email?.let { email ->
            Log.d(TAG, "Explicit offline called for $email")
            statusRef?.setValue(false)
            updateFirestore(email, false)
        }
    }

    private fun tearDownPresence() {
        // Remove connection listener
        connectedListener?.let { listener ->
            connRef?.removeEventListener(listener)
        }
        connectedListener = null

        // Cancel onDisconnect and mark offline
        statusRef?.onDisconnect()?.cancel()
        auth.currentUser?.email?.let { email ->
            statusRef?.setValue(false)
            updateFirestore(email, false)
        }

        // Clear refs
        connRef = null
        statusRef = null
    }

    fun cleanup() {
        auth.removeAuthStateListener(authStateListener)
        tearDownPresence()
    }
}