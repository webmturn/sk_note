package com.sknote.app.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.sknote.app.databinding.FragmentProfileEditBinding

class ProfileEditFragment : Fragment() {

    private var _binding: FragmentProfileEditBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ProfileEditViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        binding.btnSaveAvatar.setOnClickListener {
            val url = binding.etAvatarUrl.text.toString().trim()
            viewModel.updateAvatar(url)
        }

        binding.btnChangePassword.setOnClickListener {
            val oldPwd = binding.etOldPassword.text.toString().trim()
            val newPwd = binding.etNewPassword.text.toString().trim()
            val confirmPwd = binding.etConfirmPassword.text.toString().trim()

            if (oldPwd.isEmpty() || newPwd.isEmpty()) {
                Snackbar.make(binding.root, "请填写密码", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (newPwd.length < 6) {
                Snackbar.make(binding.root, "新密码至少6位", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (newPwd != confirmPwd) {
                Snackbar.make(binding.root, "两次输入的密码不一致", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.changePassword(oldPwd, newPwd)
        }

        observeData()
        viewModel.loadProfile()
    }

    private fun observeData() {
        viewModel.user.observe(viewLifecycleOwner) { user ->
            binding.tvUsername.text = user.username
            binding.tvEmail.text = user.email ?: ""
            if (user.avatarUrl.isNotEmpty()) {
                binding.etAvatarUrl.setText(user.avatarUrl)
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.btnSaveAvatar.isEnabled = !isLoading
            binding.btnChangePassword.isEnabled = !isLoading
        }

        viewModel.message.observe(viewLifecycleOwner) { msg ->
            if (msg != null) {
                Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
                viewModel.clearMessage()
                if (msg.contains("密码")) {
                    binding.etOldPassword.text?.clear()
                    binding.etNewPassword.text?.clear()
                    binding.etConfirmPassword.text?.clear()
                }
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                Snackbar.make(binding.root, error, Snackbar.LENGTH_SHORT).show()
                viewModel.clearError()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
