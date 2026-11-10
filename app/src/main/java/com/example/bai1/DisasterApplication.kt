package com.example.bai1

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import com.example.bai1.auth.AuthAppDependencies
import com.example.bai1.sos.SosAppDependencies
import com.example.bai1.rescue.RescueMissionAppDependencies
import com.example.bai1.rescue.routing.RoutingAppDependencies
import com.example.bai1.rescue.routing.diagnostic.DiagnosticGraphProvider
import com.example.bai1.presentation.navigation.AppNavGraph

/** Production wiring; the manifest's existing SDK providers are unchanged. */
class DisasterApplication : Application(), MainAppHost {
    override fun onCreate() {
        super.onCreate()
        // Tiền nạp đồ thị đường bộ OSM dưới nền ngay khi ứng dụng mở
        DiagnosticGraphProvider.preload(this)
    }

    override fun initialize(activity: ComponentActivity) {
        DiagnosticGraphProvider.preload(activity)
        AuthAppDependencies.initialize(activity)
        SosAppDependencies.initialize(activity)
        RescueMissionAppDependencies.initialize(activity)
        RoutingAppDependencies.initialize(activity)
    }
    @Composable override fun AppGraph(navController: NavHostController) {
        AppNavGraph(navController = navController)
    }
}
