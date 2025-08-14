package com.uniaball.gputest;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.opengl.GLSurfaceView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.view.ViewGroup;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;  // 添加缺失的GL10导入

public class MainActivity extends Activity {
	
	private TextView gpuInfoText;
	private GLSurfaceView glSurfaceView;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		Button glTestBtn = findViewById(R.id.glTestBtn);
		Button vulkanTestBtn = findViewById(R.id.vulkanTestBtn);
		gpuInfoText = findViewById(R.id.gpuInfoText);
		
		glTestBtn.setOnClickListener(v -> startGLTest());
		vulkanTestBtn.setOnClickListener(v -> showVulkanTest());
		
		initGLInfoDetector();
	}
	
	private void initGLInfoDetector() {
		glSurfaceView = new GLSurfaceView(this);
		glSurfaceView.setEGLContextClientVersion(3);
		glSurfaceView.setRenderer(new GLSurfaceView.Renderer() {
			@Override
			public void onSurfaceCreated(GL10 gl, EGLConfig config) {  // 使用GL10类型参数
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
			public void onSurfaceChanged(GL10 gl, int width, int height) {}  // 使用GL10类型参数
			
			@Override
			public void onDrawFrame(GL10 gl) {}  // 添加缺失的onDrawFrame实现
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