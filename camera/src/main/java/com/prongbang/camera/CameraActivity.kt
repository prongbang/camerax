package com.prongbang.camera

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.core.content.ContextCompat
import coil.load
import coil.transform.CircleCropTransformation
import com.karumi.dexter.Dexter
import com.prongbang.camera.databinding.ActivityCameraBinding
import com.prongbang.dexter.DexterPermissionsUtility
import com.prongbang.dexter.MultipleCheckPermissionsListenerImpl
import com.prongbang.dexter.PermissionsChecker
import com.prongbang.dexter.PermissionsCheckerListenerImpl
import com.prongbang.dexter.PermissionsGranted
import com.prongbang.dexter.PermissionsUtility
import com.prongbang.dexter.SingleCheckPermissionListenerImpl
import java.util.*
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {

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

	private val cameraViewModel by viewModels<CameraViewModel>()

	private val cameraUtility by lazy {
		CameraXUtility(
				context = this,
				lifecycleOwner = this,
				fileDirectory = OutputFileDirectory(this),
				executorService = Executors.newSingleThreadExecutor()
		)
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(binding.root)

		// Handling Lifecycle with Lifecycle-Aware Components
		lifecycle.addObserver(cameraUtility)

		// Request camera permissions
		startCameraWithPermission()

		bindView()
	}

	private fun bindView() {
		binding.apply {
			switchCameraButton.setOnClickListener { cameraUtility.switchCamera() }
			cameraCaptureButton.setOnClickListener { takePhoto() }
			galleryButton.setOnClickListener { }
			closeButton.setOnClickListener { finish() }
			viewFinder.enabledCameraAccessButton.setOnClickListener { requestCameraPermission() }
		}
	}

	private fun takePhoto() {
		cameraUtility.takePhoto()
				.observe(this@CameraActivity, {
					when (it) {
						is CameraState.Saved -> {
							binding.galleryButton.load(it.data) {
								crossfade(true)
								transformations(CircleCropTransformation())
							}
						}
						else -> {
							Toast.makeText(this@CameraActivity, "Error !!",
									Toast.LENGTH_SHORT)
									.show()
						}
					}
				})
		flashAnimation()
	}

	private fun flashAnimation() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			// Display flash animation to indicate that photo was captured
			binding.apply {
				cameraContainer.postDelayed({
					cameraContainer.foreground = ColorDrawable(Color.WHITE)
					cameraContainer.postDelayed(
							{ cameraContainer.foreground = null },
							ANIMATION_FAST_MILLIS)
				}, ANIMATION_SLOW_MILLIS)
			}
		}
	}

	private fun startCamera() {
		binding.viewFinder.cameraPermissionView.visibility = View.GONE
		cameraUtility.setupCamera(binding.viewFinder.surfaceProvider)
	}

	private fun startCameraWithPermission() {
		permissionsUtility.isCameraGranted(object : PermissionsChecker {
			override fun onGranted() {
				startCamera()
			}

			override fun onNotGranted() {
				binding.viewFinder.cameraPermissionView.visibility = View.VISIBLE
			}
		})
	}

	private fun requestCameraPermission() {
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

	companion object {
		const val ANIMATION_FAST_MILLIS = 50L
		const val ANIMATION_SLOW_MILLIS = 100L

		fun navigate(context: Context?) {
			context?.let {
				ContextCompat.startActivity(
						context, Intent(context, CameraActivity::class.java), null)
			}
		}
	}
}