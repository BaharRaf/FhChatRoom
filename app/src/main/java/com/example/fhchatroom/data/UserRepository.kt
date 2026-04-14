package com.example.fhchatroom.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
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
        ensureUserDocumentExists(auth.currentUser?.email ?: email.trim())
        Result.Success(true)
    } catch(e: Exception) {
        Result.Error(e)
    }

    private suspend fun ensureUserDocumentExists(email: String) {
        val normalizedEmail = email.trim()
        if (normalizedEmail.isBlank()) return

        val document = firestore.collection("users")
            .document(normalizedEmail)
            .get()
            .await()

        if (document.exists()) return

        val fallbackUser = buildFallbackUser(normalizedEmail)
        firestore.collection("users")
            .document(normalizedEmail)
            .set(fallbackUser, SetOptions.merge())
            .await()
    }

    private fun buildFallbackUser(email: String): User {
        val localPart = email.substringBefore("@").trim()
        val readableLocalPart = localPart
            .replace(".", " ")
            .replace("_", " ")
            .replace("-", " ")
            .trim()

        val fallbackFirstName = readableLocalPart
            .split("\\s+".toRegex())
            .firstOrNull()
            ?.replaceFirstChar { it.uppercase() }
            .orEmpty()

        return User(
            firstName = fallbackFirstName,
            lastName = "",
            email = email,
            isOnline = false
        )
    }

    private suspend fun saveUserToFirestore(user: User) {
        firestore.collection("users")
            .document(user.email)
            .set(user)
            .await()
    }


    suspend fun getCurrentUser(): Result<User> = try {
        val uid = auth.currentUser?.email
        if (uid != null) {
            val userDocument = firestore.collection("users")
                .document(uid)
                .get()
                .await()
            val user = userDocument.toUserOrNull()
            if (user != null) {
                Result.Success(user)
            } else {
                Result.Error(Exception("User data not found"))
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
        val user = userDocument.toUserOrNull()
        if (user != null) {
            Result.Success(user)
        } else {
            Result.Error(Exception("User data not found"))
        }
    } catch (e: Exception) {
        Result.Error(e)
    }
}
