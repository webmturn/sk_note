package com.sknote.app

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.sknote.app.databinding.ActivitySplashBinding
import com.sknote.app.util.AppUpdateManager
import com.sknote.app.util.StartupWarmupManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private var launched = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        splashScreen.setOnExitAnimationListener { splashScreenView ->
            splashScreenView.remove()
        }
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        startIntroAnimation()

        StartupWarmupManager.prefetchCurrentUser()
        StartupWarmupManager.prefetchHomeData()
        AppUpdateManager.prefetchForStartup(this)

        // 原实现是 postDelayed(1800)：无论预热是 100ms 完成还是没完成，都固定等 1.8s。
        // 改为「最短展示时间」与「预热完成（上限 MAX_WAIT_MS）」两者都满足即进入。
        // 超时后照常进入，未完成的预热继续在后台跑，首页由骨架屏兜底。
        lifecycleScope.launch {
            val minShow = launch { delay(MIN_SHOW_MS) }
            val warmup = launch { StartupWarmupManager.awaitWarmup(MAX_WAIT_MS) }
            minShow.join()
            warmup.join()
            launchMain()
        }
    }

    /**
     * 入场动画。总时长 300+260+220 = 780ms，刻意压在 [MIN_SHOW_MS] 之内：
     * 原先 600+500+400 = 1500ms 的序列，在 1.8s 的启动页里也只剩 300ms 静置，
     * 改成自适应时长后更必须留给页面稳定展示的时间。
     *
     * 插值器逐个子动画设置，而不是 set.interpolator —— 后者会把同一个插值器套到
     * 所有子动画上（AOSP AnimatorSet:716-719），包括 alpha。弹性曲线用在 alpha 上
     * 没有意义：透明度会提前顶到 1 再被钳制，观感是「闪一下」。
     * 所以弹性只给缩放，淡入与位移用减速曲线。
     */
    private fun startIntroAnimation() {
        val logoAlpha = ObjectAnimator.ofFloat(binding.ivLogo, "alpha", 0f, 1f).setDuration(LOGO_MS)
        val logoScaleX = ObjectAnimator.ofFloat(binding.ivLogo, "scaleX", 0.88f, 1f).setDuration(LOGO_MS)
        val logoScaleY = ObjectAnimator.ofFloat(binding.ivLogo, "scaleY", 0.88f, 1f).setDuration(LOGO_MS)
        val nameAlpha = ObjectAnimator.ofFloat(binding.tvAppName, "alpha", 0f, 1f).setDuration(NAME_MS)
        val nameTransY = ObjectAnimator.ofFloat(binding.tvAppName, "translationY", 16f, 0f).setDuration(NAME_MS)
        val sloganAlpha = ObjectAnimator.ofFloat(binding.tvSlogan, "alpha", 0f, 1f).setDuration(SLOGAN_MS)

        listOf(logoAlpha, nameAlpha, sloganAlpha, nameTransY).forEach {
            it.interpolator = DecelerateInterpolator()
        }
        listOf(logoScaleX, logoScaleY).forEach {
            it.interpolator = OvershootInterpolator(1.15f)
        }

        AnimatorSet().apply {
            play(logoAlpha).with(logoScaleX).with(logoScaleY)
            play(nameAlpha).with(nameTransY).after(logoAlpha)
            play(sloganAlpha).after(nameAlpha)
            start()
        }
    }

    /** 幂等：协程与最短展示时间可能同时满足，避免重复跳转。 */
    private fun launchMain() {
        if (launched || isFinishing || isDestroyed) return
        launched = true
        startActivity(Intent(this, MainActivity::class.java))
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    companion object {
        /** 启动页最短展示时间：保证品牌动画（780ms）能播完并留一点静置 */
        private const val MIN_SHOW_MS = 900L

        /**
         * 等待预热的上限。取值依据来自真机实测（见 UI_REVIEW 第十七批）：
         * `api/home` 冷启动约 2.7s、热态约 0.47s，故预热耗时会落在「约 0.5s」或
         * 「约 3s」两档。等 3s 不可接受，所以上限只取 1400ms：
         *   - 后端已热：预热很快结束，受 MIN_SHOW_MS 支配，启动页约 900ms，
         *     且首页能直接命中预热缓存、不闪骨架屏
         *   - 后端冷：到点就走，首页自己再请求一次（此时后端已被预热捂热，
         *     约 0.5s 返回），代价是一次短骨架屏
         * 原先固定 1800ms 反而两头不讨好：既没等到预热（实测 2.8–3.9s），
         * 又白等了 1.8s。
         */
        private const val MAX_WAIT_MS = 1100L

        private const val LOGO_MS = 300L
        private const val NAME_MS = 260L
        private const val SLOGAN_MS = 220L
    }
}
