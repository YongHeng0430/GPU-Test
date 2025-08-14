package com.uniaball.gputest;

import android.opengl.GLES32;

public class GLES32Utils {
	public static String getGLVersion() {
		return GLES32.glGetString(GLES32.GL_VERSION);
	}
	
	public static String getGPUInfo() {
		return GLES32.glGetString(GLES32.GL_RENDERER);
	}
}