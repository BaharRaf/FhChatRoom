package com.example.fhchatroom.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.fhchatroom.Injection
import com.example.fhchatroom.data.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration

data class RecommendationUiState(
    val roomIds: List<String> = emptyList(),
    val generatedAt: Long = 0L,
    val source: String = "NONE"
)

class RecommendationViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = Injection.instance()
    private var listener: ListenerRegistration? = null

    private val _uiState = MutableLiveData(RecommendationUiState())
    val uiState: LiveData<RecommendationUiState> = _uiState

    init {
        observeRecommendations()
    }

    private fun observeRecommendations() {
        val email = auth.currentUser?.email ?: return

        listener = firestore.collection("users")
            .document(email)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }

                val user = snapshot?.toObject(User::class.java) ?: return@addSnapshotListener
                _uiState.value = RecommendationUiState(
                    roomIds = user.recommendedRoomIds,
                    generatedAt = user.recommendationsUpdatedAt,
                    source = user.recommendationSource
                )
            }
    }

    override fun onCleared() {
        super.onCleared()
        listener?.remove()
        listener = null
    }
}
