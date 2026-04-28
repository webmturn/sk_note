package com.sknote.app.ui.auth

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.util.Patterns
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.exifinterface.media.ExifInterface
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.sknote.app.R
import com.sknote.app.databinding.FragmentProfileEditBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

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
    private var isProfileLoaded = false
    private val pickAvatarImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { uploadAvatar(it) }
    }

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
        binding.ivAvatar.setOnClickListener { pickAvatarImage.launch("image/*") }
        binding.btnUploadAvatar.setOnClickListener { pickAvatarImage.launch("image/*") }

        binding.btnSaveProfile.setOnClickListener {
            val nickname = binding.etNickname.text.toString().trim()
            val username = binding.etUsername.text.toString().trim()
            val bio = binding.etBio.text.toString().trim()
            val avatarUrl = binding.etAvatarUrl.text.toString().trim()
            if (!validateProfile(nickname, username, avatarUrl)) {
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
            binding.tvEmail.visibility = if (user.email.isNullOrBlank()) View.GONE else View.VISIBLE
            binding.etNickname.setText(user.displayName)
            binding.etUsername.setText(user.username)
            binding.etBio.setText(user.bio.orEmpty())
            binding.etAvatarUrl.setText(user.avatarUrl.orEmpty())
            renderAvatar(user.avatarUrl)
            isProfileLoaded = true
            captureInitialDraftState()
            syncHeaderPreview()
            updateUiState()
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.etNickname.isEnabled = !isLoading
            binding.etUsername.isEnabled = !isLoading
            binding.etBio.isEnabled = !isLoading
            binding.etAvatarUrl.isEnabled = !isLoading
            binding.etOldPassword.isEnabled = !isLoading
            binding.etNewPassword.isEnabled = !isLoading
            binding.etConfirmPassword.isEnabled = !isLoading
            binding.btnUploadAvatar.isEnabled = !isLoading
            binding.ivAvatar.isEnabled = !isLoading
            updateUiState()
        }

        viewModel.avatarUploadUrl.observe(viewLifecycleOwner) { url ->
            if (!url.isNullOrEmpty()) {
                binding.etAvatarUrl.setText(url)
                renderAvatar(url)
                updateUiState()
                viewModel.clearAvatarUploadUrl()
            }
        }

        viewModel.message.observe(viewLifecycleOwner) { msg ->
            if (msg != null) {
                Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
                if (msg.contains("资料更新成功")) {
                    captureInitialDraftState()
                    syncHeaderPreview()
                    findNavController().previousBackStackEntry?.savedStateHandle?.set("refresh_profile", true)
                    findNavController().previousBackStackEntry?.savedStateHandle?.set("profile_result_message", msg)
                }
                viewModel.clearMessage()
                if (msg.contains("密码")) {
                    binding.etOldPassword.text?.clear()
                    binding.etNewPassword.text?.clear()
                    binding.etConfirmPassword.text?.clear()
                }
                updateUiState()
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                when {
                    error.contains("账号") -> binding.layoutUsername.error = error
                    error.contains("头像") -> binding.layoutAvatarUrl.error = error
                    error.contains("旧密码") -> binding.layoutOldPassword.error = error
                    error.contains("新密码") -> binding.layoutNewPassword.error = error
                    else -> Snackbar.make(binding.root, error, Snackbar.LENGTH_SHORT).show()
                }
                viewModel.clearError()
            }
        }
    }

    private fun setupFieldValidation() {
        binding.etNickname.addTextChangedListener(simpleWatcher {
            binding.layoutNickname.error = null
            syncHeaderPreview()
            updateUiState()
        })
        binding.etUsername.addTextChangedListener(simpleWatcher {
            binding.layoutUsername.error = null
            syncHeaderPreview()
            updateUiState()
        })
        binding.etBio.addTextChangedListener(simpleWatcher { updateUiState() })
        binding.etAvatarUrl.addTextChangedListener(simpleWatcher {
            binding.layoutAvatarUrl.error = null
            updateAvatarPreviewFromInput()
            updateUiState()
        })
        binding.etOldPassword.addTextChangedListener(simpleWatcher {
            binding.layoutOldPassword.error = null
            updateUiState()
        })
        binding.etNewPassword.addTextChangedListener(simpleWatcher {
            binding.layoutNewPassword.error = null
            updateUiState()
        })
        binding.etConfirmPassword.addTextChangedListener(simpleWatcher {
            binding.layoutConfirmPassword.error = null
            updateUiState()
        })
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

    private fun validateProfile(nickname: String, username: String, avatarUrl: String): Boolean {
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
        if (avatarUrl.isNotEmpty() && !isValidAvatarUrl(avatarUrl)) {
            binding.layoutAvatarUrl.error = "请输入有效的 http/https 图片链接"
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

    private fun isValidAvatarUrl(url: String): Boolean {
        return (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) &&
            Patterns.WEB_URL.matcher(url).matches()
    }

    private fun updateAvatarPreviewFromInput() {
        val avatarUrl = binding.etAvatarUrl.text?.toString()?.trim().orEmpty()
        when {
            avatarUrl.isEmpty() -> renderAvatar(null)
            isValidAvatarUrl(avatarUrl) -> renderAvatar(avatarUrl)
        }
    }

    private fun syncHeaderPreview() {
        val nickname = binding.etNickname.text?.toString()?.trim().orEmpty()
        val username = binding.etUsername.text?.toString()?.trim().orEmpty()
        binding.tvUsername.text = nickname.ifEmpty { username }
    }

    private fun hasProfileChanges(): Boolean {
        return currentDraftState().copy(
            oldPassword = "",
            newPassword = "",
            confirmPassword = ""
        ) != initialDraftState.copy(
            oldPassword = "",
            newPassword = "",
            confirmPassword = ""
        )
    }

    private fun hasPasswordInput(): Boolean {
        return binding.etOldPassword.text?.isNotBlank() == true ||
            binding.etNewPassword.text?.isNotBlank() == true ||
            binding.etConfirmPassword.text?.isNotBlank() == true
    }

    private fun isPasswordReadyToSubmit(): Boolean {
        val oldPassword = binding.etOldPassword.text?.toString()?.trim().orEmpty()
        val newPassword = binding.etNewPassword.text?.toString()?.trim().orEmpty()
        val confirmPassword = binding.etConfirmPassword.text?.toString()?.trim().orEmpty()
        return oldPassword.isNotEmpty() &&
            newPassword.length >= 6 &&
            confirmPassword.isNotEmpty() &&
            newPassword == confirmPassword
    }

    private fun updateUiState() {
        val isLoading = viewModel.isLoading.value == true
        val avatarUrl = binding.etAvatarUrl.text?.toString()?.trim().orEmpty()
        val invalidAvatarUrl = avatarUrl.isNotEmpty() && !isValidAvatarUrl(avatarUrl)
        val profileChanged = isProfileLoaded && hasProfileChanges()
        val passwordInput = hasPasswordInput()
        val passwordReady = isPasswordReadyToSubmit()
        val hasDraft = profileChanged || passwordInput

        binding.btnSaveProfile.isEnabled = !isLoading && profileChanged && !invalidAvatarUrl
        binding.btnChangePassword.isEnabled = !isLoading && passwordReady
        binding.btnSaveProfile.alpha = if (binding.btnSaveProfile.isEnabled) 1f else 0.7f
        binding.btnChangePassword.alpha = if (binding.btnChangePassword.isEnabled) 1f else 0.7f
        binding.btnUploadAvatar.alpha = if (isLoading) 0.7f else 1f
        binding.ivAvatar.alpha = if (isLoading) 0.72f else 1f
        binding.tvProfileStatus.text = when {
            isLoading -> "正在同步资料..."
            !isProfileLoaded -> "正在加载资料..."
            invalidAvatarUrl -> "头像链接格式无效，暂时不能保存"
            passwordInput && !passwordReady -> "请完整填写并确认新密码"
            profileChanged -> "资料已修改，记得保存"
            passwordReady -> "密码已准备提交"
            else -> "资料已同步"
        }
        binding.tvProfileStatus.alpha = if (hasDraft || isLoading || !isProfileLoaded) 1f else 0.78f
    }

    private fun renderAvatar(url: String?) {
        if (!url.isNullOrEmpty()) {
            binding.ivAvatar.setPadding(0, 0, 0, 0)
            Glide.with(this)
                .load(url)
                .circleCrop()
                .placeholder(R.drawable.ic_account_circle)
                .into(binding.ivAvatar)
        } else {
            Glide.with(this).clear(binding.ivAvatar)
            val padding = (8 * resources.displayMetrics.density).toInt()
            binding.ivAvatar.setPadding(padding, padding, padding, padding)
            binding.ivAvatar.setImageResource(R.drawable.ic_account_circle)
        }
    }

    private fun uploadAvatar(uri: Uri) {
        val resolver = requireContext().contentResolver
        viewLifecycleOwner.lifecycleScope.launch {
            val imageBytes = withContext(Dispatchers.IO) {
                compressAvatarImage(uri, resolver)
            }
            if (imageBytes == null) {
                Snackbar.make(binding.root, "头像图片处理失败", Snackbar.LENGTH_SHORT).show()
                return@launch
            }
            viewModel.uploadAvatar(
                imageBytes = imageBytes,
                fileName = "avatar_${System.currentTimeMillis()}.jpg"
            )
        }
    }

    private fun compressAvatarImage(uri: Uri, resolver: ContentResolver): ByteArray? {
        return try {
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            resolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, boundsOptions)
            } ?: return null
            if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
                return null
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(
                    width = boundsOptions.outWidth,
                    height = boundsOptions.outHeight,
                    reqWidth = 1440,
                    reqHeight = 1440
                )
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val original = resolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, decodeOptions)
            } ?: return null
            val rotated = applyExifOrientation(original, uri, resolver)

            val maxWidth = 720
            val scaled = if (rotated.width > maxWidth) {
                val ratio = maxWidth.toFloat() / rotated.width
                Bitmap.createScaledBitmap(rotated, maxWidth, (rotated.height * ratio).toInt(), true)
            } else {
                rotated
            }

            val output = ByteArrayOutputStream()
            var quality = 85
            do {
                output.reset()
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, output)
                quality -= 10
            } while (output.size() > 850 * 1024 && quality >= 45)

            if (scaled !== rotated) scaled.recycle()
            if (rotated !== original) rotated.recycle()
            original.recycle()
            output.toByteArray()
        } catch (_: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        var currentWidth = width
        var currentHeight = height
        while (currentWidth / 2 >= reqWidth && currentHeight / 2 >= reqHeight) {
            currentWidth /= 2
            currentHeight /= 2
            inSampleSize *= 2
        }
        return inSampleSize.coerceAtLeast(1)
    }

    private fun applyExifOrientation(bitmap: Bitmap, uri: Uri, resolver: ContentResolver): Bitmap {
        val orientation = resolver.openInputStream(uri)?.use { inputStream ->
            ExifInterface(inputStream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } ?: ExifInterface.ORIENTATION_NORMAL

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.setRotate(180f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
        }

        return if (matrix.isIdentity) {
            bitmap
        } else {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
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
