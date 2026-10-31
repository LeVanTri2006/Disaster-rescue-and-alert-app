package com.example.bai1.auth.network

import com.example.bai1.auth.session.AuthUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val isNewUser: Boolean = false
)

enum class AuthApiErrorKind {
    BAD_REQUEST,
    UNAUTHORIZED,
    FORBIDDEN,
    SERVER,
    TIMEOUT,
    NO_INTERNET,
    UNKNOWN
}

data class AuthApiError(
    val kind: AuthApiErrorKind,
    val technicalReason: String
) {
    fun userMessage(): String {
        if (technicalReason.contains("already in use", ignoreCase = true) || technicalReason.contains("tồn tại", ignoreCase = true)) {
            return "Email hoặc số điện thoại đã được sử dụng."
        }
        return when (kind) {
            AuthApiErrorKind.BAD_REQUEST -> "Thông tin không hợp lệ."
            AuthApiErrorKind.UNAUTHORIZED -> "Email/số điện thoại hoặc mật khẩu không đúng."
            AuthApiErrorKind.FORBIDDEN -> "Tài khoản chưa được phép đăng nhập hoặc đã bị khóa."
            AuthApiErrorKind.SERVER -> "Máy chủ đang bận. Vui lòng thử lại sau."
            AuthApiErrorKind.TIMEOUT -> "Kết nối quá thời gian. Vui lòng thử lại."
            AuthApiErrorKind.NO_INTERNET -> "Không có kết nối mạng. Vui lòng kiểm tra lại."
            AuthApiErrorKind.UNKNOWN -> "Không thể đăng nhập lúc này."
        }
    }
}

sealed interface AuthApiResult<out T> {
    data class Success<T>(val value: T) : AuthApiResult<T>
    data class Failure(val error: AuthApiError) : AuthApiResult<Nothing>
}

interface AuthApiClient {
    suspend fun register(fullName: String, email: String, phone: String, password: String): AuthApiResult<AuthTokens>
    suspend fun login(identifier: String, password: String): AuthApiResult<AuthTokens>
    suspend fun googleLogin(idToken: String): AuthApiResult<AuthTokens>
    suspend fun refresh(refreshToken: String): AuthApiResult<AuthTokens>
    suspend fun me(accessToken: String): AuthApiResult<AuthUser>
    suspend fun updateRole(accessToken: String, role: String): AuthApiResult<AuthUser>
    suspend fun forgotPassword(identifier: String): AuthApiResult<Unit>
    suspend fun resetPassword(identifier: String, otp: String, newPassword: String): AuthApiResult<Unit>
    suspend fun firebaseCustomToken(accessToken: String): AuthApiResult<String> =
        AuthApiResult.Failure(AuthApiError(AuthApiErrorKind.UNKNOWN, "Firebase auth bridge is unavailable"))
}

