package com.sknote.app.ui.auth

import android.os.Bundle
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        checkLoginState()

        binding.btnLogin.setOnClickListener {
            val username = binding.etUsername.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            if (username.isEmpty() || password.isEmpty()) {
                Snackbar.make(binding.root, "请填写用户名和密码", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.login(username, password)
        }

        binding.btnRegister.setOnClickListener {
            val username = binding.etUsername.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            if (username.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Snackbar.make(binding.root, "请填写所有字段", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.register(username, email, password)
        }

        binding.btnLogout.setOnClickListener {
            viewModel.logout()
        }

        binding.tvToggleMode.setOnClickListener {
            val isLogin = binding.layoutEmail.visibility == View.GONE
            binding.layoutEmail.visibility = if (isLogin) View.VISIBLE else View.GONE
            binding.btnLogin.visibility = if (isLogin) View.GONE else View.VISIBLE
            binding.btnRegister.visibility = if (isLogin) View.VISIBLE else View.GONE
            binding.tvToggleMode.text = if (isLogin) "已有账号？点击登录" else "没有账号？点击注册"
        }

        observeData()
    }

    private fun checkLoginState() {
        viewLifecycleOwner.lifecycleScope.launch {
            val isLoggedIn = ApiClient.getTokenManager().isLoggedIn().first()
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
    }

    private fun observeData() {
        viewModel.authResult.observe(viewLifecycleOwner) { result ->
            if (result.token != null && result.user != null) {
                Snackbar.make(binding.root, "登录成功", Snackbar.LENGTH_SHORT).show()
                findNavController().popBackStack()
            } else if (result.error != null) {
                Snackbar.make(binding.root, result.error, Snackbar.LENGTH_SHORT).show()
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.btnLogin.isEnabled = !isLoading
            binding.btnRegister.isEnabled = !isLoading
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
