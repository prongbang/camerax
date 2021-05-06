package com.prongbang.camera

import java.io.File

sealed class CameraState {
	object Configured : CameraState()
	object Previewed : CameraState()
	data class Saved(val data: File) : CameraState()
	data class Error(val exception: Exception) : CameraState()
}
