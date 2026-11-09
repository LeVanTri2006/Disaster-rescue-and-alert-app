package com.example.bai1.uiisolated

import androidx.compose.runtime.Composable
import com.example.bai1.data.model.DisasterCategory
import com.example.bai1.presentation.navigation.AppNavScreens

/** Only owned static demo data. Exact production screens and actual graph callbacks. */
class ActualWarningFixtureScreens(private val app: GraphFixtureApplication) : AppNavScreens by ActualAccountFixtureScreens(app) {
    @Composable override fun WarningListScreen(onNavigateBack: () -> Unit, onNavigateToDetail: (String) -> Unit) {
        com.example.bai1.presentation.warning.WarningListScreen(onNavigateBack = onNavigateBack, onNavigateToDetail = onNavigateToDetail, warnings = app.warningItems)
    }
    @Composable override fun StormDetailScreen(onNavigateBack: () -> Unit) {
        com.example.bai1.presentation.warning.StormDetailScreen(onNavigateBack)
    }
    @Composable override fun FloodDetailScreen(onNavigateBack: () -> Unit, onViewFloodMap: () -> Unit) {
        com.example.bai1.presentation.warning.FloodDetailScreen(onNavigateBack, onViewFloodMap)
    }
    @Composable override fun NotificationScreen(onNavigateBack: () -> Unit, onBottomNavNavigate: (String) -> Unit) {
        com.example.bai1.presentation.notification.NotificationScreen(onNavigateBack, onBottomNavNavigate)
    }
}
