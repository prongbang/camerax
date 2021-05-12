package com.prongbang.camera.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.prongbang.camera.databinding.CameraPermissionViewBinding

class CameraPermissionView @kotlin.jvm.JvmOverloads constructor(
		context: Context,
		attrs: AttributeSet? = null,
		defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

	private var binding: CameraPermissionViewBinding = CameraPermissionViewBinding.inflate(
			LayoutInflater.from(context))
			.also { addView(it.root) }

	val enabledAccessButton get() = binding.enabledAccessButton
	val permissionDescText get() = binding.permissionDescText
}