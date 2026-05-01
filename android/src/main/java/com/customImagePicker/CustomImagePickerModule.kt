package com.customImagePicker

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.facebook.react.bridge.*

class CustomImagePickerModule(
  private val reactContext: ReactApplicationContext
) : ReactContextBaseJavaModule(reactContext), ActivityEventListener{

  private var promise: Promise? = null
  private var cameraImageUri: Uri? = null

  companion object {
    const val REQUEST_PICK_IMAGE = 1001
    const val REQUEST_TAKE_PHOTO = 1002
  }

  init {
    reactContext.addActivityEventListener(this)
  }

  override fun getName(): String {
    return "CustomImagePicker"
  }

  @ReactMethod
  fun pickImage(promise: Promise) {
    val activity = getCurrentActivity() ?: run {
      promise.reject("NO_ACTIVITY", "No activity")
      return
    }

    this.promise = promise

    val intent = if (Build.VERSION.SDK_INT >= 33) {
      // Android 13+ Photo Picker
      Intent(MediaStore.ACTION_PICK_IMAGES)
    } else {
      // Fallback (no permission)
      Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        type = "image/*"
        addCategory(Intent.CATEGORY_OPENABLE)
      }
    }

    activity.startActivityForResult(intent, REQUEST_PICK_IMAGE)
  }

  @ReactMethod
fun takePhoto(promise: Promise) {
  val activity = getCurrentActivity() ?: run {
    promise.reject("NO_ACTIVITY", "No activity")
    return
  }

  if (!activity.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
    promise.reject("NO_CAMERA", "No camera available")
    return
  }

  this.promise = promise

  try {
    val photoFile = createImageFile(activity)
    val authority = "${reactContext.packageName}.fileprovider"

    cameraImageUri = FileProvider.getUriForFile(
      activity,
      authority,
      photoFile
    )

    val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
      putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri)
      addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    activity.startActivityForResult(intent, REQUEST_TAKE_PHOTO)

  } catch (e: Exception) {
    promise.reject("FILE_ERROR", e.message)
  }
}

  override fun onActivityResult(
  activity: Activity,
  requestCode: Int,
  resultCode: Int,
  data: Intent?
) {
    val promise = this.promise ?: return

    if (resultCode != Activity.RESULT_OK) {
      promise.reject("CANCELLED", "User cancelled")
      this.promise = null
      return
    }

    when (requestCode) {
      REQUEST_PICK_IMAGE -> {
        val uri: Uri? = data?.data
        if (uri != null) {
          promise.resolve(uri.toString())
        } else {
          promise.reject("NO_IMAGE", "No image selected")
        }
      }

      REQUEST_TAKE_PHOTO -> {
        if (cameraImageUri != null) {
            promise.resolve(cameraImageUri.toString())
        } else {
            promise.reject("NO_IMAGE", "Image capture failed")
        }
    }
    }

    this.promise = null
  }

  @Throws(Exception::class)
    private fun createImageFile(activity: Activity): File {
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val storageDir = activity.cacheDir
    return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
    }

  override fun onNewIntent(intent: Intent) {
  // required but unused
    }
  
}