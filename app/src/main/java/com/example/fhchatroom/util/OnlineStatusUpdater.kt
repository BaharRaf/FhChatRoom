package com.example.fhchatroom.util // Or your preferred package for utilities

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore

class OnlineStatusUpdater {

    private var valueEventListenerForConnection: ValueEventListener? = null
    private var databaseRefToConnectedInfo: DatabaseReference? = null
    private var userRealtimeDbStatusRef: DatabaseReference? = null
    private var currentAuthenticatedUserEmail: String? = null

    private val firebaseAuthInstance: FirebaseAuth = FirebaseAuth.getInstance()
    private val firebaseRealtimeDatabaseInstance: FirebaseDatabase = FirebaseDatabase.getInstance()
    private val firebaseFirestoreInstance: FirebaseFirestore = FirebaseFirestore.getInstance()

    private val TAG = "OnlineStatusUpdater"

    // Listener for Firebase Authentication state changes
    private val authStateListener = FirebaseAuth.AuthStateListener { auth ->
        val firebaseUser = auth.currentUser
        if (firebaseUser != null) {
            // User is signed in
            val email = firebaseUser.email
            if (email != null) {
                if (email != currentAuthenticatedUserEmail || userRealtimeDbStatusRef == null) {
                    // New user signed in, or app restarted with a user already signed in.
                    Log.d(TAG, "Auth state changed: User signed in - $email. Previous: $currentAuthenticatedUserEmail")
                    clearPreviousUserStatusAndListeners() // Clean up for any previous user
                    currentAuthenticatedUserEmail = email
                    initializeUserOnlinePresence()
                }
                // If email is the same and userRealtimeDbStatusRef is not null, it's already handled.
            } else {
                Log.w(TAG, "Auth state changed: User signed in but email is null.")
                clearPreviousUserStatusAndListeners() // Clear if email becomes null
                currentAuthenticatedUserEmail = null
            }
        } else {
            // User is signed out
            Log.d(TAG, "Auth state changed: User signed out. Previous: $currentAuthenticatedUserEmail")
            clearPreviousUserStatusAndListeners()
            currentAuthenticatedUserEmail = null
        }
    }

    init {
        firebaseAuthInstance.addAuthStateListener(authStateListener)
        // Check current user immediately in case listener fires after init completes
        val currentUser = firebaseAuthInstance.currentUser
        if (currentUser != null && currentUser.email != null) {
            if (currentAuthenticatedUserEmail == null) { // Avoid race if authListener fired quickly
                Log.d(TAG, "Init: User already signed in - ${currentUser.email}")
                currentAuthenticatedUserEmail = currentUser.email
                initializeUserOnlinePresence()
            }
        } else {
            Log.d(TAG, "Init: No user initially signed in.")
        }
    }

