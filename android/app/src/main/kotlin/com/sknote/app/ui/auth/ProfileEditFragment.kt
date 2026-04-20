package com.sknote.app.ui.auth

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.sknote.app.R
import com.sknote.app.databinding.FragmentProfileEditBinding

class ProfileEditFragment : Fragment() {

    private data class ProfileDraftState(
        val nickname: String,
        val username: String,
        val bio: String,
        val avatarUrl: String,
        val oldPassword: String,
        val newPassword: String,
        val confirmPassword: String
    )

    private var _binding: FragmentProfileEditBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ProfileEditViewModel by viewModels()
    private var initialDraftState = ProfileDraftState("", "", "", "", "", "", "")

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { confirmExit() }
        setupFieldValidation()
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() { confirmExit() }
            }
        )

        binding.btnSaveProfile.setOnClickListener {
            val nickname = binding.etNickname.text.toString().trim()
            val username = binding.etUsername.text.toString().trim()
            val bio = binding.etBio.text.toString().trim()
            val avatarUrl = binding.etAvatarUrl.text.toString().trim()
            if (!validateProfile(nickname, username)) {
                return@setOnClickListener
            }
            viewModel.updateProfile(nickname, username, bio, avatarUrl)
        }

        binding.btnChangePassword.setOnClickListener {
            val oldPwd = binding.etOldPassword.text.toString().trim()
            val newPwd = binding.etNewPassword.text.toString().trim()
            val confirmPwd = binding.etConfirmPassword.text.toString().trim()

            if (!validatePasswordChange(oldPwd, newPwd, confirmPwd)) {
                return@setOnClickListener
            }
            viewModel.changePassword(oldPwd, newPwd)
        }

        observeData()
        viewModel.loadProfile()
    }

    private fun observeData() {
        viewModel.user.observe(viewLifecycleOwner) { user ->
            binding.tvUsername.text = user.displayName
            binding.tvEmail.text = user.email ?: ""
            binding.etNickname.setText(user.displayName)
            binding.etUsername.setText(user.username)
            binding.etBio.setText(user.bio.orEmpty())
            binding.etAvatarUrl.setText(user.avatarUrl.orEmpty())
            if (!user.avatarUrl.isNullOrEmpty()) {
                Glide.with(this)
                    .load(user.avatarUrl.orEmpty())
                    .circleCrop()
                    .placeholder(R.drawable.ic_account_circle)
                    .into(binding.ivAvatar)
            }
            captureInitialDraftState()
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.btnSaveProfile.isEnabled = !isLoading
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
                when {
                    error.contains("账号") -> binding.layoutUsername.error = error
                    error.contains("旧密码") -> binding.layoutOldPassword.error = error
                    error.contains("新密码") -> binding.layoutNewPassword.error = error
                    else -> Snackbar.make(binding.root, error, Snackbar.LENGTH_SHORT).show()
                }
                viewModel.clearError()
            }
        }
    }

    private fun setupFieldValidation() {
        binding.etNickname.addTextChangedListener(simpleWatcher { binding.layoutNickname.error = null })
        binding.etUsername.addTextChangedListener(simpleWatcher { binding.layoutUsername.error = null })
        binding.etAvatarUrl.addTextChangedListener(simpleWatcher { binding.layoutAvatarUrl.error = null })
        binding.etOldPassword.addTextChangedListener(simpleWatcher { binding.layoutOldPassword.error = null })
        binding.etNewPassword.addTextChangedListener(simpleWatcher { binding.layoutNewPassword.error = null })
        binding.etConfirmPassword.addTextChangedListener(simpleWatcher { binding.layoutConfirmPassword.error = null })
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

    private fun clearProfileErrors() {
        binding.layoutNickname.error = null
        binding.layoutUsername.error = null
        binding.layoutAvatarUrl.error = null
    }

    private fun clearPasswordErrors() {
        binding.layoutOldPassword.error = null
        binding.layoutNewPassword.error = null
        binding.layoutConfirmPassword.error = null
    }

    private fun validateProfile(nickname: String, username: String): Boolean {
        clearProfileErrors()
        var valid = true
        if (nickname.isEmpty()) {
            binding.layoutNickname.error = "请输入昵称"
            valid = false
        }
        if (username.isEmpty()) {
            binding.layoutUsername.error = "请输入账号"
            valid = false
        } else if (username.length < 2) {
            binding.layoutUsername.error = "账号至少2个字符"
            valid = false
        }
        return valid
    }

    private fun validatePasswordChange(oldPwd: String, newPwd: String, confirmPwd: String): Boolean {
        clearPasswordErrors()
        var valid = true
        if (oldPwd.isEmpty()) {
            binding.layoutOldPassword.error = "请输入当前密码"
            valid = false
        }
        if (newPwd.isEmpty()) {
            binding.layoutNewPassword.error = "请输入新密码"
            valid = false
        } else if (newPwd.length < 6) {
            binding.layoutNewPassword.error = "新密码至少6位"
            valid = false
        }
        if (confirmPwd.isEmpty()) {
            binding.layoutConfirmPassword.error = "请再次输入新密码"
            valid = false
        } else if (newPwd.isNotEmpty() && newPwd != confirmPwd) {
            binding.layoutConfirmPassword.error = "两次输入的密码不一致"
            valid = false
        }
        return valid
    }

    private fun captureInitialDraftState() {
        initialDraftState = currentDraftState()
    }

    private fun currentDraftState(): ProfileDraftState {
        return ProfileDraftState(
            nickname = binding.etNickname.text?.toString()?.trim().orEmpty(),
            username = binding.etUsername.text?.toString()?.trim().orEmpty(),
            bio = binding.etBio.text?.toString()?.trim().orEmpty(),
            avatarUrl = binding.etAvatarUrl.text?.toString()?.trim().orEmpty(),
            oldPassword = binding.etOldPassword.text?.toString()?.trim().orEmpty(),
            newPassword = binding.etNewPassword.text?.toString()?.trim().orEmpty(),
            confirmPassword = binding.etConfirmPassword.text?.toString()?.trim().orEmpty()
        )
    }

    private fun hasUnsavedChanges(): Boolean {
        return currentDraftState() != initialDraftState
    }

    private fun confirmExit() {
        if (!hasUnsavedChanges()) {
            findNavController().navigateUp()
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("放弃当前修改？")
            .setMessage("未保存的资料或密码输入将会丢失，确定返回吗？")
            .setPositiveButton("返回") { _, _ -> findNavController().navigateUp() }
            .setNegativeButton("继续编辑", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
