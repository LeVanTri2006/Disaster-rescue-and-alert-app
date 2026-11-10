package com.example.bai1.sos.network

import android.content.Context
import com.example.bai1.auth.session.SharedPreferencesTokenStorage
import com.example.bai1.sos.domain.SosTransportMode
import com.example.bai1.sos.domain.SosUrgency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import android.util.Log
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.Instant
import java.util.concurrent.TimeUnit

interface AccessTokenProvider {
    fun getAccessToken(): String?
}

/** Reads the access token written by the app authentication layer. */
class SharedPreferencesAccessTokenProvider(context: Context) : AccessTokenProvider {
    private val tokenStorage = SharedPreferencesTokenStorage(context)

    override fun getAccessToken(): String? = tokenStorage.getAccessToken()

    companion object {
        const val PREFERENCES_NAME = SharedPreferencesTokenStorage.PREFERENCES_NAME
        const val ACCESS_TOKEN_KEY = SharedPreferencesTokenStorage.ACCESS_TOKEN_KEY
    }
}

interface SosApiClient {
    suspend fun createSos(request: CreateSosRequest): SosApiResult
    suspend fun getActiveSos(): SosApiResultList
    suspend fun getPhotoUploadUrl(request: GetPhotoUploadUrlRequest): SosPhotoUploadUrlResult
}

