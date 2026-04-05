package com.example.fhchatroom.viewmodel

import com.example.fhchatroom.data.UserRepository
import com.example.fhchatroom.data.Result
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.example.fhchatroom.Injection
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {
    private val userRepository: UserRepository

    init {
        userRepository = UserRepository(
            FirebaseAuth.getInstance(),
            Injection.instance()
        )
    }

    private val _authResult = MutableLiveData<Result<Boolean>?>()
    val authResult: MutableLiveData<Result<Boolean>?> get() = _authResult

    fun signUp(
        email: String,
        password: String,
        firstName: String,
        lastName: String,
        studyPath: String,
        semester: Long
    ) {
        viewModelScope.launch {
            _authResult.value = userRepository.signUp(
                email = email,
                password = password,
                firstName = firstName,
                lastName = lastName,
                studyPath = studyPath,
                semester = semester
            )
        }
    }
    fun clearAuthResult() {
        _authResult.value = null
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _authResult.value = userRepository.login(email, password)
        }
    }
}
