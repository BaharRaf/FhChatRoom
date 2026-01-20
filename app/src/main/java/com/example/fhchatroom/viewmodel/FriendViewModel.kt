package com.example.fhchatroom.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fhchatroom.Injection
import com.example.fhchatroom.data.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class FriendsViewModel : ViewModel() {
    private val TAG = "FriendsViewModel"
    private val friendsRepository = FriendsRepository(Injection.instance())
    private val userRepository = UserRepository(FirebaseAuth.getInstance(), Injection.instance())
    private val auth = FirebaseAuth.getInstance()

    private val _friends = MutableLiveData<List<Friend>>()
    val friends: LiveData<List<Friend>> = _friends

    private val _receivedRequests = MutableLiveData<List<FriendRequest>>()
    val receivedRequests: LiveData<List<FriendRequest>> = _receivedRequests

    private val _sentRequests = MutableLiveData<List<FriendRequest>>()
    val sentRequests: LiveData<List<FriendRequest>> = _sentRequests

    private val _searchResults = MutableLiveData<List<User>>()
    val searchResults: LiveData<List<User>> = _searchResults

    private val _currentUser = MutableLiveData<User?>()
    val currentUser: LiveData<User?> = _currentUser

    private val _operationResult = MutableLiveData<Result<String>?>(null)
    val operationResult: LiveData<Result<String>?> = _operationResult

    init {
        Log.d(TAG, "FriendsViewModel initialized")
        loadCurrentUser()
        observeFriends()
        observeReceivedRequests()
        observeSentRequests()
    }

    private fun loadCurrentUser() {
        viewModelScope.launch {
            Log.d(TAG, "Loading current user")
            when (val result = userRepository.getCurrentUser()) {
                is Result.Success -> {
                    _currentUser.value = result.data
                    Log.d(TAG, "Current user loaded: ${result.data.email}")
                }
                is Result.Error -> {
                    Log.e(TAG, "Failed to load current user", result.exception)
                }
            }
        }
    }

    private fun observeFriends() {
        val userEmail = auth.currentUser?.email ?: return
        Log.d(TAG, "Observing friends for: $userEmail")
        viewModelScope.launch {
            friendsRepository.getFriends(userEmail).collect { friendsList ->
                Log.d(TAG, "Friends updated: ${friendsList.size}")
                _friends.value = friendsList
            }
        }
    }

    private fun observeReceivedRequests() {
        val userEmail = auth.currentUser?.email ?: return
        Log.d(TAG, "Observing received requests for: $userEmail")
        viewModelScope.launch {
            friendsRepository.getReceivedFriendRequests(userEmail).collect { requests ->
                Log.d(TAG, "Received requests updated: ${requests.size}")
                _receivedRequests.value = requests
            }
        }
    }

    private fun observeSentRequests() {
        val userEmail = auth.currentUser?.email ?: return
        Log.d(TAG, "Observing sent requests for: $userEmail")
        viewModelScope.launch {
            friendsRepository.getSentFriendRequests(userEmail).collect { requests ->
                Log.d(TAG, "Sent requests updated: ${requests.size}")
                _sentRequests.value = requests
            }
        }
    }

    fun sendFriendRequest(toUser: User) {
        viewModelScope.launch {
            val currentUser = _currentUser.value
            if (currentUser == null) {
                Log.e(TAG, "Cannot send friend request: current user is null")
                _operationResult.value = Result.Error(Exception("Please wait, loading user data..."))
                return@launch
            }

            Log.d(TAG, "Sending friend request to: ${toUser.email}")

            val result = friendsRepository.sendFriendRequest(
                fromEmail = currentUser.email,
                toEmail = toUser.email,
                fromName = "${currentUser.firstName} ${currentUser.lastName}".trim(),
                toName = "${toUser.firstName} ${toUser.lastName}".trim(),
                fromProfilePhoto = currentUser.profilePhotoUrl
            )

            when (result) {
                is Result.Success -> {
                    Log.d(TAG, "Friend request sent successfully")
                    // The real-time listener will automatically update the sentRequests list
                    _operationResult.value = Result.Success("Friend request sent")
                }
                is Result.Error -> {
                    Log.e(TAG, "Failed to send friend request", result.exception)
                    _operationResult.value = Result.Error(result.exception)
                }
            }
        }
    }

    fun acceptFriendRequest(request: FriendRequest) {
        viewModelScope.launch {
            Log.d(TAG, "Accepting friend request: ${request.id} from ${request.fromEmail}")

            if (request.id.isEmpty()) {
                Log.e(TAG, "Cannot accept friend request: request ID is empty")
                _operationResult.value = Result.Error(Exception("Invalid friend request"))
                return@launch
            }

            val result = friendsRepository.acceptFriendRequest(
                requestId = request.id,
                fromEmail = request.fromEmail,
                toEmail = request.toEmail
            )

            when (result) {
                is Result.Success -> {
                    Log.d(TAG, "Friend request accepted successfully")
                    // The real-time listeners will automatically update the lists
                    _operationResult.value = Result.Success("Friend request accepted")
                }
                is Result.Error -> {
                    Log.e(TAG, "Failed to accept friend request", result.exception)
                    _operationResult.value = Result.Error(result.exception)
                }
            }
        }
    }

    fun declineFriendRequest(request: FriendRequest) {
        viewModelScope.launch {
            Log.d(TAG, "Declining friend request: ${request.id}")

            if (request.id.isEmpty()) {
                Log.e(TAG, "Cannot decline friend request: request ID is empty")
                _operationResult.value = Result.Error(Exception("Invalid friend request"))
                return@launch
            }

            val result = friendsRepository.declineFriendRequest(request.id)

            when (result) {
                is Result.Success -> {
                    Log.d(TAG, "Friend request declined successfully")
                    // The real-time listener will automatically update the receivedRequests list
                    _operationResult.value = Result.Success("Friend request declined")
                }
                is Result.Error -> {
                    Log.e(TAG, "Failed to decline friend request", result.exception)
                    _operationResult.value = Result.Error(result.exception)
                }
            }
        }
    }

    fun cancelFriendRequest(request: FriendRequest) {
        viewModelScope.launch {
            Log.d(TAG, "Canceling friend request: ${request.id}")

            if (request.id.isEmpty()) {
                Log.e(TAG, "Cannot cancel friend request: request ID is empty")
                _operationResult.value = Result.Error(Exception("Invalid friend request"))
                return@launch
            }

            val result = friendsRepository.cancelFriendRequest(request.id)

            when (result) {
                is Result.Success -> {
                    Log.d(TAG, "Friend request cancelled successfully")
                    // The real-time listener will automatically update the sentRequests list
                    _operationResult.value = Result.Success("Friend request cancelled")
                }
                is Result.Error -> {
                    Log.e(TAG, "Failed to cancel friend request", result.exception)
                    _operationResult.value = Result.Error(result.exception)
                }
            }
        }
    }

    fun removeFriend(friend: Friend) {
        viewModelScope.launch {
            val userEmail = auth.currentUser?.email
            if (userEmail == null) {
                Log.e(TAG, "Cannot remove friend: user not logged in")
                _operationResult.value = Result.Error(Exception("User not logged in"))
                return@launch
            }

            Log.d(TAG, "Removing friend: ${friend.email}")

            val result = friendsRepository.removeFriend(userEmail, friend.email)

            when (result) {
                is Result.Success -> {
                    Log.d(TAG, "Friend removed successfully")
                    // The real-time listener will automatically update the friends list
                    _operationResult.value = Result.Success("Friend removed")
                }
                is Result.Error -> {
                    Log.e(TAG, "Failed to remove friend", result.exception)
                    _operationResult.value = Result.Error(result.exception)
                }
            }
        }
    }

    fun searchUsers(query: String) {
        if (query.length < 2) {
            _searchResults.value = emptyList()
            return
        }

        viewModelScope.launch {
            val userEmail = auth.currentUser?.email ?: return@launch
            Log.d(TAG, "Searching users with query: $query")

            when (val result = friendsRepository.searchUsers(query, userEmail)) {
                is Result.Success -> {
                    _searchResults.value = result.data
                }
                is Result.Error -> {
                    _searchResults.value = emptyList()
                    _operationResult.value = Result.Error(result.exception)
                }
            }
        }
    }

    fun clearSearchResults() {
        _searchResults.value = emptyList()
    }

    fun clearOperationResult() {
        _operationResult.value = null
    }

    /**
     * Resolve friendship status based on locally cached data.
     * This provides instant feedback without network calls.
     */
    fun resolveFriendshipStatus(otherUserEmail: String): FriendshipStatus {
        val friends = _friends.value ?: emptyList()
        val sentRequests = _sentRequests.value ?: emptyList()
        val receivedRequests = _receivedRequests.value ?: emptyList()

        return when {
            friends.any { it.email == otherUserEmail } -> FriendshipStatus.FRIENDS
            sentRequests.any { it.toEmail == otherUserEmail } -> FriendshipStatus.REQUEST_SENT
            receivedRequests.any { it.fromEmail == otherUserEmail } -> FriendshipStatus.REQUEST_RECEIVED
            else -> FriendshipStatus.NOT_FRIENDS
        }
    }

    /**
     * Get friendship status from server. Use this when you need fresh data.
     */
    fun getFriendshipStatus(otherUserEmail: String, callback: (FriendshipStatus) -> Unit) {
        viewModelScope.launch {
            val userEmail = auth.currentUser?.email ?: return@launch
            when (val result = friendsRepository.getFriendshipStatus(userEmail, otherUserEmail)) {
                is Result.Success -> callback(result.data)
                is Result.Error -> callback(FriendshipStatus.NOT_FRIENDS)
            }
        }
    }
}