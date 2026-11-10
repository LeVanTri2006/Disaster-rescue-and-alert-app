package com.example.bai1.auth.credentials

/** Account-picker cleanup boundary; never authorizes a backend or routing session. */
fun interface CredentialStateCleaner {
    suspend fun clear()
}
