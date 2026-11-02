package com.example.bai1.rescue.network

import android.util.Log
import com.example.bai1.sos.network.AccessTokenProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

interface RescueMissionApiClient {
    suspend fun getMyMissions(): RescueMissionApiResult<List<RescueMissionResponse>>
    suspend fun getMissionDetail(id: String): RescueMissionApiResult<RescueMissionResponse>
    suspend fun updateMissionStatus(id: String, status: String): RescueMissionApiResult<RescueMissionResponse>
    suspend fun acceptRescueRequest(requestId: String): RescueMissionApiResult<RescueMissionResponse>
    suspend fun getNearbyRequests(lat: Double, lon: Double, radius: Int = 10000): RescueMissionApiResult<List<RescueMissionRequestInfo>>
    suspend fun getRescueRequestDetail(id: String): RescueMissionApiResult<RescueMissionRequestInfo>
}

class OkHttpRescueMissionApiClient(
    private val baseUrl: String,
    private val tokenProvider: AccessTokenProvider,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
) : RescueMissionApiClient {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun getMyMissions(): RescueMissionApiResult<List<RescueMissionResponse>> = withContext(Dispatchers.IO) {
        val token = tokenProvider.getAccessToken()?.trim().orEmpty()
        if (token.isEmpty()) {
            return@withContext RescueMissionApiResult.Failure(
                RescueMissionApiError(RescueMissionApiErrorKind.NO_TOKEN, "No access token available")
            )
        }

        val httpRequest = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/rescue-missions/my")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .get()
            .build()

        try {
            client.newCall(httpRequest).execute().use { response ->
                val responseText = response.body?.string().orEmpty()
                android.util.Log.d("RESCUE_DEBUG", "HTTP ${response.code} Response: $responseText")
                if (!response.isSuccessful) {
                    return@withContext RescueMissionApiResult.Failure(
                        RescueMissionApiError(errorKind(response.code), "HTTP ${response.code}")
                    )
                }
                runCatching {
                    val dataArray = JSONObject(responseText).getJSONArray("data")
                    val missions = mutableListOf<RescueMissionResponse>()
                    for (i in 0 until dataArray.length()) {
                        missions.add(parseMission(dataArray.getJSONObject(i)))
                    }
                    missions
                }.fold(
                    onSuccess = { RescueMissionApiResult.Success(it) },
                    onFailure = { RescueMissionApiResult.Failure(RescueMissionApiError(RescueMissionApiErrorKind.UNKNOWN, "Invalid mission response")) }
                )
            }
        } catch (_: SocketTimeoutException) {
            RescueMissionApiResult.Failure(RescueMissionApiError(RescueMissionApiErrorKind.TIMEOUT, "Request timed out"))
        } catch (_: IOException) {
            RescueMissionApiResult.Failure(RescueMissionApiError(RescueMissionApiErrorKind.NO_INTERNET, "No internet connection"))
        } catch (_: RuntimeException) {
            RescueMissionApiResult.Failure(RescueMissionApiError(RescueMissionApiErrorKind.UNKNOWN, "Request failed"))
        }
    }

    override suspend fun getMissionDetail(id: String): RescueMissionApiResult<RescueMissionResponse> = withContext(Dispatchers.IO) {
        val token = tokenProvider.getAccessToken()?.trim().orEmpty()
        if (token.isEmpty()) {
            return@withContext RescueMissionApiResult.Failure(
                RescueMissionApiError(RescueMissionApiErrorKind.NO_TOKEN, "No access token available")
            )
        }

        val httpRequest = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/rescue-missions/$id")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .get()
            .build()

        try {
            client.newCall(httpRequest).execute().use { response ->
                val responseText = response.body?.string().orEmpty()
                android.util.Log.d("RESCUE_DEBUG", "HTTP ${response.code} Response: $responseText")
                if (!response.isSuccessful) {
                    return@withContext RescueMissionApiResult.Failure(
                        RescueMissionApiError(errorKind(response.code), "HTTP ${response.code}")
                    )
                }
                runCatching {
                    parseMission(JSONObject(responseText).getJSONObject("data"))
                }.fold(
                    onSuccess = { RescueMissionApiResult.Success(it) },
                    onFailure = { RescueMissionApiResult.Failure(RescueMissionApiError(RescueMissionApiErrorKind.UNKNOWN, "Invalid mission response")) }
                )
            }
        } catch (_: SocketTimeoutException) {
            RescueMissionApiResult.Failure(RescueMissionApiError(RescueMissionApiErrorKind.TIMEOUT, "Request timed out"))
        } catch (_: IOException) {
            RescueMissionApiResult.Failure(RescueMissionApiError(RescueMissionApiErrorKind.NO_INTERNET, "No internet connection"))
        } catch (_: RuntimeException) {
            RescueMissionApiResult.Failure(RescueMissionApiError(RescueMissionApiErrorKind.UNKNOWN, "Request failed"))
        }
    }

    override suspend fun updateMissionStatus(id: String, status: String): RescueMissionApiResult<RescueMissionResponse> = withContext(Dispatchers.IO) {
        val token = tokenProvider.getAccessToken()?.trim().orEmpty()
        if (token.isEmpty()) {
            return@withContext RescueMissionApiResult.Failure(
                RescueMissionApiError(RescueMissionApiErrorKind.NO_TOKEN, "No access token available")
            )
        }
        val json = JSONObject().apply { put("status", status) }
        val body = json.toString().toRequestBody("application/json".toMediaType())
        val httpRequest = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/rescue-missions/$id")
            .header("Authorization", "Bearer $token")
            .patch(body)
            .build()
        try {
            client.newCall(httpRequest).execute().use { response ->
                val responseText = response.body?.string().orEmpty()
                android.util.Log.d("RESCUE_DEBUG", "updateMissionStatus HTTP ${response.code} Response: $responseText")
                if (!response.isSuccessful) {
                    val errMsg = runCatching {
                        val obj = JSONObject(responseText)
                        if (obj.has("message")) {
                            val msg = obj.get("message")
                            if (msg is JSONArray) msg.join(", ") else msg.toString()
                        } else if (obj.has("error") && obj.get("error") is JSONObject) {
                            obj.getJSONObject("error").optString("message", "HTTP ${response.code}")
                        } else "HTTP ${response.code}"
                    }.getOrDefault("HTTP ${response.code}")
                    return@withContext RescueMissionApiResult.Failure(RescueMissionApiError(errorKind(response.code), errMsg))
                }
                val jsonObject = org.json.JSONObject(if(responseText.isEmpty()) "{}" else responseText)
                val dataObj = if (jsonObject.has("data")) jsonObject.getJSONObject("data") else jsonObject
                RescueMissionApiResult.Success(parseMission(dataObj))
            }
        } catch (_: Exception) {
            RescueMissionApiResult.Failure(RescueMissionApiError(RescueMissionApiErrorKind.UNKNOWN, "Request failed"))
        }
    }

    override suspend fun acceptRescueRequest(requestId: String): RescueMissionApiResult<RescueMissionResponse> = withContext(Dispatchers.IO) {
        val token = tokenProvider.getAccessToken()?.trim().orEmpty()
        if (token.isEmpty()) {
            return@withContext RescueMissionApiResult.Failure(
                RescueMissionApiError(RescueMissionApiErrorKind.NO_TOKEN, "No access token available")
            )
        }
        val httpRequest = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/rescue-missions/accept/$requestId")
            .header("Authorization", "Bearer $token")
            .post("{}".toRequestBody("application/json".toMediaType()))
            .build()
        try {
            client.newCall(httpRequest).execute().use { response ->
                val responseText = response.body?.string().orEmpty()
                android.util.Log.d("RESCUE_DEBUG", "HTTP ${response.code} Response: $responseText")
                if (!response.isSuccessful) {
                    var msg = "HTTP ${response.code}"
                    try { msg = JSONObject(responseText).getString("message") } catch(e: Exception){}
                    return@withContext RescueMissionApiResult.Failure(RescueMissionApiError(errorKind(response.code), msg))
                }
                runCatching {
                    parseMission(JSONObject(responseText).getJSONObject("data"))
                }.fold(
                    onSuccess = { RescueMissionApiResult.Success(it) },
                    onFailure = { RescueMissionApiResult.Failure(RescueMissionApiError(RescueMissionApiErrorKind.UNKNOWN, "Invalid mission response")) }
                )
            }
        } catch (_: Exception) {
            RescueMissionApiResult.Failure(RescueMissionApiError(RescueMissionApiErrorKind.UNKNOWN, "Request failed"))
        }
    }

    override suspend fun getNearbyRequests(lat: Double, lon: Double, radius: Int): RescueMissionApiResult<List<RescueMissionRequestInfo>> = withContext(Dispatchers.IO) {
        val token = tokenProvider.getAccessToken()?.trim().orEmpty()
        if (token.isEmpty()) {
            return@withContext RescueMissionApiResult.Failure(
                RescueMissionApiError(RescueMissionApiErrorKind.NO_TOKEN, "No access token available")
            )
        }
        val httpRequest = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/rescue-requests/nearby?lat=$lat&lon=$lon&radius=$radius")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        try {
            client.newCall(httpRequest).execute().use { response ->
                val responseText = response.body?.string().orEmpty()
                android.util.Log.d("RESCUE_DEBUG", "HTTP ${response.code} Response: $responseText")
                android.util.Log.d("RESCUE_DEBUG", "updateMissionStatus HTTP ${response.code} Response: $responseText")
                if (!response.isSuccessful) {
                    val errMsg = runCatching {
                        val obj = JSONObject(responseText)
                        if (obj.has("message")) {
                            val msg = obj.get("message")
                            if (msg is JSONArray) msg.join(", ") else msg.toString()
                        } else if (obj.has("error") && obj.get("error") is JSONObject) {
                            obj.getJSONObject("error").optString("message", "HTTP ${response.code}")
                        } else "HTTP ${response.code}"
                    }.getOrDefault("HTTP ${response.code}")
                    return@withContext RescueMissionApiResult.Failure(RescueMissionApiError(errorKind(response.code), errMsg))
                }
                runCatching {
                    val dataArray = JSONObject(responseText).getJSONArray("data")
                    val requests = mutableListOf<RescueMissionRequestInfo>()
                    for (i in 0 until dataArray.length()) {
                        val reqObj = dataArray.getJSONObject(i)
                        var rLat: Double? = null
                        var rLon: Double? = null
                        if (reqObj.has("location") && !reqObj.isNull("location")) {
                            val locObj = reqObj.getJSONObject("location")
                            if (locObj.has("coordinates")) {
                                val coords = locObj.getJSONArray("coordinates")
                                if (coords.length() >= 2) {
                                    rLon = coords.getDouble(0)
                                    rLat = coords.getDouble(1)
                                }
                            }
                        }
                        requests.add(
                            RescueMissionRequestInfo(
                                id = reqObj.getString("id"),
                                requesterId = reqObj.getString("requesterId"),
                                requesterName = if (reqObj.has("requesterName") && !reqObj.isNull("requesterName")) reqObj.getString("requesterName") else null,
                                requesterPhone = if (reqObj.has("requesterPhone") && !reqObj.isNull("requesterPhone")) reqObj.getString("requesterPhone") else null,
                                peopleCount = reqObj.optInt("peopleCount", 1),
                                urgency = reqObj.getString("urgency"),
                                description = if (reqObj.has("description") && !reqObj.isNull("description")) reqObj.getString("description") else null,
                                status = reqObj.getString("status"),
                                latitude = rLat,
                                longitude = rLon,
                                distance = if (reqObj.has("distance") && !reqObj.isNull("distance")) reqObj.getDouble("distance") else null,
                                createdAt = if (reqObj.has("createdAt") && !reqObj.isNull("createdAt")) reqObj.getString("createdAt") else null,
                                updatedAt = if (reqObj.has("updatedAt") && !reqObj.isNull("updatedAt")) reqObj.getString("updatedAt") else null,
                                sosEventId = if (reqObj.has("sosEventId") && !reqObj.isNull("sosEventId")) reqObj.getString("sosEventId") else null,
                                adults = reqObj.optInt("adults", 0),
                                children = reqObj.optInt("children", 0),
                                elderly = reqObj.optInt("elderly", 0),
                                photoUrl = if (reqObj.has("photoUrl") && !reqObj.isNull("photoUrl")) reqObj.getString("photoUrl") else null,
                                emergencyType = if (reqObj.has("emergencyType") && !reqObj.isNull("emergencyType")) reqObj.getString("emergencyType") else null,
                                neededItems = parseNeededItems(reqObj)
                            )
                        )
                    }
                    requests
                }.fold(
                    onSuccess = { RescueMissionApiResult.Success(it) },
                    onFailure = { RescueMissionApiResult.Failure(RescueMissionApiError(RescueMissionApiErrorKind.UNKNOWN, "Invalid request response")) }
                )
            }
        } catch (_: Exception) {
            RescueMissionApiResult.Failure(RescueMissionApiError(RescueMissionApiErrorKind.UNKNOWN, "Request failed"))
        }
    }

    override suspend fun getRescueRequestDetail(id: String): RescueMissionApiResult<RescueMissionRequestInfo> = withContext(Dispatchers.IO) {
        val token = tokenProvider.getAccessToken()?.trim().orEmpty()
        if (token.isEmpty()) {
            return@withContext RescueMissionApiResult.Failure(
                RescueMissionApiError(RescueMissionApiErrorKind.NO_TOKEN, "No access token available")
            )
        }
        val httpRequest = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/rescue-requests/$id")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        try {
            client.newCall(httpRequest).execute().use { response ->
                val responseText = response.body?.string().orEmpty()
                android.util.Log.d("RESCUE_DEBUG", "HTTP ${response.code} Response: $responseText")
                android.util.Log.d("RESCUE_DEBUG", "updateMissionStatus HTTP ${response.code} Response: $responseText")
                if (!response.isSuccessful) {
                    val errMsg = runCatching {
                        val obj = JSONObject(responseText)
                        if (obj.has("message")) {
                            val msg = obj.get("message")
                            if (msg is JSONArray) msg.join(", ") else msg.toString()
                        } else if (obj.has("error") && obj.get("error") is JSONObject) {
                            obj.getJSONObject("error").optString("message", "HTTP ${response.code}")
                        } else "HTTP ${response.code}"
                    }.getOrDefault("HTTP ${response.code}")
                    return@withContext RescueMissionApiResult.Failure(RescueMissionApiError(errorKind(response.code), errMsg))
                }
                runCatching {
                    val reqObj = JSONObject(responseText).getJSONObject("data")
                    var rLat: Double? = null
                    var rLon: Double? = null
                    if (reqObj.has("location") && !reqObj.isNull("location")) {
                        val locObj = reqObj.getJSONObject("location")
                        if (locObj.has("coordinates")) {
                            val coords = locObj.getJSONArray("coordinates")
                            if (coords.length() >= 2) {
                                rLon = coords.getDouble(0)
                                rLat = coords.getDouble(1)
                            }
                        }
                    }
                    RescueMissionRequestInfo(
                        id = reqObj.getString("id"),
                        requesterId = reqObj.getString("requesterId"),
                        requesterName = if (reqObj.has("requesterName") && !reqObj.isNull("requesterName")) reqObj.getString("requesterName") else null,
                        requesterPhone = if (reqObj.has("requesterPhone") && !reqObj.isNull("requesterPhone")) reqObj.getString("requesterPhone") else null,
                        peopleCount = reqObj.optInt("peopleCount", 1),
                        urgency = reqObj.getString("urgency"),
                        description = if (reqObj.has("description") && !reqObj.isNull("description")) reqObj.getString("description") else null,
                        status = reqObj.getString("status"),
                        latitude = rLat,
                        longitude = rLon,
                        distance = if (reqObj.has("distance") && !reqObj.isNull("distance")) reqObj.getDouble("distance") else null,
                        createdAt = if (reqObj.has("createdAt") && !reqObj.isNull("createdAt")) reqObj.getString("createdAt") else null,
                        updatedAt = if (reqObj.has("updatedAt") && !reqObj.isNull("updatedAt")) reqObj.getString("updatedAt") else null,
                        sosEventId = if (reqObj.has("sosEventId") && !reqObj.isNull("sosEventId")) reqObj.getString("sosEventId") else null,
                        adults = reqObj.optInt("adults", 0),
                        children = reqObj.optInt("children", 0),
                        elderly = reqObj.optInt("elderly", 0),
                        photoUrl = if (reqObj.has("photoUrl") && !reqObj.isNull("photoUrl")) reqObj.getString("photoUrl") else null,
                        emergencyType = if (reqObj.has("emergencyType") && !reqObj.isNull("emergencyType")) reqObj.getString("emergencyType") else null,
                        neededItems = parseNeededItems(reqObj)
                    )
                }.fold(
                    onSuccess = { RescueMissionApiResult.Success(it) },
                    onFailure = { RescueMissionApiResult.Failure(RescueMissionApiError(RescueMissionApiErrorKind.UNKNOWN, "Invalid request response")) }
                )
            }
        } catch (_: Exception) {
            RescueMissionApiResult.Failure(RescueMissionApiError(RescueMissionApiErrorKind.UNKNOWN, "Request failed"))
        }
    }

    private fun parseNeededItems(obj: JSONObject): List<String> {
        if (!obj.has("neededItems") || obj.isNull("neededItems")) return emptyList()
        val arr = obj.getJSONArray("neededItems")
        val list = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val s = arr.optString(i)
            if (!s.isNullOrBlank()) list.add(s)
        }
        return list
    }

    private fun parseMission(data: JSONObject): RescueMissionResponse {
        var requestInfo: RescueMissionRequestInfo? = null
        if (data.has("request") && !data.isNull("request")) {
            val reqObj = data.getJSONObject("request")
            var lat: Double? = null
            var lon: Double? = null
            if (reqObj.has("location") && !reqObj.isNull("location")) {
                val locObj = reqObj.getJSONObject("location")
                if (locObj.has("coordinates")) {
                    val coords = locObj.getJSONArray("coordinates")
                    if (coords.length() >= 2) {
                        lon = coords.getDouble(0)
                        lat = coords.getDouble(1)
                    }
                }
            }
            requestInfo = RescueMissionRequestInfo(
                id = reqObj.getString("id"),
                requesterId = reqObj.getString("requesterId"),
                requesterName = if (reqObj.has("requesterName") && !reqObj.isNull("requesterName")) reqObj.getString("requesterName") else null,
                requesterPhone = if (reqObj.has("requesterPhone") && !reqObj.isNull("requesterPhone")) reqObj.getString("requesterPhone") else null,
                peopleCount = reqObj.optInt("peopleCount", 1),
                urgency = reqObj.getString("urgency"),
                description = if (reqObj.has("description") && !reqObj.isNull("description")) reqObj.getString("description") else null,
                status = reqObj.getString("status"),
                latitude = lat,
                longitude = lon,
                distance = if (reqObj.has("distance") && !reqObj.isNull("distance")) reqObj.getDouble("distance") else null,
                createdAt = if (reqObj.has("createdAt") && !reqObj.isNull("createdAt")) reqObj.getString("createdAt") else null,
                updatedAt = if (reqObj.has("updatedAt") && !reqObj.isNull("updatedAt")) reqObj.getString("updatedAt") else null,
                sosEventId = if (reqObj.has("sosEventId") && !reqObj.isNull("sosEventId")) reqObj.getString("sosEventId") else null,
                adults = reqObj.optInt("adults", 0),
                children = reqObj.optInt("children", 0),
                elderly = reqObj.optInt("elderly", 0),
                photoUrl = if (reqObj.has("photoUrl") && !reqObj.isNull("photoUrl")) reqObj.getString("photoUrl") else null,
                emergencyType = if (reqObj.has("emergencyType") && !reqObj.isNull("emergencyType")) reqObj.getString("emergencyType") else null,
                neededItems = parseNeededItems(reqObj)
            )
        }

        return RescueMissionResponse(
            id = data.getString("id"),
            requestId = data.getString("requestId"),
            rescuerId = if (data.has("rescuerId") && !data.isNull("rescuerId")) data.getString("rescuerId") else null,
            rescuerName = if (data.has("rescuerName") && !data.isNull("rescuerName")) data.getString("rescuerName") else null,
            rescuerPhone = if (data.has("rescuerPhone") && !data.isNull("rescuerPhone")) data.getString("rescuerPhone") else null,
            teamId = if (data.has("teamId") && !data.isNull("teamId")) data.getString("teamId") else null,
            status = data.getString("status"),
            notes = if (data.has("notes") && !data.isNull("notes")) data.getString("notes") else null,
            acceptedAt = if (data.has("acceptedAt") && !data.isNull("acceptedAt")) data.getString("acceptedAt") else null,
            startedAt = if (data.has("startedAt") && !data.isNull("startedAt")) data.getString("startedAt") else null,
            completedAt = if (data.has("completedAt") && !data.isNull("completedAt")) data.getString("completedAt") else null,
            request = requestInfo
        )
    }

    private fun errorKind(code: Int): RescueMissionApiErrorKind = when (code) {
        400 -> RescueMissionApiErrorKind.BAD_REQUEST
        401 -> RescueMissionApiErrorKind.UNAUTHORIZED
        403 -> RescueMissionApiErrorKind.FORBIDDEN
        404 -> RescueMissionApiErrorKind.NOT_FOUND
        in 500..599 -> RescueMissionApiErrorKind.SERVER
        else -> RescueMissionApiErrorKind.UNKNOWN
    }
    
}
