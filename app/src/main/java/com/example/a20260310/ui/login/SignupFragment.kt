package com.example.a20260310.ui.login

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.a20260310.R
import com.example.a20260310.data.auth.TokenManager
import com.example.a20260310.data.remote.ApiErrorParser
import com.example.a20260310.data.repository.AuthRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

class SignupFragment : Fragment(R.layout.fragment_signup) {
    companion object {
        private val USERNAME_REGEX = Regex("^[a-z][a-z0-9_.]{3,19}$")
        private val PASSWORD_REGEX = Regex("^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d!@#\$%^&*()_+=-]{8,32}$")
    }

    private val authRepository = AuthRepository()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        TokenManager.init(requireContext())

        val usernameLayout = view.findViewById<TextInputLayout>(R.id.signupUsernameLayout)
        val passwordLayout = view.findViewById<TextInputLayout>(R.id.signupPasswordLayout)
        val confirmPasswordLayout =
            view.findViewById<TextInputLayout>(R.id.signupConfirmPasswordLayout)
        val usernameInput = view.findViewById<TextInputEditText>(R.id.signupUsernameInput)
        val passwordInput = view.findViewById<TextInputEditText>(R.id.signupPasswordInput)
        val confirmPasswordInput = view.findViewById<TextInputEditText>(R.id.signupConfirmPasswordInput)
        val signupButton = view.findViewById<MaterialButton>(R.id.signupSubmitButton)
        val loginButton = view.findViewById<MaterialButton>(R.id.backToLoginButton)

        signupButton.setOnClickListener {
            val username = usernameInput.text?.toString()?.trim().orEmpty()
            val password = passwordInput.text?.toString().orEmpty()
            val confirmPassword = confirmPasswordInput.text?.toString().orEmpty()

            if (!validateInputs(
                    username = username,
                    password = password,
                    confirmPassword = confirmPassword,
                    usernameLayout = usernameLayout,
                    passwordLayout = passwordLayout,
                    confirmPasswordLayout = confirmPasswordLayout,
                )
            ) {
                return@setOnClickListener
            }

            signup(username, password, signupButton, loginButton)
        }

        loginButton.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun validateInputs(
        username: String,
        password: String,
        confirmPassword: String,
        usernameLayout: TextInputLayout,
        passwordLayout: TextInputLayout,
        confirmPasswordLayout: TextInputLayout,
    ): Boolean {
        usernameLayout.error = null
        passwordLayout.error = null
        confirmPasswordLayout.error = null

        var valid = true

        if (username.isBlank()) {
            usernameLayout.error = getString(R.string.signup_error_empty_username)
            valid = false
        } else if (!USERNAME_REGEX.matches(username)) {
            usernameLayout.error = getString(R.string.signup_error_invalid_username)
            valid = false
        }

        if (password.isBlank()) {
            passwordLayout.error = getString(R.string.signup_error_empty_password)
            valid = false
        } else if (!PASSWORD_REGEX.matches(password)) {
            passwordLayout.error = getString(R.string.signup_error_invalid_password)
            valid = false
        }

        if (confirmPassword.isBlank()) {
            confirmPasswordLayout.error = getString(R.string.signup_error_empty_password)
            valid = false
        } else if (password != confirmPassword) {
            confirmPasswordLayout.error = getString(R.string.signup_error_password_mismatch)
            valid = false
        }

        return valid
    }

    private fun signup(
        username: String,
        password: String,
        signupButton: MaterialButton,
        loginButton: MaterialButton,
    ) {
        setLoading(signupButton, loginButton, true)
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                authRepository.signup(username, password)
                authRepository.login(username, password)
            }.onSuccess {
                Toast.makeText(requireContext(), R.string.signup_complete, Toast.LENGTH_SHORT).show()
                findNavController().navigate(R.id.action_signupFragment_to_homeFragment)
            }.onFailure { error ->
                showError(error)
                setLoading(signupButton, loginButton, false)
            }
        }
    }

    private fun setLoading(
        signupButton: MaterialButton,
        loginButton: MaterialButton,
        loading: Boolean,
    ) {
        signupButton.isEnabled = !loading
        loginButton.isEnabled = !loading
    }

    private fun showError(error: Throwable) {
        val message = ApiErrorParser.message(error, getString(R.string.signup_failed))
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}
