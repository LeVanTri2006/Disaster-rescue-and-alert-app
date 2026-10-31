package com.example.bai1.auth.network

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthApiClientTest {
    @Test
    fun `login sends exact backend email contract without exposing credentials`() = runTest {
        var requestBody = ""
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                requestBody = chain.request().body!!.let { body ->
                    val buffer = okio.Buffer()
                    body.writeTo(buffer)
                    buffer.readUtf8()
                }
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(400)
                    .message("Bad Request")
                    .body("{\"message\":\"invalid\"}".toResponseBody())
                    .build()
            }
            .build()

        val result = OkHttpAuthApiClient("http://test", client).login("a@example.com", "password")

        assertTrue(result is AuthApiResult.Failure)
        assertEquals(AuthApiErrorKind.BAD_REQUEST, (result as AuthApiResult.Failure).error.kind)
        assertTrue(requestBody.contains("\"email\":\"a@example.com\""))
        assertTrue(requestBody.contains("\"password\":\"password\""))
    }

    @Test
    fun `401 is mapped without exposing backend payload`() = runTest {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(401)
                    .message("Unauthorized")
                    .body("{\"message\":\"secret backend detail\"}".toResponseBody())
                    .build()
            }
            .build()

        val result = OkHttpAuthApiClient("http://test", client).me("access")

        assertTrue(result is AuthApiResult.Failure)
        val failure = result as AuthApiResult.Failure
        assertEquals(AuthApiErrorKind.UNAUTHORIZED, failure.error.kind)
        assertEquals("HTTP 401", failure.error.technicalReason)
        assertEquals("Email/số điện thoại hoặc mật khẩu không đúng.", failure.error.userMessage())
    }
}
