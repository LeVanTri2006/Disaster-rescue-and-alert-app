package com.example.bai1.presentation.warning

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.bai1.data.model.DisasterCategory
import com.example.bai1.data.repository.DisasterRepository
import com.example.bai1.presentation.components.AppFilterChip
import com.example.bai1.presentation.components.AppTopBar
import com.example.bai1.presentation.components.WarningItemCard
import com.example.bai1.ui.theme.BackgroundLight

@Composable
fun WarningListScreen(
    initialCategory: DisasterCategory? = null,
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit
) {
    var selectedFilter by remember {
        mutableStateOf(
            when (initialCategory) {
                DisasterCategory.STORM -> "Bão"
                DisasterCategory.FLOOD -> "Lũ lụt"
                DisasterCategory.LANDSLIDE -> "Sạt lở"
                else -> "Tất cả"
            }
        )
    }

    val filters = listOf("Tất cả", "Bão", "Lũ lụt", "Sạt lở")

    val filteredItems = remember(selectedFilter) {
        when (selectedFilter) {
            "Bão" -> DisasterRepository.warningList.filter { it.category == DisasterCategory.STORM }
            "Lũ lụt" -> DisasterRepository.warningList.filter { it.category == DisasterCategory.FLOOD }
            "Sạt lở" -> DisasterRepository.warningList.filter { it.category == DisasterCategory.LANDSLIDE }
            else -> DisasterRepository.warningList
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Cảnh báo thiên tai",
                showBackButton = true,
                onBackClick = onNavigateBack
            )
        },
        containerColor = BackgroundLight
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Horizontal Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(androidx.compose.ui.graphics.Color.White)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                filters.forEach { filter ->
                    AppFilterChip(
                        text = filter,
                        isSelected = selectedFilter == filter,
                        onClick = { selectedFilter = filter }
                    )
                }
            }

            // Warning Items List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredItems) { item ->
                    WarningItemCard(
                        item = item,
                        onClick = { onNavigateToDetail(item.id) }
                    )
                }
            }
        }
    }
}
