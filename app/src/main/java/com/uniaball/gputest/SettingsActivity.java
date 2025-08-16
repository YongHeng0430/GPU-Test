package com.uniaball.gputest;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.activity.EdgeToEdge;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;
import com.uniaball.gputest.databinding.ActivitySettingsBinding;

public class SettingsActivity extends AppCompatActivity {
    
    private ActivitySettingsBinding binding;
    private static final String PREF_NAME = "Settings";
    private static final String KEY_BALL_COUNT = "ballCount";
    private static final int MIN_BALL_COUNT = 10000;
    private static final int DEFAULT_BALL_COUNT = 100000;
    private static final int MAX_BALL_COUNT = 500000;
    private int lastValidValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        EdgeToEdge.enable(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setNavigationBarContrastEnforced(false);
        }

        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        initSlider();
    }

    private void initSlider() {
        binding.ballCountSeekBar.setValueFrom(MIN_BALL_COUNT);
        binding.ballCountSeekBar.setValueTo(MAX_BALL_COUNT);
        binding.ballCountSeekBar.setStepSize(10000f);

        int initialValue = getSphereCount(this);
        binding.ballCountSeekBar.setValue(initialValue); // 初始化滑块位置
        updateBallCountText(initialValue);
        lastValidValue = initialValue;

        binding.ballCountSeekBar.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(Slider slider) {}

            @Override
            public void onStopTrackingTouch(Slider slider) {
                int currentValue = (int) slider.getValue();
                if (currentValue > 200000 && lastValidValue <= 200000) {
                    showHighLoadWarningDialog(currentValue);
                } else {
                    lastValidValue = currentValue;
                }
            }
        });

        binding.ballCountSeekBar.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) {
                int intValue = (int) value;
                saveBallCountSetting(intValue);
                updateBallCountText(intValue);
            }
        });
    }

    private void updateBallCountText(int value) {
        binding.ballCountValue.setText(String.format("%,d", value));
    }

    private void showHighLoadWarningDialog(int selectedCount) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("高负载警告")
                .setMessage(String.format(
                    "您选择了 %,d 个球体，这可能大幅度增加GPU负载！\n\n" +
                    "建议仅在测试环境下使用此设置。",
                    selectedCount, selectedCount / 10000
                ))
                .setPositiveButton("确定", (dialog, which) -> dialog.dismiss())
                .setNegativeButton("取消", (dialog, which) -> {
                    resetToDefaultValue(); // 点击取消时触发恢复
                    dialog.dismiss();
                })
                .show();
    }

    private void resetToDefaultValue() {
        // 1. 直接设置Slider的值为默认值（关键修复）
        binding.ballCountSeekBar.setValue(DEFAULT_BALL_COUNT);
        
        // 2. 保存默认值到SharedPreferences
        saveBallCountSetting(DEFAULT_BALL_COUNT);
        
        // 3. 更新显示文本
        updateBallCountText(DEFAULT_BALL_COUNT);
        
        // 4. 更新记录的上次有效值
        lastValidValue = DEFAULT_BALL_COUNT;
    }

    private void saveBallCountSetting(int ballCount) {
        int clampedValue = clampValue(ballCount, MIN_BALL_COUNT, MAX_BALL_COUNT);
        SharedPreferences.Editor editor = getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit();
        editor.putInt(KEY_BALL_COUNT, clampedValue);
        editor.apply();
    }

    public static int getSphereCount(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        int storedValue = prefs.getInt(KEY_BALL_COUNT, DEFAULT_BALL_COUNT);
        return clampValue(storedValue, MIN_BALL_COUNT, MAX_BALL_COUNT);
    }

    private static int clampValue(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }
}
