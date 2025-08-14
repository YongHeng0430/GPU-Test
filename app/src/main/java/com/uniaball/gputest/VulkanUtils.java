package com.uniaball.gputest;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;

public class VulkanUtils {
	public static boolean isSupported(Activity activity) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			return activity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)
			&& activity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION);
		}
		return false;
	}
}