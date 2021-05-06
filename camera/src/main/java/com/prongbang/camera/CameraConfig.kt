package com.prongbang.camera

import androidx.camera.core.AspectRatio
import androidx.camera.core.impl.ImageOutputConfig.RotationValue

data class CameraConfig(
		@RotationValue
		val rotation: Int = 0,
		@AspectRatio.Ratio
		val aspectRatio: Int = AspectRatio.RATIO_4_3
)