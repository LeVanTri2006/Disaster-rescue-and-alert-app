package com.example.bai1.auth.firebase

/** Cloud-free session boundary; the production implementation lives separately. */
interface FirebaseAuthSession {
    suspend fun signInWithCustomToken(customToken: String): Boolean
    suspend fun signOut()
}