    private fun initializeUserOnlinePresence() {
        if (currentAuthenticatedUserEmail == null) {
            Log.w(TAG, "initializeUserOnlinePresence: currentAuthenticatedUserEmail is null, cannot proceed.")
            return
        }
        Log.d(TAG, "initializeUserOnlinePresence for: $currentAuthenticatedUserEmail")

        val userEmailForPath = currentAuthenticatedUserEmail!!.replace(".", ",")
        userRealtimeDbStatusRef = firebaseRealtimeDatabaseInstance.getReference("status/$userEmailForPath")
        databaseRefToConnectedInfo = firebaseRealtimeDatabaseInstance.getReference(".info/connected")

        if (valueEventListenerForConnection != null) {
            Log.d(TAG, "initializeUserOnlinePresence: Removing existing connection listener.")
            databaseRefToConnectedInfo?.removeEventListener(valueEventListenerForConnection!!)
        }

        valueEventListenerForConnection = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                Log.d(TAG, ".info/connected: $connected for user $currentAuthenticatedUserEmail")
                if (connected) {
                    userRealtimeDbStatusRef?.onDisconnect()?.setValue(false)
                        ?.addOnSuccessListener { Log.d(TAG, "onDisconnect for $currentAuthenticatedUserEmail set to false.") }
                        ?.addOnFailureListener { e -> Log.e(TAG, "Failed to set onDisconnect for $currentAuthenticatedUserEmail.", e) }

                    userRealtimeDbStatusRef?.setValue(true)
                        ?.addOnSuccessListener { Log.d(TAG, "RTDB status for $currentAuthenticatedUserEmail set to true.") }
                        ?.addOnFailureListener { e -> Log.e(TAG, "Failed to set RTDB status true for $currentAuthenticatedUserEmail.", e) }

                    if (currentAuthenticatedUserEmail != null) {
                        updateFirestoreUserOnlineStatus(currentAuthenticatedUserEmail!!, true)
                    }
                } else {
                    // Client is not connected to Firebase Realtime Database.
                    // onDisconnect will handle server-side detection if this is a real disconnect.
                    // If app is still running, SDK attempts to reconnect.
                    // We could set Firestore to false here, but it might cause flapping.
                    // Rely on onStop for graceful backgrounding.
                    Log.d(TAG, "Not connected to RTDB. Relying on onDisconnect or onStop for $currentAuthenticatedUserEmail.")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "ValueEventListener for .info/connected cancelled for $currentAuthenticatedUserEmail", error.toException())
            }
        }
        databaseRefToConnectedInfo?.addValueEventListener(valueEventListenerForConnection!!)
    }

    fun markUserAsOnlineIfNeeded() {
        if (currentAuthenticatedUserEmail != null) {
            Log.d(TAG, "markUserAsOnlineIfNeeded for $currentAuthenticatedUserEmail")
            // This function is called onActivityStart.
            // The .info/connected listener should handle setting status to true when connection is established.
            // Explicitly setting true here might be redundant but can help if .info/connected is slow to update.
            // However, ensure listeners are set up first.
            if (userRealtimeDbStatusRef == null || valueEventListenerForConnection == null) {
                Log.d(TAG, "markUserAsOnlineIfNeeded: Listeners not ready, re-initializing for $currentAuthenticatedUserEmail")
                initializeUserOnlinePresence() // Ensure presence is initialized
            } else {
                // If listeners are active, they should manage the state.
                // Forcing true here might be okay as a "belt and suspenders" approach.
                userRealtimeDbStatusRef?.setValue(true)
                updateFirestoreUserOnlineStatus(currentAuthenticatedUserEmail!!, true)
            }
        } else {
            Log.d(TAG, "markUserAsOnlineIfNeeded: No authenticated user.")
        }
    }

    fun markUserAsOffline() {
        if (currentAuthenticatedUserEmail != null && userRealtimeDbStatusRef != null) {
            Log.d(TAG, "markUserAsOffline for $currentAuthenticatedUserEmail (typically onStop)")
            userRealtimeDbStatusRef?.setValue(false)
            updateFirestoreUserOnlineStatus(currentAuthenticatedUserEmail!!, false)
        } else {
            Log.d(TAG, "markUserAsOffline: No user or RTDB ref to mark offline.")
        }
        // Do not remove listeners here; onDisconnect handles if app is killed.
        // AuthStateListener handles if user logs out.
    }

    private fun clearPreviousUserStatusAndListeners() {
        val emailToClear = currentAuthenticatedUserEmail // Capture before it's potentially nulled
        Log.d(TAG, "clearPreviousUserStatusAndListeners for: $emailToClear")

        valueEventListenerForConnection?.let { listener ->
            databaseRefToConnectedInfo?.removeEventListener(listener)
            Log.d(TAG, "Removed connection listener for $emailToClear.")
        }
        valueEventListenerForConnection = null
        // databaseRefToConnectedInfo = null; // This ref is generic, no need to null

        if (userRealtimeDbStatusRef != null && emailToClear != null) {
            Log.d(TAG, "Cancelling onDisconnect and setting RTDB status to false for $emailToClear.")
            userRealtimeDbStatusRef?.onDisconnect()?.cancel()
                ?.addOnSuccessListener { Log.d(TAG, "Cancelled onDisconnect for $emailToClear.") }
                ?.addOnFailureListener { e -> Log.e(TAG, "Failed to cancel onDisconnect for $emailToClear.", e) }
            userRealtimeDbStatusRef?.setValue(false)
            updateFirestoreUserOnlineStatus(emailToClear, false)
        }
        userRealtimeDbStatusRef = null
        // currentAuthenticatedUserEmail will be updated by the authStateListener logic
    }

    private fun updateFirestoreUserOnlineStatus(email: String, isOnline: Boolean) {
        Log.d(TAG, "Updating Firestore: $email isOnline: $isOnline")
        firebaseFirestoreInstance.collection("users").document(email)
            .update("isOnline", isOnline)
            .addOnSuccessListener { Log.d(TAG, "Firestore update success for $email, isOnline: $isOnline") }
            .addOnFailureListener { e -> Log.e(TAG, "Firestore update failed for $email, isOnline: $isOnline", e) }
    }

    fun performCleanupOnAppDestroy() {
        Log.d(TAG, "performCleanupOnAppDestroy for $currentAuthenticatedUserEmail")
        firebaseAuthInstance.removeAuthStateListener(authStateListener)
        clearPreviousUserStatusAndListeners() // Ensure current user is marked offline and listeners removed
        currentAuthenticatedUserEmail = null // Explicitly nullify
    }
}