class OkHttpAuthApiClient(
    baseUrl: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
) : AuthApiClient {
    private val baseUrl = baseUrl.trimEnd('/')
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun register(fullName: String, email: String, phone: String, password: String): AuthApiResult<AuthTokens> {
        val hasEmail = email.isNotBlank()
        val hasPhone = phone.isNotBlank()
        val bodyBuilder = java.lang.StringBuilder("{")
        bodyBuilder.append("\"fullName\":\"${escapeJson(fullName)}\",")
        bodyBuilder.append("\"password\":\"${escapeJson(password)}\"")
        if (hasEmail) bodyBuilder.append(",\"email\":\"${escapeJson(email)}\"")
        if (hasPhone) bodyBuilder.append(",\"phone\":\"${escapeJson(phone)}\"")
        bodyBuilder.append("}")

        return executeJson(
            method = "POST",
            path = "/auth/register",
            body = bodyBuilder.toString(),
            parser = ::parseTokens
        )
    }

    override suspend fun googleLogin(idToken: String): AuthApiResult<AuthTokens> =
        executeJson(
            method = "POST",
            path = "/auth/google",
            body = "{\"idToken\":\"${escapeJson(idToken)}\"}",
            parser = ::parseTokens
        )

    override suspend fun login(identifier: String, password: String): AuthApiResult<AuthTokens> =
        executeJson(
            method = "POST",
            path = "/auth/login",
            body = "{" +
                "\"${if (identifier.contains("@")) "email" else "phone"}\":\"${escapeJson(identifier)}\"," +
                "\"password\":\"${escapeJson(password)}\"" +
                "}",
            parser = ::parseTokens
        )

    override suspend fun refresh(refreshToken: String): AuthApiResult<AuthTokens> =
        executeJson(
            method = "POST",
            path = "/auth/refresh",
            body = "{\"refreshToken\":\"${escapeJson(refreshToken)}\"}",
            parser = ::parseTokens
        )

    override suspend fun me(accessToken: String): AuthApiResult<AuthUser> =
        executeJson(
            method = "GET",
            path = "/auth/me",
            bearerToken = accessToken,
            parser = ::parseUser
        )

    override suspend fun forgotPassword(identifier: String): AuthApiResult<Unit> =
        executeJson(
            method = "POST",
            path = "/auth/forgot-password",
            body = "{\"identifier\":\"${escapeJson(identifier)}\"}",
            parser = { Unit }
        )

    override suspend fun resetPassword(identifier: String, otp: String, newPassword: String): AuthApiResult<Unit> =
        executeJson(
            method = "POST",
            path = "/auth/reset-password",
            body = "{\"identifier\":\"${escapeJson(identifier)}\",\"otp\":\"${escapeJson(otp)}\",\"newPassword\":\"${escapeJson(newPassword)}\"}",
            parser = { Unit }
        )

    override suspend fun updateRole(accessToken: String, role: String): AuthApiResult<AuthUser> =
        executeJson(
            method = "PATCH",
            path = "/users/me/role",
            body = "{\"role\":\"${escapeJson(role)}\"}",
            bearerToken = accessToken,
            parser = ::parseUser
        )

    override suspend fun firebaseCustomToken(accessToken: String): AuthApiResult<String> =
        executeJson(
            method = "POST",
            path = "/auth/firebase-token",
            bearerToken = accessToken,
            parser = { data -> data.getString("customToken") }
        )

    private suspend fun <T> executeJson(
        method: String,
        path: String,
        body: String? = null,
        bearerToken: String? = null,
        parser: (JSONObject) -> T
    ): AuthApiResult<T> = withContext(Dispatchers.IO) {
        val requestBuilder = Request.Builder()
            .url(baseUrl + path)
            .header("Accept", "application/json")

        bearerToken?.takeIf { it.isNotBlank() }?.let {
            requestBuilder.header("Authorization", "Bearer $it")
        }

        if (body != null) {
            requestBuilder.header("Content-Type", "application/json")
                .method(method, body.toRequestBody(jsonMediaType))
        } else {
            if (method == "POST" || method == "PUT" || method == "PATCH") {
                requestBuilder.method(method, "".toRequestBody(null))
            } else {
                requestBuilder.method(method, null)
            }
        }

        try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                val responseText = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    var serverMsg = "HTTP ${response.code}"
                    runCatching {
                        val rootJson = JSONObject(responseText)
                        if (rootJson.has("error")) {
                            val errorObj = rootJson.getJSONObject("error")
                            if (errorObj.has("message")) {
                                serverMsg = errorObj.getString("message")
                            }
                        } else if (rootJson.has("message")) {
                            serverMsg = rootJson.getString("message")
                        }
                    }
                    return@withContext AuthApiResult.Failure(
                        AuthApiError(errorKind(response.code), serverMsg)
                    )
                }

                runCatching { parser(JSONObject(responseText).getJSONObject("data")) }
                    .fold(
                        onSuccess = { AuthApiResult.Success(it) },
                        onFailure = {
                            AuthApiResult.Failure(
                                AuthApiError(AuthApiErrorKind.UNKNOWN, "Invalid auth response")
                            )
                        }
                    )
            }
        } catch (_: SocketTimeoutException) {
            AuthApiResult.Failure(AuthApiError(AuthApiErrorKind.TIMEOUT, "Auth request timed out"))
        } catch (_: IOException) {
            AuthApiResult.Failure(AuthApiError(AuthApiErrorKind.NO_INTERNET, "Auth request could not reach server"))
        } catch (_: RuntimeException) {
            AuthApiResult.Failure(AuthApiError(AuthApiErrorKind.UNKNOWN, "Auth request failed"))
        }
    }

    private fun parseTokens(data: JSONObject): AuthTokens = AuthTokens(
        accessToken = data.getString("accessToken"),
        refreshToken = data.getString("refreshToken"),
        isNewUser = data.optBoolean("isNewUser", false)
    )

    private fun parseUser(data: JSONObject): AuthUser = AuthUser(
        id = data.getString("id"),
        fullName = data.optString("fullName").takeIf { it.isNotBlank() },
        email = data.optString("email").takeIf { it.isNotBlank() },
        phone = data.optString("phone").takeIf { it.isNotBlank() },
        role = data.optString("role").takeIf { it.isNotBlank() },
        status = data.optString("status").takeIf { it.isNotBlank() },
        verificationStatus = data.optJSONObject("rescueProfile")?.optString("verificationStatus")?.takeIf { it.isNotBlank() }
    )

    private fun errorKind(code: Int): AuthApiErrorKind = when (code) {
        400 -> AuthApiErrorKind.BAD_REQUEST
        401 -> AuthApiErrorKind.UNAUTHORIZED
        403 -> AuthApiErrorKind.FORBIDDEN
        in 500..599 -> AuthApiErrorKind.SERVER
        else -> AuthApiErrorKind.UNKNOWN
    }

    private fun escapeJson(value: String): String = buildString(value.length + 8) {
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(character)
            }
        }
    }
}
