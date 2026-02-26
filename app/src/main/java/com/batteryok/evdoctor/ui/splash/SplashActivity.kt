package com.batteryok.evdoctor.ui.splash

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.batteryok.evdoctor.BuildConfig
import com.batteryok.evdoctor.R
import com.batteryok.evdoctor.databinding.ActivitySplashBinding
import com.batteryok.evdoctor.ui.home.HomeActivity
import com.batteryok.evdoctor.utils.NetworkUtils

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private val handler = Handler(Looper.getMainLooper())
    private var isCheckingConnectivity = false

    companion object {
        private const val ANIMATION_DELAY_MS = 400L
        private const val CONNECTIVITY_CHECK_DELAY_MS = 2000L
        private const val NAVIGATE_DELAY_MS = 800L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupVersionText()
        startEntranceAnimation()
        setupClickListeners()
    }

    private fun setupVersionText() {
        val versionName = BuildConfig.VERSION_NAME
        binding.tvVersion.text = "${getString(R.string.version_prefix)} $versionName"
    }

    private fun startEntranceAnimation() {
        handler.postDelayed({
            animateLogoIn()
        }, ANIMATION_DELAY_MS)
    }

    private fun animateLogoIn() {
        // Logo group fade + slide up
        val logoFade = ObjectAnimator.ofFloat(binding.logoGroup, View.ALPHA, 0f, 1f)
        val logoSlide = ObjectAnimator.ofFloat(binding.logoGroup, View.TRANSLATION_Y, 40f, 0f)
        logoFade.duration = 600
        logoSlide.duration = 600
        logoSlide.interpolator = DecelerateInterpolator()

        // Version fade
        val versionFade = ObjectAnimator.ofFloat(binding.tvVersion, View.ALPHA, 0f, 1f)
        versionFade.duration = 600
        versionFade.startDelay = 200

        // Connectivity card fade
        val cardFade = ObjectAnimator.ofFloat(binding.connectivityCard, View.ALPHA, 0f, 1f)
        val cardSlide = ObjectAnimator.ofFloat(binding.connectivityCard, View.TRANSLATION_Y, 20f, 0f)
        cardFade.duration = 500
        cardSlide.duration = 500
        cardFade.startDelay = 300
        cardSlide.startDelay = 300

        val animSet = AnimatorSet()
        animSet.playTogether(logoFade, logoSlide, versionFade, cardFade, cardSlide)
        animSet.start()

        animSet.doOnEnd {
            handler.postDelayed({
                startConnectivityCheck()
            }, 400)
        }
    }

    private fun startConnectivityCheck() {
        if (isCheckingConnectivity) return
        isCheckingConnectivity = true

        binding.progressConnectivity.visibility = View.VISIBLE
        binding.layoutRetryActions.visibility = View.GONE
        binding.tvConnectivityStatus.text = getString(R.string.checking_connection)
        binding.ivConnectivityIcon.setImageResource(R.drawable.ic_wifi)

        handler.postDelayed({
            checkConnectivity()
        }, CONNECTIVITY_CHECK_DELAY_MS)
    }

    private fun checkConnectivity() {
        isCheckingConnectivity = false
        val isConnected = NetworkUtils.isNetworkAvailable(this)

        if (isConnected) {
            onConnectivitySuccess()
        } else {
            onConnectivityFailed()
        }
    }

    private fun onConnectivitySuccess() {
        binding.progressConnectivity.visibility = View.GONE
        binding.ivConnectivityIcon.setImageResource(R.drawable.ic_wifi)
        binding.tvConnectivityStatus.text = getString(R.string.connection_available)
        binding.tvConnectivityStatus.setTextColor(getColor(R.color.status_good))

        handler.postDelayed({
            navigateToHome()
        }, NAVIGATE_DELAY_MS)
    }

    private fun onConnectivityFailed() {
        binding.progressConnectivity.visibility = View.GONE
        binding.ivConnectivityIcon.setImageResource(R.drawable.ic_wifi_off)
        binding.tvConnectivityStatus.text = getString(R.string.no_connection)
        binding.tvConnectivityStatus.setTextColor(getColor(R.color.status_warning))
        binding.layoutRetryActions.visibility = View.VISIBLE
    }

    private fun setupClickListeners() {
        binding.btnRetry.setOnClickListener {
            startConnectivityCheck()
        }

        binding.btnContinueOffline.setOnClickListener {
            navigateToHome()
        }
    }

    private fun navigateToHome() {
        val intent = Intent(this, HomeActivity::class.java)
        startActivity(intent)
        overridePendingTransition(R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    // Extension to handle animator end callback
    private fun android.animation.Animator.doOnEnd(action: () -> Unit) {
        addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                action()
            }
        })
    }
}
