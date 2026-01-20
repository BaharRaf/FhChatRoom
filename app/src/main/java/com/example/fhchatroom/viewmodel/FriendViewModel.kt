package com.example.fhchatroom.viewmodel


import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fhchatroom.Injection
import com.example.fhchatroom.data.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class FriendsViewModel : ViewModel() {
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
        loadCurrentUser()
        observeFriends()
        observeReceivedRequests()
        observeSentRequests()
    }

    private fun loadCurrentUser() {
        viewModelScope.launch {
            when (val result = userRepository.getCurrentUser()) {
                is Result.Success -> _currentUser.value = result.data
                is Result.Error -> {
                    // Handle error
                }
            }
        }
    }

    private fun observeFriends() {
        val userEmail = auth.currentUser?.email ?: return
        viewModelScope.launch {
            friendsRepository.getFriends(userEmail).collect { friendsList ->
                _friends.value = friendsList
            }
        }
    }

    private fun observeReceivedRequests() {
        val userEmail = auth.currentUser?.email ?: return
        viewModelScope.launch {
            friendsRepository.getReceivedFriendRequests(userEmail).collect { requests ->
                _receivedRequests.value = requests
            }
        }
    }

    private fun observeSentRequests() {
        val userEmail = auth.currentUser?.email ?: return
        viewModelScope.launch {
            friendsRepository.getSentFriendRequests(userEmail).collect { requests ->
                _sentRequests.value = requests
            }
        }
    }

    fun sendFriendRequest(toUser: User) {
        viewModelScope.launch {
            val currentUser = _currentUser.value ?: return@launch
            val result = friendsRepository.sendFriendRequest(
                fromEmail = currentUser.email,
                toEmail = toUser.email,
                fromName = "${currentUser.firstName} ${currentUser.lastName}",
                toName = "${toUser.firstName} ${toUser.lastName}",
                fromProfilePhoto = currentUser.profilePhotoUrl
            )
            when (result) {
                is Result.Success -> {
                    val updated = listOf(result.data) + (_sentRequests.value ?: emptyList())
                        .filterNot { it.id == result.data.id }
                    _sentRequests.value = updated
                    _operationResult.value = Result.Success("Friend request sent")
                }
                is Result.Error -> _operationResult.value = Result.Error(result.exception)
            }
        }
    }

    fun acceptFriendRequest(request: FriendRequest) {
        viewModelScope.launch {
            val result = friendsRepository.acceptFriendRequest(
                requestId = request.id,
                fromEmail = request.fromEmail,
                toEmail = request.toEmail
            )
            when (result) {
                is Result.Success -> {
                    _receivedRequests.value = (_receivedRequests.value ?: emptyList())
                        .filterNot { it.id == request.id }
                    when (val userResult = userRepository.getUserByEmail(request.fromEmail)) {
                        is Result.Success -> {
                            val friend = Friend(
                                email = userResult.data.email,
                                firstName = userResult.data.firstName,
                                lastName = userResult.data.lastName,
                                profilePhotoUrl = userResult.data.profilePhotoUrl,
                                isOnline = userResult.data.isOnline
                            )
                            val current = _friends.value ?: emptyList()
                            if (current.none { it.email == friend.email }) {
                                _friends.value = listOf(friend) + current
                            }
                        }
                        is Result.Error -> {
                            // Ignore; real-time listener will refresh friend list
                        }
                    }
                    _operationResult.value = Result.Success("Friend request accepted")
                }
                is Result.Error -> _operationResult.value = Result.Error(result.exception)
            }
        }
    }

    fun declineFriendRequest(request: FriendRequest) {
        viewModelScope.launch {
            val result = friendsRepository.declineFriendRequest(request.id)
            when (result) {
                is Result.Success -> {
                    _receivedRequests.value = (_receivedRequests.value ?: emptyList())
                        .filterNot { it.id == request.id }
                    _operationResult.value = Result.Success("Friend request declined")
                }
                is Result.Error -> _operationResult.value = Result.Error(result.exception)
            }
        }
    }

    fun cancelFriendRequest(request: FriendRequest) {
        viewModelScope.launch {
            val result = friendsRepository.cancelFriendRequest(request.id)
            when (result) {
                is Result.Success -> {
                    _sentRequests.value = (_sentRequests.value ?: emptyList())
                        .filterNot { it.id == request.id }
                    _operationResult.value = Result.Success("Friend request cancelled")
                }
                is Result.Error -> _operationResult.value = Result.Error(result.exception)
            }
        }
    }

    fun removeFriend(friend: Friend) {
        viewModelScope.launch {
            val userEmail = auth.currentUser?.email ?: return@launch
            val result = friendsRepository.removeFriend(userEmail, friend.email)
            when (result) {
                is Result.Success -> _operationResult.value = Result.Success("Friend removed")
                is Result.Error -> _operationResult.value = Result.Error(result.exception)
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
            when (val result = friendsRepository.searchUsers(query, userEmail)) {
                is Result.Success -> _searchResults.value = result.data
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