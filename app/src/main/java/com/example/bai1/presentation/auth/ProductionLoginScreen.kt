package com.example.bai1.presentation.auth

import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.*
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.bai1.R
import com.google.android.libraries.identity.googleid.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** Production default overload. SDK instances are created only after a Google button click. */
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit = {},
    onRegisterClick: () -> Unit,
    onForgotPasswordClick: () -> Unit,
    onLogin: ((String, String) -> Unit)? = null,
    onGoogleLogin: ((String) -> Unit)? = null,
    isLoading: Boolean = false,
    errorMessage: String? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    LoginScreen(onLoginSuccess, onRegisterClick, onForgotPasswordClick, onLogin, onGoogleLogin,
        isLoading, errorMessage, onGoogleSignInClick = {
            coroutineScope.launch {
                val credentialManager = CredentialManager.create(context)
                        Log.d("GOOGLE_STEP", "Credential request started")
                        
                        val rawNonce = UUID.randomUUID().toString()
                        val bytes = rawNonce.toByteArray()
                        val md = MessageDigest.getInstance("SHA-256")
                        val digest = md.digest(bytes)
                        val hashedNonce = digest.joinToString("") { "%02x".format(it) }

                        val getGoogleIdOptionBuilder = GetGoogleIdOption.Builder()
                            .setServerClientId(context.getString(R.string.default_web_client_id))
                            .setNonce(hashedNonce)
                            .setAutoSelectEnabled(false) // Never auto-select to allow switching accounts

                        var credentialResponse: androidx.credentials.GetCredentialResponse? = null
                        
                        try {
                            val googleIdOption = getGoogleIdOptionBuilder
                                .setFilterByAuthorizedAccounts(false) // Show all accounts on the device
                                .build()
                                
                            val request = GetCredentialRequest.Builder()
                                .addCredentialOption(googleIdOption)
                                .build()
                            
                            credentialResponse = credentialManager.getCredential(context, request)
                        } catch (e: GetCredentialCancellationException) {
                            Log.d("GOOGLE_STEP", "User cancelled during all accounts check")
                            return@launch
                        } catch (e: Exception) {
                            Log.d("GOOGLE_STEP", "Exception during all accounts check: ${e.message}")
                            e.printStackTrace()
                            return@launch
                        }

                        if (credentialResponse == null) return@launch
                        
                        Log.d("GOOGLE_STEP", "Credential returned")
                        val credential = credentialResponse.credential
                        Log.d("GOOGLE_STEP", "Credential type: ${credential.type}")

                        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                            try {
                                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                                val idToken = googleIdTokenCredential.idToken
                                Log.d("GOOGLE_STEP", "Google token obtained: YES")
                                
                                Log.d("GOOGLE_STEP", "Firebase sign-in started")
                                val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                                val authResult = FirebaseAuth.getInstance().signInWithCredential(firebaseCredential).await()
                                
                                if (authResult.user != null) {
                                    Log.d("GOOGLE_STEP", "Firebase sign-in success")
                                    val tokenResult = authResult.user!!.getIdToken(false).await()
                                    val firebaseIdToken = tokenResult.token
                                    if (firebaseIdToken != null) {
                                        Log.d("GOOGLE_STEP", "Firebase ID token obtained: YES")
                                        Log.d("GOOGLE_STEP", "Backend request started")
                                        onGoogleLogin?.invoke(firebaseIdToken)
                                    } else {
                                        Log.d("GOOGLE_STEP", "Firebase ID token obtained: NO")
                                    }
                                } else {
                                    Log.d("GOOGLE_STEP", "Firebase sign-in failure: User is null")
                                }
                            } catch (e: Exception) {
                                Log.d("GOOGLE_STEP", "Firebase sign-in failure: ${e.message}")
                                e.printStackTrace()
                            }
                        } else {
                            Log.d("GOOGLE_STEP", "Unsupported credential type")
                        }
                    }
        })
}