class OkHttpSosApiClient(
    private val baseUrl: String,
    private val tokenProvider: AccessTokenProvider,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
) : SosApiClient {
    private val createSosUrl = baseUrl.trimEnd('/') + "/sos"
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    init {
        android.util.Log.d("API_CONFIG", "SOS_API_BASE_URL=${baseUrl}")
    }

    override suspend fun createSos(request: CreateSosRequest): SosApiResult = withContext(Dispatchers.IO) {
        val token = tokenProvider.getAccessToken()?.trim().orEmpty()
        if (token.isEmpty()) {
            return@withContext SosApiResult.Failure(
                SosApiError(SosApiErrorKind.NO_TOKEN, "No access token available")
            )
        }

        val body = JSONObject().apply {
            put("adults", request.adults)
            put("children", request.children)
            put("elderly", request.elderly)
            put("neededItems", JSONArray(request.neededItems.toList()))
            put("condition", request.condition)
            request.description?.let { put("description", it) }
            put("latitude", request.latitude)
            put("longitude", request.longitude)
            request.accuracyMeters?.let { put("accuracyMeters", it) }
            request.batteryPercent?.let { put("batteryPercent", it) }
            put("urgency", request.urgency.name)
            put("timestamp", request.timestampIso())
            request.photoUrl?.let { put("photoUrl", it) }
            request.photos?.let { put("photos", JSONArray(it)) }
        }.toString().toRequestBody(jsonMediaType)

        val httpRequest = Request.Builder()
            .url(createSosUrl)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .post(body)
            .build()

        try {
            client.newCall(httpRequest).execute().use { response ->
                val responseText = response.body?.string().orEmpty()
                Log.d("MAP_DEBUG", "MAP_DEBUG_HTTP_STATUS=${response.code}")
                if (!response.isSuccessful) {
                    return@withContext SosApiResult.Failure(
                        SosApiError(errorKind(response.code), "HTTP ${response.code}")
                    )
                }
                runCatching { parseEvent(JSONObject(responseText).getJSONObject("data")) }
                    .fold(
                        onSuccess = { SosApiResult.Success(it) },
                        onFailure = { SosApiResult.Failure(SosApiError(SosApiErrorKind.UNKNOWN, "Invalid SOS response")) }
                    )
            }
        } catch (_: SocketTimeoutException) {
            SosApiResult.Failure(SosApiError(SosApiErrorKind.TIMEOUT, "SOS request timed out"))
        } catch (_: IOException) {
            SosApiResult.Failure(SosApiError(SosApiErrorKind.NO_INTERNET, "SOS request could not reach server"))
        } catch (_: RuntimeException) {
            SosApiResult.Failure(SosApiError(SosApiErrorKind.UNKNOWN, "SOS request failed"))
        }
    }

    override suspend fun getActiveSos(): SosApiResultList = withContext(Dispatchers.IO) {
        Log.d("MAP_DEBUG", "MAP_DEBUG_REQUEST_START")
        val token = tokenProvider.getAccessToken()?.trim().orEmpty()
        if (token.isEmpty()) {
            return@withContext SosApiResultList.Failure(
                SosApiError(SosApiErrorKind.NO_TOKEN, "No access token available")
            )
        }

        val httpRequest = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/sos/active")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .get()
            .build()

        try {
            client.newCall(httpRequest).execute().use { response ->
                val responseText = response.body?.string().orEmpty()
                Log.d("MAP_DEBUG", "MAP_DEBUG_HTTP_STATUS=${response.code}")
                if (!response.isSuccessful) {
                    return@withContext SosApiResultList.Failure(
                        SosApiError(errorKind(response.code), "HTTP ${response.code}")
                    )
                }
                runCatching { 
                    val dataArray = JSONObject(responseText).getJSONArray("data")
                    Log.d("MAP_DEBUG", "MAP_DEBUG_RESPONSE_COUNT=${dataArray.length()}")
                    val events = mutableListOf<SosEventResponse>()
                    for (i in 0 until dataArray.length()) {
                        val parsed = parseEvent(dataArray.getJSONObject(i))
                        Log.d("MAP_DEBUG", "MAP_DEBUG_EVENT=${parsed.eventId} STATUS=${parsed.status}")
                        events.add(parsed)
                    }
                    Log.d("MAP_DEBUG", "MAP_DEBUG_PARSED_COUNT=${events.size}")
                    events
                }.fold(
                    onSuccess = { SosApiResultList.Success(it) },
                    onFailure = { SosApiResultList.Failure(SosApiError(SosApiErrorKind.UNKNOWN, "Invalid SOS response")) }
                )
            }
        } catch (_: SocketTimeoutException) {
            SosApiResultList.Failure(SosApiError(SosApiErrorKind.TIMEOUT, "SOS request timed out"))
        } catch (_: IOException) {
            SosApiResultList.Failure(SosApiError(SosApiErrorKind.NO_INTERNET, "SOS request could not reach server"))
        } catch (_: RuntimeException) {
            SosApiResultList.Failure(SosApiError(SosApiErrorKind.UNKNOWN, "SOS request failed"))
        }
    }

    // ── R2 Photo Upload URL ─────────────────────────────────────────────────
    override suspend fun getPhotoUploadUrl(request: GetPhotoUploadUrlRequest): SosPhotoUploadUrlResult = withContext(Dispatchers.IO) {
        val token = tokenProvider.getAccessToken()?.trim().orEmpty()
        if (token.isEmpty()) {
            return@withContext SosPhotoUploadUrlResult.Failure(
                SosApiError(SosApiErrorKind.NO_TOKEN, "No access token available")
            )
        }

        val body = JSONObject().apply {
            put("contentType", request.contentType)
        }.toString().toRequestBody(jsonMediaType)

        val httpRequest = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/sos/photo/upload-url")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .post(body)
            .build()

        try {
            client.newCall(httpRequest).execute().use { response ->
                val responseText = response.body?.string().orEmpty()
                Log.d("R2_UPLOAD", "getPhotoUploadUrl HTTP ${response.code}")
                if (!response.isSuccessful) {
                    return@withContext SosPhotoUploadUrlResult.Failure(
                        SosApiError(errorKind(response.code), "HTTP ${response.code}")
                    )
                }
                runCatching {
                    val root = JSONObject(responseText)
                    val data = root.getJSONObject("data")
                    GetPhotoUploadUrlResponse(
                        uploadUrl = data.getString("uploadUrl"),
                        objectKey = data.getString("objectKey"),
                        expiresIn = data.getInt("expiresIn"),
                        contentType = data.getString("contentType")
                    )
                }.fold(
                    onSuccess = { SosPhotoUploadUrlResult.Success(it) },
                    onFailure = { SosPhotoUploadUrlResult.Failure(SosApiError(SosApiErrorKind.UNKNOWN, "Invalid upload URL response")) }
                )
            }
        } catch (_: SocketTimeoutException) {
            SosPhotoUploadUrlResult.Failure(SosApiError(SosApiErrorKind.TIMEOUT, "Upload URL request timed out"))
        } catch (_: IOException) {
            SosPhotoUploadUrlResult.Failure(SosApiError(SosApiErrorKind.NO_INTERNET, "Upload URL request could not reach server"))
        } catch (_: RuntimeException) {
            SosPhotoUploadUrlResult.Failure(SosApiError(SosApiErrorKind.UNKNOWN, "Upload URL request failed"))
        }
    }

    private fun parseEvent(data: JSONObject): SosEventResponse {
        val items = buildSet {
            val array = data.optJSONArray("neededItems") ?: JSONArray()
            for (index in 0 until array.length()) add(array.getString(index))
        }
        return SosEventResponse(
            eventId = data.getString("eventId"),
            adults = data.optInt("adults", 0),
            children = data.optInt("children", 0),
            elderly = data.optInt("elderly", 0),
            neededItems = items,
            condition = data.optString("condition"),
            description = data.optString("description").takeIf { it.isNotBlank() },
            latitude = data.getDouble("latitude"),
            longitude = data.getDouble("longitude"),
            accuracyMeters = data.optDouble("accuracyMeters").takeUnless { it.isNaN() }?.toFloat(),
            batteryPercent = data.optInt("batteryPercent", -1).takeIf { it >= 0 },
            createdAt = parseTimestamp(data.opt("createdAt")),
            status = data.optString("status", "ACTIVE"),
            urgency = runCatching { SosUrgency.valueOf(data.optString("urgency", "HIGH")) }
                .getOrDefault(SosUrgency.HIGH),
            transportMode = runCatching { SosTransportMode.valueOf(data.optString("transportMode", "ONLINE")) }
                .getOrDefault(SosTransportMode.ONLINE),
            photoUrl = data.optString("photoUrl", "").takeIf { it.isNotEmpty() },
            photos = data.optJSONArray("photos")?.let { array ->
                List(array.length()) { array.getString(it) }
            }
        )
    }

    private fun parseTimestamp(value: Any?): Long = when (value) {
        is Number -> value.toLong()
        is String -> value.toLongOrNull() ?: Instant.parse(value).toEpochMilli()
        else -> System.currentTimeMillis()
    }

    private fun errorKind(code: Int): SosApiErrorKind = when (code) {
        400 -> SosApiErrorKind.BAD_REQUEST
        401 -> SosApiErrorKind.UNAUTHORIZED
        403 -> SosApiErrorKind.FORBIDDEN
        404 -> SosApiErrorKind.NOT_FOUND
        409 -> SosApiErrorKind.CONFLICT
        in 500..599 -> SosApiErrorKind.SERVER
        else -> SosApiErrorKind.UNKNOWN
    }
}
