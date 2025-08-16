package com.uniaball.gputest;

import android.content.Intent;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Build;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import androidx.activity.EdgeToEdge;

public class MainActivity extends AppCompatActivity {
    
    private TextView gpuInfoText;
    private GLSurfaceView glSurfaceView;
    private MaterialToolbar toolbar;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        EdgeToEdge.enable(this);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      getWindow().setNavigationBarContrastEnforced(false);
    }

        // 初始化Material Toolbar
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar); // 关键修复：设置支持ActionBar
        
        // 添加Button类型声明
        Button glTestBtn = findViewById(R.id.glTestBtn);
        Button vulkanTestBtn = findViewById(R.id.vulkanTestBtn);
        gpuInfoText = findViewById(R.id.gpuInfoText);
        
        glTestBtn.setOnClickListener(v -> startGLTest());
        vulkanTestBtn.setOnClickListener(v -> showVulkanTest());
        
        initGLInfoDetector();
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // 为Toolbar创建选项菜单
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // 处理菜单项选择
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            // 启动设置Activity
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    private void initGLInfoDetector() {
        glSurfaceView = new GLSurfaceView(this);
        glSurfaceView.setEGLContextClientVersion(3);
        glSurfaceView.setRenderer(new GLSurfaceView.Renderer() {
            @Override
            public void onSurfaceCreated(GL10 gl, EGLConfig config) {
                final String gpu = GLES32Utils.getGPUInfo();
                final String glVersion = GLES32Utils.getGLVersion();
                final boolean vulkanSupported = VulkanUtils.isSupported(MainActivity.this);
                
                runOnUiThread(() -> {
                    gpuInfoText.setText(String.format("GPU: %s\nOpenGL: %s\nVulkan: %s",
                    gpu, glVersion, vulkanSupported ? "支持" : "不支持"));
                    
                    if (glSurfaceView.getParent() != null) {
                        ((ViewGroup) glSurfaceView.getParent()).removeView(glSurfaceView);
                    }
                });
            }
            
            @Override
            public void onSurfaceChanged(GL10 gl, int width, int height) {}
            
            @Override
            public void onDrawFrame(GL10 gl) {}
        });
        
        addContentView(glSurfaceView, new ViewGroup.LayoutParams(1, 1));
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        if (glSurfaceView != null) glSurfaceView.onResume();
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        if (glSurfaceView != null) glSurfaceView.onPause();
    }
    
    private void startGLTest() {
        startActivity(new Intent(this, GLTestActivity.class));
    }
    
    private void showVulkanTest() {
        Toast.makeText(this,
        VulkanUtils.isSupported(this) ? "支持Vulkan 1.1" : "不支持Vulkan 1.1",
        Toast.LENGTH_SHORT).show();
    }
}
