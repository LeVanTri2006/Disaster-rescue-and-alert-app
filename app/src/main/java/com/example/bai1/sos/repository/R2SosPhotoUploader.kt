package com.example.bai1.sos.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.util.Log
import com.example.bai1.sos.network.GetPhotoUploadUrlRequest
import com.example.bai1.sos.network.SosApiClient
import com.example.bai1.sos.network.SosPhotoUploadUrlResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Uploads SOS photos to Cloudflare R2 via Presigned PUT URL.
 *
 * Flow:
 * 1. Request a Presigned PUT URL from the backend (POST /sos/photo/upload-url) asking for image/webp.
 * 2. Read the file bytes from the local [Uri] via ContentResolver, resize to max 1600px, compress to WebP.
 * 3. HTTP PUT the binary payload directly to R2 using the presigned URL.
 * 4. Return the R2 object key (NOT the presigned URL) to be stored in the SOS record.
 *
 * No R2 credentials are used or stored on the device.
 */
class R2SosPhotoUploader(
    private val context: Context,
    private val apiClient: SosApiClient
) {

    private val uploadClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    /**
     * Upload an SOS photo to R2.
     *
     * @param uri     local content:// or file:// URI of the photo
     * @param eventId currently unused (kept for API compatibility with FirebaseSosPhotoUploader)
     * @return Result.success(objectKey) or Result.failure(exception)
     */
    suspend fun uploadSosPhoto(uri: Uri, eventId: String): Result<String> = withContext(Dispatchers.IO) {
        var tempFile: File? = null
        try {
            val contentResolver = context.contentResolver

            // 1. Force WebP output
            val mimeType = "image/webp"

            // 2. Request presigned PUT URL from backend
            val urlResult = apiClient.getPhotoUploadUrl(GetPhotoUploadUrlRequest(contentType = mimeType))
            val uploadInfo = when (urlResult) {
                is SosPhotoUploadUrlResult.Success -> urlResult.response
                is SosPhotoUploadUrlResult.Failure -> {
                    Log.e(TAG, "Failed to get upload URL: ${urlResult.error.technicalReason}")
                    return@withContext Result.failure(
                        IOException("Failed to get upload URL: ${urlResult.error.technicalReason}")
                    )
                }
            }
            Log.d(TAG, "Got presigned URL for key: ${uploadInfo.objectKey}")

            // 3. Read and resize image
            val originalBytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext Result.failure(IOException("Cannot open InputStream for URI: $uri"))
                
            Log.d(TAG, "ORIGINAL_SIZE_BYTES=${originalBytes.size}")

            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(originalBytes, 0, originalBytes.size, options)
            val width = options.outWidth
            val height = options.outHeight

            var inSampleSize = 1
            while (width / inSampleSize > 1600 || height / inSampleSize > 1600) {
                inSampleSize *= 2
            }

            options.inJustDecodeBounds = false
            options.inSampleSize = inSampleSize
            var bitmap = BitmapFactory.decodeByteArray(originalBytes, 0, originalBytes.size, options)
                ?: return@withContext Result.failure(IOException("Failed to decode image from URI"))

            if (bitmap.width > 1600 || bitmap.height > 1600) {
                val scale = 1600f / maxOf(bitmap.width, bitmap.height)
                val matrix = Matrix().apply { postScale(scale, scale) }
                val scaledBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (scaledBitmap != bitmap) {
                    bitmap.recycle()
                    bitmap = scaledBitmap
                }
            }

            // 4. Compress to WebP
            tempFile = File.createTempFile("sos_upload_${UUID.randomUUID()}", ".webp", context.cacheDir)
            FileOutputStream(tempFile).use { out ->
                @Suppress("DEPRECATION")
                val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    Bitmap.CompressFormat.WEBP
                }
                bitmap.compress(format, 80, out)
            }
            bitmap.recycle()

            val compressedBytes = tempFile.readBytes()
            Log.d(TAG, "COMPRESSED_WEBP_SIZE_BYTES=${compressedBytes.size}")

            // 5. PUT binary to R2
            val mediaType = uploadInfo.contentType.toMediaType()
            val putRequest = Request.Builder()
                .url(uploadInfo.uploadUrl)
                .put(compressedBytes.toRequestBody(mediaType))
                .header("Content-Type", uploadInfo.contentType)
                .build()

            uploadClient.newCall(putRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "R2 PUT failed: HTTP ${response.code}")
                    return@withContext Result.failure(
                        IOException("R2 upload failed: HTTP ${response.code}")
                    )
                }
                Log.d(TAG, "R2 upload success! objectKey=${uploadInfo.objectKey}")
            }

            // 6. Return the object key (backend converts this to presigned GET URL on read)
            Result.success(uploadInfo.objectKey)
        } catch (e: Exception) {
            Log.e(TAG, "Upload failed", e)
            Result.failure(e)
        } finally {
            tempFile?.delete()
        }
    }

    /**
     * Upload multiple SOS photos to R2.
     * Uploads sequentially and fails fast if any upload fails.
     *
     * @param uris List of local URIs. Maximum 3.
     * @param eventId currently unused
     * @return Result.success(listOfObjectKeys) or Result.failure(exception)
     */
    suspend fun uploadSosPhotos(uris: List<Uri>, eventId: String): Result<List<String>> {
        if (uris.size > 3) {
            return Result.failure(IllegalArgumentException("Maximum 3 photos allowed"))
        }
        if (uris.isEmpty()) {
            return Result.success(emptyList())
        }

        val objectKeys = mutableListOf<String>()
        for (uri in uris) {
            val result = uploadSosPhoto(uri, eventId)
            result.fold(
                onSuccess = { key -> objectKeys.add(key) },
                onFailure = { exception -> return Result.failure(exception) }
            )
        }
        return Result.success(objectKeys)
    }

    companion object {
        private const val TAG = "R2SosPhotoUploader"
    }
}
