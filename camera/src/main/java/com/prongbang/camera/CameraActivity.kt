package com.prongbang.camera

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import com.karumi.dexter.Dexter
import com.prongbang.camera.databinding.ActivityCameraBinding
import com.prongbang.dexter.DexterPermissionsUtility
import com.prongbang.dexter.MultipleCheckPermissionsListenerImpl
import com.prongbang.dexter.PermissionsCheckerListenerImpl
import com.prongbang.dexter.PermissionsGranted
import com.prongbang.dexter.PermissionsUtility
import com.prongbang.dexter.SingleCheckPermissionListenerImpl
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService

typealias LumaListener = (luma: Double) -> Unit

class CameraActivity : AppCompatActivity() {

	private var imageCapture: ImageCapture? = null
	private lateinit var outputDirectory: File
	private lateinit var cameraExecutor: ExecutorService

	private val permissionsUtility: PermissionsUtility by lazy {
		DexterPermissionsUtility(
				Dexter.withContext(this),
				SingleCheckPermissionListenerImpl(),
				MultipleCheckPermissionsListenerImpl(),
				PermissionsCheckerListenerImpl(this)
		)
	}

	private val binding: ActivityCameraBinding by lazy {
		ActivityCameraBinding.inflate(layoutInflater)
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(binding.root)

		// Request camera permissions
		startCameraWithPermission()

		// Set up the listener for take photo button
		binding.cameraCaptureButton.setOnClickListener { takePhoto() }

		outputDirectory = getOutputDirectory()

		cameraExecutor = Executors.newSingleThreadExecutor()
	}

	private fun startCameraWithPermission() {
		permissionsUtility.checkCameraGranted(object : PermissionsGranted() {
			override fun onGranted() {
				startCamera()
			}

			override fun onDenied() {
				Toast.makeText(this@CameraActivity, "Allow Camera permission denied",
						Toast.LENGTH_SHORT)
						.show()
			}

			override fun onNotShowAgain() {
				Toast.makeText(this@CameraActivity, "Allow Camera permission not show again",
						Toast.LENGTH_SHORT)
						.show()
			}
		})
	}

	private fun takePhoto() {
		// Get a stable reference of the modifiable image capture use case
		val imageCapture = imageCapture ?: return

		// Create time-stamped output file to hold the image
		val photoFile = File(
				outputDirectory,
				SimpleDateFormat(FILENAME_FORMAT, Locale.US
				).format(System.currentTimeMillis()) + ".jpg")

		// Create output options object which contains file + metadata
		val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

		// Set up image capture listener, which is triggered after photo has
		// been taken
		imageCapture.takePicture(
				outputOptions, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
			override fun onError(exc: ImageCaptureException) {
				Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
			}

			override fun onImageSaved(output: ImageCapture.OutputFileResults) {
				val savedUri = Uri.fromFile(photoFile)
				val msg = "Photo capture succeeded: $savedUri"
				Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
				Log.d(TAG, msg)
			}
		})
	}

	private fun startCamera() {
		val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

		cameraProviderFuture.addListener({
			// Used to bind the lifecycle of cameras to the lifecycle owner
			val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

			// Preview
			val preview = Preview.Builder()
					.build()
					.also {
						it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
					}

			imageCapture = ImageCapture.Builder()
					.build()

			val imageAnalyzer = ImageAnalysis.Builder()
					.build()
					.also {
						it.setAnalyzer(cameraExecutor, LuminosityAnalyzer { luma ->
							Log.d(TAG, "Average luminosity: $luma")
						})
					}

			// Select back camera as a default
			val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

			try {
				// Unbind use cases before rebinding
				cameraProvider.unbindAll()

				// Bind use cases to camera
				cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, imageAnalyzer)

			} catch (exc: Exception) {
				Log.e(TAG, "Use case binding failed", exc)
			}

		}, ContextCompat.getMainExecutor(this))
	}

	private fun getOutputDirectory(): File {
		val mediaDir = externalMediaDirs.firstOrNull()
				?.let {
					File(it, "myais").apply { mkdirs() }
				}
		return if (mediaDir != null && mediaDir.exists())
			mediaDir else filesDir
	}

	override fun onDestroy() {
		super.onDestroy()
		cameraExecutor.shutdown()
	}

	companion object {
		private const val TAG = "CameraXBasic"
		private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"

		fun navigate(context: Context?) {
			context?.let {
				ContextCompat.startActivity(
						context, Intent(context, CameraActivity::class.java), null)
			}
		}
	}
}