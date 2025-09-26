package com.example.fhchatroom.viewmodel

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fhchatroom.Injection
import com.example.fhchatroom.data.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.util.UUID

class ProfileViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = Injection.instance()
    private val storage = FirebaseStorage.getInstance()

    private val _currentUser = MutableLiveData<User?>()
    val currentUser: LiveData<User?> = _currentUser

    private val _uploadProgress = MutableLiveData<Float>()
    val uploadProgress: LiveData<Float> = _uploadProgress

    init {
        loadCurrentUser()
    }

    private fun loadCurrentUser() {
        val email = auth.currentUser?.email ?: return

        firestore.collection("users")
            .document(email)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                _currentUser.value = snapshot?.toObject(User::class.java)
            }
    }

    fun uploadProfilePhoto(uri: Uri, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val email = auth.currentUser?.email ?: return@launch
                val fileName = "profile_photos/${UUID.randomUUID()}.jpg"
                val storageRef = storage.reference.child(fileName)

                val uploadTask = storageRef.putFile(uri)

                uploadTask.addOnProgressListener { snapshot ->
                    val progress = (100.0 * snapshot.bytesTransferred / snapshot.totalByteCount).toFloat()
                    _uploadProgress.value = progress
                }

                uploadTask.await()
                val downloadUrl = storageRef.downloadUrl.await().toString()

                // Delete old photo if exists
                _currentUser.value?.profilePhotoUrl?.let { oldUrl ->
                    if (oldUrl.isNotEmpty()) {
                        try {
                            storage.getReferenceFromUrl(oldUrl).delete().await()
                        } catch (e: Exception) {
                            // Ignore if deletion fails
                        }
                    }
                }

                // Update user profile with new photo URL
                firestore.collection("users")
                    .document(email)
                    .update("profilePhotoUrl", downloadUrl)
                    .await()

                onComplete(true)
                _uploadProgress.value = 100f
            } catch (e: Exception) {
                onComplete(false)
            }
        }
    }

    fun uploadProfilePhotoBitmap(bitmap: Bitmap, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val email = auth.currentUser?.email ?: return@launch
                val fileName = "profile_photos/${UUID.randomUUID()}.jpg"
                val storageRef = storage.reference.child(fileName)

                val baos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos)
                val data = baos.toByteArray()

                val uploadTask = storageRef.putBytes(data)

                uploadTask.addOnProgressListener { snapshot ->
                    val progress = (100.0 * snapshot.bytesTransferred / snapshot.totalByteCount).toFloat()
                    _uploadProgress.value = progress
                }

                uploadTask.await()
                val downloadUrl = storageRef.downloadUrl.await().toString()

                // Delete old photo if exists
                _currentUser.value?.profilePhotoUrl?.let { oldUrl ->
                    if (oldUrl.isNotEmpty()) {
                        try {
                            storage.getReferenceFromUrl(oldUrl).delete().await()
                        } catch (e: Exception) {
                            // Ignore if deletion fails
                        }
                    }
                }

                // Update user profile with new photo URL
                firestore.collection("users")
                    .document(email)
                    .update("profilePhotoUrl", downloadUrl)
                    .await()

                onComplete(true)
                _uploadProgress.value = 100f
            } catch (e: Exception) {
                onComplete(false)
            }
        }
    }

    fun removeProfilePhoto(onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val email = auth.currentUser?.email ?: return@launch

                // Delete photo from storage
                _currentUser.value?.profilePhotoUrl?.let { url ->
                    if (url.isNotEmpty()) {
                        storage.getReferenceFromUrl(url).delete().await()
                    }
                }

                // Update user profile
                firestore.collection("users")
                    .document(email)
                    .update("profilePhotoUrl", "")
                    .await()

                onComplete(true)
            } catch (e: Exception) {
                onComplete(false)
            }
        }
    }

    fun updateProfile(firstName: String, lastName: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val email = auth.currentUser?.email ?: return@launch

                firestore.collection("users")
                    .document(email)
                    .update(
                        mapOf(
                            "firstName" to firstName,
                            "lastName" to lastName
                        )
                    )
                    .await()

                onComplete(true)
            } catch (e: Exception) {
                onComplete(false)
            }
        }
    }
}