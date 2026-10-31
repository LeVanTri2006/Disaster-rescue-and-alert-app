package com.example.bai1.auth.firebase

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

interface FirebaseAuthSession {
    suspend fun signInWithCustomToken(customToken: String): Boolean
    suspend fun signOut()
}

class AndroidFirebaseAuthSession(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : FirebaseAuthSession {
    override suspend fun signInWithCustomToken(customToken: String): Boolean = runCatching {
        await(auth.signInWithCustomToken(customToken))
        true
    }.onFailure { e ->
        android.util.Log.e("FirebaseAuthSession", "Failed to sign in with custom token", e)
    }.getOrDefault(false)

    override suspend fun signOut() {
        auth.signOut()
    }

    private suspend fun <T> await(task: com.google.android.gms.tasks.Task<T>): T =
        suspendCancellableCoroutine { continuation ->
            task.addOnCompleteListener { completed ->
                if (completed.isSuccessful) {
                    continuation.resume(completed.result)
                } else {
                    continuation.resumeWith(Result.failure(completed.exception ?: IllegalStateException("Firebase task failed")))
                }
            }
        }
}
