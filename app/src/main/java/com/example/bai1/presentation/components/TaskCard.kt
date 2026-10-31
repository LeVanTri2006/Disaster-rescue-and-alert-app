package com.example.bai1.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bai1.ui.theme.TextMuted
import com.example.bai1.ui.theme.TextPrimary
import com.example.bai1.ui.theme.TextSecondary

@Composable
fun TaskCard(
    title: String,
    location: String,
    details: String,
    alertColor: Color,
    alertBgColor: Color,
    badgeText: String,
    timeAgo: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Left color indicator bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .heightIn(min = 100.dp) // Ensure minimum height to stretch bar
                    .fillMaxHeight()
                    .background(alertColor)
            )
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(alertBgColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = icon, contentDescription = null, tint = alertColor, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = alertColor)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = location, fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = details, fontSize = 12.sp, color = TextSecondary)
                        }
                    }
                    
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(alertBgColor)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(text = badgeText, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = alertColor)
                        }
                        Text(text = timeAgo, fontSize = 11.sp, color = TextMuted)
                    }
                }
            }
        }
    }
}
