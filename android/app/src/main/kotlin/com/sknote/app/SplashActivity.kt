package com.sknote.app

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.sknote.app.databinding.ActivitySplashBinding
import com.sknote.app.util.AppUpdateManager
import com.sknote.app.util.StartupWarmupManager

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private val launchRunnable = Runnable {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        splashScreen.setOnExitAnimationListener { splashScreenView ->
            splashScreenView.remove()
        }
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val logoAlpha = ObjectAnimator.ofFloat(binding.ivLogo, "alpha", 0f, 1f).setDuration(600)
        val logoScale = ObjectAnimator.ofFloat(binding.ivLogo, "scaleX", 0.5f, 1f).setDuration(600)
        val logoScaleY = ObjectAnimator.ofFloat(binding.ivLogo, "scaleY", 0.5f, 1f).setDuration(600)
        val nameAlpha = ObjectAnimator.ofFloat(binding.tvAppName, "alpha", 0f, 1f).setDuration(500)
        val nameTransY = ObjectAnimator.ofFloat(binding.tvAppName, "translationY", 30f, 0f).setDuration(500)
        val sloganAlpha = ObjectAnimator.ofFloat(binding.tvSlogan, "alpha", 0f, 1f).setDuration(400)

        val set = AnimatorSet()
        set.play(logoAlpha).with(logoScale).with(logoScaleY)
        set.play(nameAlpha).with(nameTransY).after(logoAlpha)
        set.play(sloganAlpha).after(nameAlpha)
        set.interpolator = OvershootInterpolator(1.2f)
        set.start()

        StartupWarmupManager.prefetchCurrentUser()
        StartupWarmupManager.prefetchHomeData()
        AppUpdateManager.prefetchForStartup(this)

        binding.root.postDelayed(launchRunnable, 1800)
    }

    override fun onDestroy() {
        binding.root.removeCallbacks(launchRunnable)
        super.onDestroy()
    }
}
