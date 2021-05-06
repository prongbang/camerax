package com.prongbang.camera

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.core.content.ContextCompat
import com.karumi.dexter.Dexter
import com.prongbang.camera.databinding.ActivityCameraBinding
import com.prongbang.dexter.DexterPermissionsUtility
import com.prongbang.dexter.MultipleCheckPermissionsListenerImpl
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
			cameraCaptureButton.setOnClickListener { cameraUtility.takePhoto() }
			galleryButton.setOnClickListener { }
			closeButton.setOnClickListener { finish() }
		}
	}

	private fun startCameraWithPermission() {
		permissionsUtility.checkCameraGranted(object : PermissionsGranted() {
			override fun onGranted() {
				cameraUtility.setupCamera(binding.viewFinder.surfaceProvider)
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
		fun navigate(context: Context?) {
			context?.let {
				ContextCompat.startActivity(
						context, Intent(context, CameraActivity::class.java), null)
			}
		}
	}
}