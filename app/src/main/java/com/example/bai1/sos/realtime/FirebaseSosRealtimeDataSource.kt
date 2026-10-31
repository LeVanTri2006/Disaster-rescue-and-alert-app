package com.example.bai1.sos.realtime

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

interface SosRealtimeDataSource {
    fun observeChanges(): Flow<FirebaseSosChange>
}

class FirebaseSosRealtimeDataSource(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val databaseProvider: () -> FirebaseDatabase = { FirebaseDatabase.getInstance() },
) : SosRealtimeDataSource {
    override
    fun observeChanges(): Flow<FirebaseSosChange> = callbackFlow {


        val database = runCatching { databaseProvider() }.getOrElse {
            close()
            return@callbackFlow
        }

        fun listener(name: String) = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                FirebaseSosEventMapper.fromSnapshot(snapshot)?.let { android.util.Log.d("FirebaseRealtime", "RTDB_CHILD_ADDED: id=${snapshot.key}"); trySend(it) }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                FirebaseSosEventMapper.fromSnapshot(snapshot)?.let { android.util.Log.d("FirebaseRealtime", "RTDB_CHILD_CHANGED: id=${snapshot.key}"); trySend(it) }
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                snapshot.key?.let { android.util.Log.d("FirebaseRealtime", "RTDB_CHILD_REMOVED: id=$it"); trySend(FirebaseSosChange.Removed(it)) }
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) = Unit
            override fun onCancelled(error: DatabaseError) {
                android.util.Log.e("FirebaseRealtime", "Listener $name failed: ${error.message}")
            }
        }

        val operationalReference = database.getReference("sos-events")
        val operationalListener = listener("sos-events")
        val ownerListener = listener("sos-owner-events")
        var currentUid: String? = null

        val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val newUid = firebaseAuth.uid
            android.util.Log.d("FirebaseRealtime", "RTDB_AUTH_STATE: firebaseUid=$newUid")
            if (newUid == currentUid) return@AuthStateListener

            if (currentUid != null) {
                database.getReference("sos-owner-events").child(currentUid!!).removeEventListener(ownerListener)
            }
            operationalReference.removeEventListener(operationalListener)
            
            currentUid = newUid

            if (newUid != null) {
                database.getReference("sos-owner-events").child(newUid).addChildEventListener(ownerListener)
                android.util.Log.d("FirebaseRealtime", "RTDB_LISTENER_START: path=sos-events")
                operationalReference.addChildEventListener(operationalListener)
            }
        }

        auth.addAuthStateListener(authStateListener)

        awaitClose {
            auth.removeAuthStateListener(authStateListener)
            operationalReference.removeEventListener(operationalListener)
            if (currentUid != null) {
                database.getReference("sos-owner-events").child(currentUid!!).removeEventListener(ownerListener)
            }
        }
    }
}
