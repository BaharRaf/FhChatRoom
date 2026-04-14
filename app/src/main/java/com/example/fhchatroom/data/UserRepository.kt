package com.example.fhchatroom.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class UserRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {

    suspend fun signUp(
        email: String,
        password: String,
        firstName: String,
        lastName: String
    ): Result<Boolean> = try {
        auth.createUserWithEmailAndPassword(email, password).await()
        val user = User(firstName, lastName, email, isOnline = true)
        saveUserToFirestore(user)
        Result.Success(true)
    } catch (e: Exception) {
        Result.Error(e)
    }


    suspend fun login(email: String, password: String): Result<Boolean> = try {
        auth.signInWithEmailAndPassword(email, password).await()


        Result.Success(true)
    } catch(e: Exception) {
        Result.Error(e)
    }


    private suspend fun saveUserToFirestore(user: User) {
        firestore.collection("users")
            .document(user.email)
            .set(user)
            .await()
    }


    suspend fun getCurrentUser(): Result<User> = try {
        val firebaseUser = auth.currentUser
        val email = firebaseUser?.email
        if (firebaseUser != null && email != null) {
            val userDocument = firestore.collection("users")
                .document(email)
                .get()
                .await()
            val user = userDocument.toObject(User::class.java)
            if (user != null) {
                Result.Success(user)
            } else {
                // Backfill: Firestore profile is missing (can happen if signUp's
                // second write failed, or the doc was deleted). Reconstruct from
                // the Firebase Auth profile so the app stays usable.
                val displayName = firebaseUser.displayName.orEmpty().trim()
                val (firstName, lastName) = when {
                    displayName.isEmpty() -> email.substringBefore('@') to ""
                    displayName.contains(' ') -> {
                        displayName.substringBefore(' ') to displayName.substringAfter(' ')
                    }
                    else -> displayName to ""
                }
                val fallback = User(firstName, lastName, email, isOnline = true)
                try {
                    saveUserToFirestore(fallback)
                } catch (_: Exception) {
                    // Non-fatal: we can still return the in-memory user.
                }
                Result.Success(fallback)
            }
        } else {
            Result.Error(Exception("User not authenticated"))
        }
    } catch (e: Exception) {
        Result.Error(e)
    }
    suspend fun getUserByEmail(email: String): Result<User> = try {
        val userDocument = firestore.collection("users")
            .document(email)
            .get()
            .await()
        val user = userDocument.toObject(User::class.java)
        if (user != null) {
            Result.Success(user)
        } else {
            Result.Error(Exception("User data not found"))
        }
    } catch (e: Exception) {
        Result.Error(e)
    }
}
