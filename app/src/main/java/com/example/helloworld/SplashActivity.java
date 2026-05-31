package com.example.helloworld;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    private static final long SPLASH_DURATION_MS = 1900L;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        View glow = findViewById(R.id.splashGlow);
        View icon = findViewById(R.id.splashIcon);
        View title = findViewById(R.id.splashTitle);
        View tagline = findViewById(R.id.splashTagline);
        View bar = findViewById(R.id.splashBar);
        View dot1 = findViewById(R.id.splashDot1);
        View dot2 = findViewById(R.id.splashDot2);
        View dot3 = findViewById(R.id.splashDot3);

        glow.setAlpha(0f);
        glow.setScaleX(0.5f);
        glow.setScaleY(0.5f);

        icon.setAlpha(0f);
        icon.setScaleX(0.55f);
        icon.setScaleY(0.55f);

        title.setAlpha(0f);
        title.setTranslationY(24f);

        tagline.setAlpha(0f);
        tagline.setTranslationY(16f);

        bar.setScaleX(0f);
        bar.setPivotX(0f);

        dot1.setAlpha(0.25f);
        dot2.setAlpha(0.25f);
        dot3.setAlpha(0.25f);

        glow.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(900L)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();

        icon.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(650L)
                .setInterpolator(new OvershootInterpolator(1.15f))
                .start();

        title.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(220L)
                .setDuration(500L)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();

        tagline.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(380L)
                .setDuration(450L)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();

        bar.animate()
                .scaleX(1f)
                .setStartDelay(520L)
                .setDuration(700L)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();

        animateDot(dot1, 620L);
        animateDot(dot2, 760L);
        animateDot(dot3, 900L);

        handler.postDelayed(this::openMain, SPLASH_DURATION_MS);
    }

    private void animateDot(View dot, long delayMs) {
        dot.animate()
                .alpha(1f)
                .scaleX(1.35f)
                .scaleY(1.35f)
                .setStartDelay(delayMs)
                .setDuration(280L)
                .withEndAction(() -> dot.animate()
                        .alpha(0.45f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(280L)
                        .start())
                .start();
    }

    private void openMain() {
        if (isFinishing()) {
            return;
        }
        View root = findViewById(android.R.id.content);
        root.animate()
                .alpha(0f)
                .setDuration(260L)
                .withEndAction(() -> {
                    startActivity(new Intent(this, MainActivity.class));
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                    finish();
                })
                .start();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
