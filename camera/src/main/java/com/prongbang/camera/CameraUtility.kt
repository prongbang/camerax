package com.prongbang.camera

import android.content.ContextWrapper
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.OnLifecycleEvent
import java.util.concurrent.ExecutorService

interface CameraUtility {
	fun setupCamera(surfaceProvider: Preview.SurfaceProvider): LiveData<CameraState>
	fun bindCamera(config: CameraConfig): LiveData<CameraState>
	fun takePhoto(): LiveData<CameraState>
	fun shutdown()
	fun switchCamera()
}

class CameraXUtility(
		private val context: ContextWrapper,
		private val lifecycleOwner: LifecycleOwner,
		private val fileDirectory: FileDirectory,
		private val executorService: ExecutorService,
) : CameraUtility, LifecycleObserver {

	private var imageCapture: ImageCapture? = null
	private var cameraProvider: ProcessCameraProvider? = null
	private var surfaceProvider: Preview.SurfaceProvider? = null
	private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
	private var cameraConfig: CameraConfig = CameraConfig()

	override fun bindCamera(config: CameraConfig): LiveData<CameraState> {
		val data = MutableLiveData<CameraState>()

		cameraConfig = config

		val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

		cameraProviderFuture.addListener({
			// Used to bind the lifecycle of cameras to the lifecycle owner
			val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

			// Preview
			val preview = Preview.Builder()
					// We request aspect ratio but no resolution
					.setTargetAspectRatio(config.aspectRatio)
					// Set initial target rotation
					.setTargetRotation(config.rotation)
					.build()
					.also {
						it.setSurfaceProvider(surfaceProvider)
					}

			// ImageCapture
			imageCapture = ImageCapture.Builder()
					.setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
					// We request aspect ratio but no resolution to match preview config, but letting
					// CameraX optimize for whatever specific resolution best fits our use cases
					.setTargetAspectRatio(config.aspectRatio)
					// Set initial target rotation, we will have to call this again if rotation changes
					// during the lifecycle of this use case
					.setTargetRotation(config.rotation)
					.build()

			// ImageAnalysis
			val imageAnalyzer = ImageAnalysis.Builder()
					// We request aspect ratio but no resolution
					.setTargetAspectRatio(config.aspectRatio)
					// Set initial target rotation, we will have to call this again if rotation changes
					// during the lifecycle of this use case
					.setTargetRotation(config.rotation)
					// The analyzer can then be assigned to the instance
					.build()
					.also {
						it.setAnalyzer(executorService, LuminosityAnalyzer { luma ->
							// Values returned from our analyzer are passed to the attached listener
							// We log image analysis results here - you should do something useful instead!
							Log.d(TAG, "Average luminosity: $luma")
						})
					}

			// CameraSelector
			val cameraSelector = CameraSelector.Builder()
					.requireLensFacing(lensFacing)
					.build()

			try {
				// Unbind use cases before rebinding
				cameraProvider.unbindAll()

				// Bind use cases to camera
				cameraProvider.bindToLifecycle(
						lifecycleOwner,
						cameraSelector,
						preview,
						imageCapture,
						imageAnalyzer
				)

				// Attach the viewfinder's surface provider to preview use case
				preview.setSurfaceProvider(surfaceProvider)

				data.postValue(CameraState.Previewed)
			} catch (exc: Exception) {
				Log.e(TAG, "Use case binding failed", exc)
				data.postValue(CameraState.Error(exc))
			}

		}, ContextCompat.getMainExecutor(context))

		return data
	}

	override fun setupCamera(surfaceProvider: Preview.SurfaceProvider): LiveData<CameraState> {
		val data = MutableLiveData<CameraState>()

		this.surfaceProvider = surfaceProvider

		val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
		cameraProviderFuture.addListener({

			// CameraProvider
			cameraProvider = cameraProviderFuture.get()

			// Select lensFacing depending on the available cameras
			lensFacing = when {
				hasBackCamera() -> CameraSelector.LENS_FACING_BACK
				hasFrontCamera() -> CameraSelector.LENS_FACING_FRONT
				else -> throw IllegalStateException("Back and front camera are unavailable")
			}

			// Build and bind the camera use cases
			data.postValue(CameraState.Configured)

			// Bind camera
			// rotation = binding.viewFinder.display.rotation,
			bindCamera(CameraConfig())

		}, ContextCompat.getMainExecutor(context))

		return data;
	}

	override fun takePhoto(): LiveData<CameraState> {
		val data = MutableLiveData<CameraState>()

		// Get a stable reference of the modifiable image capture use case
		val imageCapture = imageCapture ?: return data

		// Create time-stamped output file to hold the image
		val photoFile = fileDirectory.photoFile()

		// Create output options object which contains file + metadata
		val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile)
				.build()

		// Set up image capture listener, which is triggered after photo has been taken
		imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(context),
				object : ImageCapture.OnImageSavedCallback {
					override fun onError(exc: ImageCaptureException) {
						Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
						data.postValue(CameraState.Error(exc))
					}

					override fun onImageSaved(output: ImageCapture.OutputFileResults) {
						val savedUri = Uri.fromFile(photoFile)
						val msg = "Photo capture succeeded: $savedUri"
						Toast.makeText(context, msg, Toast.LENGTH_SHORT)
								.show()
						Log.d(TAG, msg)
						data.postValue(CameraState.Saved(photoFile))
					}
				})

		return data
	}

	@OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
	override fun shutdown() {
		executorService.shutdown()
	}

	override fun switchCamera() {
		lensFacing = if (CameraSelector.LENS_FACING_FRONT == lensFacing) {
			CameraSelector.LENS_FACING_BACK
		} else {
			CameraSelector.LENS_FACING_FRONT
		}
		// Re-bind use cases to update selected camera
		bindCamera(cameraConfig)
	}

	/** Returns true if the device has an available back camera. False otherwise */
	private fun hasBackCamera(): Boolean {
		return cameraProvider?.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ?: false
	}

	/** Returns true if the device has an available front camera. False otherwise */
	private fun hasFrontCamera(): Boolean {
		return cameraProvider?.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ?: false
	}

	companion object {
		private val TAG = CameraXUtility::class.java.simpleName;
	}
}