package com.leonoretech.tiktokcontroller

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

/**
 * Splash / loading screen displaying the "Leonore Tech Team" brand
 * before navigating to the main dashboard.
 */
class SplashActivity : AppCompatActivity() {

    private val splashDelayMillis = 1800L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, splashDelayMillis)
    }
}
