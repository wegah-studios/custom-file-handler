package com.customFileHandler

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
import java.util.LinkedList
import java.util.UUID.randomUUID
import com.facebook.react.bridge.*

class CustomFileHandlerModule(
  private val reactContext: ReactApplicationContext
) : ReactContextBaseJavaModule(reactContext), ActivityEventListener{

  private var promise: Promise? = null
  private var cameraImageUri: Uri? = null
  private var pendingSaveSourceUri: Uri? = null
  private var pendingSaveMimeType: String? = null
  private var pendingSaveFileName: String? = null
  private val taskQueue: LinkedList<() -> Unit> = LinkedList()
  private var isProcessing = false

  companion object {
    const val REQUEST_PICK_IMAGE = 1001
    const val REQUEST_TAKE_PHOTO = 1002
    const val REQUEST_PICK_DOCUMENT = 2001
    const val REQUEST_SAVE_FILE = 3001
  }

  init {
    reactContext.addActivityEventListener(this)
  }

  override fun getName(): String {
    return "CustomFileHandler"
  }

  private fun enqueue(task: () -> Unit) {
    taskQueue.add(task)
    if (!isProcessing) {
      processNext()
    }
  }

  private fun processNext() {
    val next = taskQueue.poll()
    if (next == null) {
      isProcessing = false
      return
    }

    isProcessing = true
    next()
  }

  private fun finishPromise() {
    this.promise = null
    processNext()
  }

  private fun getUriInfo(uri: Uri): WritableMap {
    val resolver = reactApplicationContext.contentResolver

    val mime = resolver.getType(uri) ?: "application/octet-stream"

    // 🔍 Get extension from MIME
    val extension = android.webkit.MimeTypeMap.getSingleton()
    .getExtensionFromMimeType(mime)
    ?.let { ".$it" } ?: ""

    var name = "unknown"

    resolver.query(uri, null, null, null, null)?.use { cursor ->
      val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
      if (nameIndex != -1 && cursor.moveToFirst()) {
        name = cursor.getString(nameIndex)
      }
    }

    // 🧠 Prefer original extension if available
    val finalExtension = when {
      name?.contains(".") == true -> name!!.substringAfterLast(".", "")
        .let { ".$it" }
      else -> extension
    }

    val inputStream = resolver.openInputStream(uri)
      ?: throw Exception("Failed to open input stream")

    val cacheFileName = "${getUID()}$finalExtension"
    val file = File(reactApplicationContext.cacheDir, cacheFileName)

    file.outputStream().use { output ->
      inputStream.use { input ->
        input.copyTo(output)
      }
    }

    val cacheUri = Uri.fromFile(file)

    return Arguments.createMap().apply {
      putString("uri", cacheUri.toString())
      putString("originalUri", uri.toString())
      putString("mime", mime)
      putString("name", name)
    }
  }

  private fun getUID(): String {
    return randomUUID().toString().replace("-", "")
  }


  @ReactMethod
  fun pickDocument(type: String, promise: Promise) {
    enqueue {
      pickDocumentWorker(type, promise)
    }
  }

  fun pickDocumentWorker(fileType: String, promise: Promise){
    val activity = getCurrentActivity() ?: run {
      promise.reject("NO_ACTIVITY", "No activity")
      finishPromise()
        
      return
    }

    this.promise = promise

    val mimeTypes = when (fileType.lowercase()) {
      "pdf" -> arrayOf("application/pdf")

      "zip" -> arrayOf(
        "application/zip",
        "application/x-zip-compressed"
      )

      "xlsx" -> arrayOf(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
      )

      else -> {
        promise.reject("INVALID_TYPE", "Unsupported type: $fileType")
        finishPromise()
        
        return
      }
    }

    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
      addCategory(Intent.CATEGORY_OPENABLE)

      // ⚠️ Required pattern when using multiple MIME types
      type = if (mimeTypes.size == 1) mimeTypes[0] else "*/*"

      if (mimeTypes.size > 1) {
        putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
      }

      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    activity.startActivityForResult(intent, REQUEST_PICK_DOCUMENT)
  }

  @ReactMethod
  fun saveFile(sourceUri: String, promise: Promise) {
    enqueue {
      saveFileWorker(sourceUri, promise)
    }
  }

  fun saveFileWorker(sourceUri: String, promise: Promise) {
    try {
      val context = reactApplicationContext
      val resolver = context.contentResolver
      val uri = Uri.parse(sourceUri)

      val mimeType = resolver.getType(uri)
        ?: android.webkit.MimeTypeMap.getFileExtensionFromUrl(uri.toString())
            ?.let { ext ->
              android.webkit.MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(ext.lowercase())
            }
        ?: throw Exception("Unable to determine MIME type")

      val activity = getCurrentActivity() ?: run {
        promise.reject("NO_ACTIVITY", "No activity")
        finishPromise()
        
        return
      }

      // Generate filename
      val extension = android.webkit.MimeTypeMap.getSingleton()
        .getExtensionFromMimeType(mimeType)
        ?.let { ".$it" } ?: ""

      val fileName = "${getUID()}$extension"

      this.promise = promise

      // 🖼️ IMAGE FLOW
      if (mimeType.startsWith("image/")) {

        if (Build.VERSION.SDK_INT < 29) {
          promise.reject(
            "LEGACY_ANDROID",
            "Saving images to gallery not supported on Android 9 and below"
          )
          finishPromise()
        
          return
        }

        val inputStream = resolver.openInputStream(uri)
          ?: throw Exception("Unable to open input stream")

        val contentValues = android.content.ContentValues().apply {
          put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, fileName)
          put(android.provider.MediaStore.Images.Media.MIME_TYPE, mimeType)
          put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/${reactContext.applicationInfo.loadLabel(reactContext.packageManager)}")
          put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
        }

        val collection =
          android.provider.MediaStore.Images.Media.getContentUri(
            android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY
          )

        val newUri = resolver.insert(collection, contentValues)
          ?: throw Exception("Failed to create MediaStore entry")

        val outputStream = resolver.openOutputStream(newUri)
          ?: throw Exception("Unable to open output stream")

        inputStream.use { input ->
          outputStream.use { output ->
            input.copyTo(output)
          }
        }

        contentValues.clear()
        contentValues.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(newUri, contentValues, null, null)

        promise.resolve(getUriInfo(newUri))
        finishPromise()
          
        return
      }

      // 📄 DOCUMENT FLOW (pdf, zip, xlsx only)
      val allowedMimeTypes = setOf(
        "application/pdf",
        "application/zip",
        "application/x-zip-compressed",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "text/plain",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
      )

      if (!allowedMimeTypes.contains(mimeType)) {
        promise.reject("UNSUPPORTED_TYPE", "File type not supported: $mimeType")
        finishPromise()
        
        return
      }

      // Store for async write
      pendingSaveSourceUri = uri
      pendingSaveMimeType = mimeType
      pendingSaveFileName = fileName

      val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = mimeType
        putExtra(Intent.EXTRA_TITLE, fileName)
      }

      activity.startActivityForResult(intent, REQUEST_SAVE_FILE)

    } catch (e: Exception) {
      promise.reject("SAVE_FAILED", e.message)
    finishPromise()
        }

  }

  @ReactMethod
  fun pickImage(promise: Promise) {
    enqueue{
      pickImageWorker(promise)
    }
  }

  fun pickImageWorker(promise: Promise){
    val activity = getCurrentActivity() ?: run {
      promise.reject("NO_ACTIVITY", "No activity")
      finishPromise()
        
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
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
    }

    activity.startActivityForResult(intent, REQUEST_PICK_IMAGE)
  }

  @ReactMethod
  fun takePhoto(promise: Promise) {
    enqueue{
      takePhotoWorker(promise)
    }
  }

  fun takePhotoWorker(promise: Promise){
    val activity = getCurrentActivity() ?: run {
      promise.reject("NO_ACTIVITY", "No activity")
      finishPromise()
        
      return
    }

    if (!activity.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
      promise.reject("NO_CAMERA", "No camera available")
      finishPromise()
        
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

      intent.resolveActivity(activity.packageManager)?.let {
        activity.grantUriPermission(
          it.packageName,
          cameraImageUri,
          Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
      }

      activity.startActivityForResult(intent, REQUEST_TAKE_PHOTO)

    } catch (e: Exception) {
      promise.reject("FILE_ERROR", e.message)
    finishPromise()
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
      finishPromise()
      
      return
    }

    when (requestCode) {

      REQUEST_PICK_IMAGE -> {
        val uri: Uri? = data?.data
        if (uri != null) {
          try {
            reactApplicationContext.contentResolver.takePersistableUriPermission(
              uri,
              Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
          } catch (e: Exception) {
              // Ignore if persistable permission isn't supported for this URI
          }
          promise.resolve(getUriInfo(uri))
          finishPromise()
        } else {
          promise.reject("NO_IMAGE", "No image selected")
          finishPromise()
        }
      }

      REQUEST_TAKE_PHOTO -> {
        if (cameraImageUri != null) {
            promise.resolve(getUriInfo(cameraImageUri!!))
            finishPromise()
          
        } else {
            promise.reject("NO_IMAGE", "Image capture failed")
            finishPromise()
        }

      }

      REQUEST_PICK_DOCUMENT -> {
        val uri = data?.data
        if (uri != null) {
          reactApplicationContext.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
          )
          promise.resolve(getUriInfo(uri))
          finishPromise()
          
        } else {
          promise.reject("NO_FILE", "No document selected")
        finishPromise()
        }
      }

      REQUEST_SAVE_FILE -> {
        val uri = data?.data
        if (uri != null && pendingSaveSourceUri != null) {
          try {

            reactApplicationContext.contentResolver.takePersistableUriPermission(
              uri,
              Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
            )

            val resolver = reactApplicationContext.contentResolver

            val inputStream = resolver.openInputStream(pendingSaveSourceUri!!)
              ?: throw Exception("Failed to open source")

            val outputStream = resolver.openOutputStream(uri)
              ?: throw Exception("Failed to open destination")

            inputStream.use { input ->
              outputStream.use { output ->
                input.copyTo(output)
              }
            }

            promise.resolve(getUriInfo(uri))
            finishPromise()
          

          } catch (e: Exception) {
            promise.reject("SAVE_FAILED", e.message)
          finishPromise()
        } 
          finally {
            pendingSaveSourceUri = null
            pendingSaveMimeType = null
            pendingSaveFileName = null
          }

        } else {
          promise.reject("CREATE_FAILED", "No location selected")
        finishPromise()
        }
      }
    }
  }


  @Throws(Exception::class)
    private fun createImageFile(activity: Activity): File {
      val storageDir = activity.cacheDir
      return File.createTempFile(getUID(), ".jpg", storageDir)
    }

  override fun onNewIntent(intent: Intent) {
  // required but unused
    }
  
}