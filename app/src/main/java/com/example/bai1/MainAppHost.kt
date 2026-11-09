package com.example.bai1

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController

/** Application-owned startup and screen bindings. MainActivity has no SDK/service constructors. */
interface MainAppHost {
    fun initialize(activity: ComponentActivity)
    @Composable fun AppGraph(navController: NavHostController)
}
