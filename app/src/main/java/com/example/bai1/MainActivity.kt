package com.example.bai1

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.example.bai1.presentation.navigation.AppNavGraph
import com.example.bai1.auth.AuthAppDependencies
import com.example.bai1.sos.SosAppDependencies
import com.example.bai1.rescue.RescueMissionAppDependencies
import com.example.bai1.ui.theme.DisasterAlertTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AuthAppDependencies.initialize(this)
        SosAppDependencies.initialize(this)
        RescueMissionAppDependencies.initialize(this)
        enableEdgeToEdge()
        setContent {
            DisasterAlertTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    AppNavGraph(navController = navController)
                }
            }
        }
    }
}
