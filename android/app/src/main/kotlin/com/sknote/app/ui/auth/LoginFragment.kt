package com.sknote.app.ui.auth

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.sknote.app.data.api.ApiClient
import com.sknote.app.databinding.FragmentLoginBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AuthViewModel by viewModels()
    private var isLoginMode = true
    private var pendingAuthAction: String? = null

    private fun isFragmentUsable(): Boolean {
        return _binding != null && isAdded && context != null
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        checkLoginState()
        setupFieldValidation()
        updateModeUi()

        binding.btnLogin.setOnClickListener {
            val username = binding.etUsername.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            if (!validateLogin(username, password)) {
                return@setOnClickListener
            }
            pendingAuthAction = "login"
            viewModel.login(username, password)
        }

        binding.btnRegister.setOnClickListener {
            val username = binding.etUsername.text.toString().trim()
            val nickname = binding.etNickname.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            if (!validateRegister(username, email, password)) {
                return@setOnClickListener
            }
            pendingAuthAction = "register"
            viewModel.register(username, email, password, nickname)
        }

        binding.btnLogout.setOnClickListener {
            viewModel.logout()
        }

        binding.tvToggleMode.setOnClickListener {
            isLoginMode = !isLoginMode
            clearFieldErrors()
            updateModeUi()
        }

        observeData()
    }

    private fun setupFieldValidation() {
        binding.etUsername.addTextChangedListener(simpleWatcher { binding.layoutUsername.error = null })
        binding.etNickname.addTextChangedListener(simpleWatcher { binding.layoutNickname.error = null })
        binding.etEmail.addTextChangedListener(simpleWatcher { binding.layoutEmail.error = null })
        binding.etPassword.addTextChangedListener(simpleWatcher { binding.layoutPassword.error = null })
    }

    private fun simpleWatcher(onChanged: () -> Unit): TextWatcher {
        return object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                onChanged()
            }
        }
    }

    private fun updateModeUi() {
        binding.layoutNickname.visibility = if (isLoginMode) View.GONE else View.VISIBLE
        binding.layoutEmail.visibility = if (isLoginMode) View.GONE else View.VISIBLE
        binding.btnLogin.visibility = if (isLoginMode) View.VISIBLE else View.GONE
        binding.btnRegister.visibility = if (isLoginMode) View.GONE else View.VISIBLE
        binding.tvToggleMode.text = if (isLoginMode) "没有账号？点击注册" else "已有账号？点击登录"
        binding.tvAuthModeTitle.text = if (isLoginMode) "登录账号" else "注册账号"
        binding.tvAuthModeSubtitle.text = if (isLoginMode) {
            "使用已有账号继续参与讨论和交流"
        } else {
            "创建账号后即可发布内容、参与讨论和接收通知"
        }
    }

    private fun clearFieldErrors() {
        binding.layoutUsername.error = null
        binding.layoutNickname.error = null
        binding.layoutEmail.error = null
        binding.layoutPassword.error = null
    }

    private fun validateLogin(username: String, password: String): Boolean {
        clearFieldErrors()
        var valid = true
        if (username.isEmpty()) {
            binding.layoutUsername.error = "请输入账号"
            valid = false
        }
        if (password.isEmpty()) {
            binding.layoutPassword.error = "请输入密码"
            valid = false
        }
        return valid
    }

    private fun validateRegister(username: String, email: String, password: String): Boolean {
        clearFieldErrors()
        var valid = true
        if (username.isEmpty()) {
            binding.layoutUsername.error = "请输入账号"
            valid = false
        }
        if (email.isEmpty()) {
            binding.layoutEmail.error = "请输入邮箱"
            valid = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.layoutEmail.error = "请输入有效邮箱"
            valid = false
        }
        if (password.isEmpty()) {
            binding.layoutPassword.error = "请输入密码"
            valid = false
        }
        return valid
    }

    private fun checkLoginState() {
        viewLifecycleOwner.lifecycleScope.launch {
            val isLoggedIn = ApiClient.getTokenManager().isLoggedIn().first()
            if (!isFragmentUsable()) return@launch
            if (isLoggedIn) {
                findNavController().popBackStack()
            } else {
                showLoggedOutState()
            }
        }
    }

    private fun showLoggedInState(username: String) {
        binding.layoutLogin.visibility = View.GONE
        binding.layoutLoggedIn.visibility = View.VISIBLE
        binding.tvWelcome.text = "欢迎, $username"
    }

    private fun showLoggedOutState() {
        binding.layoutLogin.visibility = View.VISIBLE
        binding.layoutLoggedIn.visibility = View.GONE
        updateModeUi()
    }

    private fun observeData() {
        viewModel.authResult.observe(viewLifecycleOwner) { result ->
            result ?: return@observe
            if (result.token != null && result.user != null) {
                Snackbar.make(
                    binding.root,
                    if (pendingAuthAction == "register") "注册成功" else "登录成功",
                    Snackbar.LENGTH_SHORT
                ).show()
                pendingAuthAction = null
                findNavController().popBackStack()
            } else if (result.error != null) {
                Snackbar.make(binding.root, result.error, Snackbar.LENGTH_SHORT).show()
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.btnLogin.isEnabled = !isLoading
            binding.btnRegister.isEnabled = !isLoading
            binding.tvToggleMode.isEnabled = !isLoading
            binding.tvToggleMode.alpha = if (isLoading) 0.5f else 1f
        }

        viewModel.loggedOut.observe(viewLifecycleOwner) { loggedOut ->
            if (loggedOut) {
                showLoggedOutState()
                Snackbar.make(binding.root, "已退出登录", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
