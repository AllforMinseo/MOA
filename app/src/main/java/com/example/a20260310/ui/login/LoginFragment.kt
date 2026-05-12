package com.example.a20260310.ui.login

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.a20260310.R
import com.example.a20260310.data.auth.TokenManager
import com.example.a20260310.data.repository.AuthRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import retrofit2.HttpException

/**
 * 로그인 화면.
 */
class LoginFragment : Fragment(R.layout.fragment_login) {
    private val authRepository = AuthRepository()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        TokenManager.init(requireContext())

        val emailInput = view.findViewById<TextInputEditText>(R.id.emailInput)
        val passwordInput = view.findViewById<TextInputEditText>(R.id.passwordInput)
        val loginButton = view.findViewById<MaterialButton>(R.id.loginButton)
        val signupButton = view.findViewById<MaterialButton>(R.id.signupButton)

        loginButton.setOnClickListener {
            val username = emailInput.text?.toString()?.trim().orEmpty()
            val password = passwordInput.text?.toString().orEmpty()

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(requireContext(), R.string.login_error_empty, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            login(username, password, loginButton)
        }

        signupButton.setOnClickListener {
            findNavController().navigate(R.id.action_loginFragment_to_signupFragment)
        }
    }

    private fun login(
        username: String,
        password: String,
        loginButton: MaterialButton,
    ) {
        setLoading(loginButton, true)
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                authRepository.login(username, password)
            }.onSuccess {
                findNavController().navigate(R.id.action_loginFragment_to_homeFragment)
            }.onFailure { error ->
                showError(error, R.string.login_failed)
                setLoading(loginButton, false)
            }
        }
    }

    private fun setLoading(
        loginButton: MaterialButton,
        loading: Boolean,
    ) {
        loginButton.isEnabled = !loading
    }

    private fun showError(error: Throwable, fallbackRes: Int) {
        val message =
            if (error is HttpException) {
                error.response()?.errorBody()?.string()?.takeIf { it.isNotBlank() }
            } else {
                error.message
            } ?: getString(fallbackRes)

        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}
