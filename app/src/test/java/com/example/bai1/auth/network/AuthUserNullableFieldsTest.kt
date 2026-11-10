package com.example.bai1.auth.network

import kotlinx.coroutines.test.runTest
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class AuthUserNullableFieldsTest {
    private suspend fun user(payload: String): com.example.bai1.auth.session.AuthUser {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(payload.toResponseBody()).build()
        }.build()
        return (OkHttpAuthApiClient("http://owned-parser.invalid", client).me("controlled-access") as AuthApiResult.Success).value
    }
    @Test fun explicitJsonNullAndMissingOptionalFieldsRemainAbsent() = runTest {
        for (payload in listOf(
            """{"success":true,"data":{"id":"controlled-user","fullName":null,"email":null,"phone":null,"role":null,"status":null,"rescueProfile":{"verificationStatus":null}}}""",
            """{"success":true,"data":{"id":"controlled-user"}}"""
        )) {
            val value = user(payload)
            assertNull(value.fullName); assertNull(value.email); assertNull(value.phone)
            assertNull(value.role); assertNull(value.status); assertNull(value.verificationStatus)
        }
    }
    @Test fun nonNullUserValuesAndLiteralNullTextArePreserved() = runTest {
        val value = user("""{"success":true,"data":{"id":"controlled-user","fullName":"null","email":"controlled@example.invalid","phone":"0000000000","role":"USER","status":"ACTIVE","rescueProfile":{"verificationStatus":"PENDING"}}}""")
        assertEquals("null", value.fullName); assertEquals("controlled@example.invalid", value.email)
        assertEquals("0000000000", value.phone); assertEquals("USER", value.role)
        assertEquals("ACTIVE", value.status); assertEquals("PENDING", value.verificationStatus)
    }
}
