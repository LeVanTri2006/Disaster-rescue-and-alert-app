package com.example.bai1.presentation.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bai1.R
import com.example.bai1.presentation.components.PrimaryButton
import com.example.bai1.ui.theme.PrimaryBlue

data class OnboardingPageData(
    val badgeImageRes: Int,
    val imageRes: Int,
    val title: String,
    val subtitle: String,
    val bgColor: Color
)

@Composable
fun OnboardingScreen(
    onFinishOnboarding: () -> Unit
) {
    val pages = remember {
        listOf(
            OnboardingPageData(
                badgeImageRes = R.drawable.onboarding1,
                imageRes = R.drawable.onboarding_1_illustration,
                title = "Cùng nhau\nvượt qua thiên tai",
                subtitle = "Cảnh báo sớm\nGiảm thiểu rủi ro",
                bgColor = Color(0xFFFEFEFE) // White to match image bottom
            ),
            OnboardingPageData(
                badgeImageRes = R.drawable.onboarding2,
                imageRes = R.drawable.onboarding_2_illustration,
                title = "Kết nối lực lượng\ncứu hộ",
                subtitle = "Cộng đồng mạnh hơn\nKhi chúng ta cùng nhau",
                bgColor = Color(0xFF0385DE) // Blue water to match image bottom
            ),
            OnboardingPageData(
                badgeImageRes = R.drawable.onboarding3,
                imageRes = R.drawable.onboarding_3_illustration,
                title = "An toàn hơn\ncho mọi người",
                subtitle = "Vì cộng đồng bình an\nvà tương lai bền vững",
                bgColor = Color(0xFF16835D) // Green grass to match image bottom
            )
        )
    }

    var currentPage by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .systemBarsPadding()
    ) {
        AnimatedContent(
            targetState = currentPage,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "OnboardingContent",
            modifier = Modifier.fillMaxSize()
        ) { pageIndex ->
            val page = pages[pageIndex]
            
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Content (Logo & Text) - SHRUNK SPACING
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, top = 24.dp) // Reduced top padding
                ) {
                    Image(
                        painter = painterResource(id = page.badgeImageRes),
                        contentDescription = "Badge",
                        modifier = Modifier.size(100.dp) // Keeping it slightly larger to show detail
                    )

                    Spacer(modifier = Modifier.height(24.dp)) // Reduced

                    Text(
                        text = page.title,
                        fontSize = 28.sp,
                        lineHeight = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        textAlign = TextAlign.Center
                    )

                    if (page.subtitle.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp)) // Reduced
                        Text(
                            text = page.subtitle,
                            fontSize = 16.sp,
                            lineHeight = 24.sp,
                            color = Color(0xFF64748B),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Middle & Bottom (Image + Bottom Navigation Overlay)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f) // Takes all remaining vertical space
                        .clipToBounds() // Harmlessly crops top sky if screen is too short
                ) {
                    // Seamless background extension for the bottom gap
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .align(Alignment.BottomCenter)
                            .background(page.bgColor)
                    )

                    // LAYER 2: Onboarding illustration PNG
                    Image(
                        painter = painterResource(id = page.imageRes),
                        contentDescription = "Onboarding Illustration",
                        contentScale = ContentScale.FillWidth,
                        alignment = Alignment.BottomCenter,
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .offset(y = (-110).dp) // Pushed up so buttons don't cover boat/hands!
                    )

                    // LAYER 3: Bottom Navigation (Dots, Primary Button, Skip) - Overlaying
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 12.dp)
                    ) {
                        // Dots Indicator
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            repeat(pages.size) { index ->
                                val isSelected = index == currentPage
                                Box(
                                    modifier = Modifier
                                        .size(if (isSelected) 8.dp else 8.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) PrimaryBlue else Color(0xFFE2E8F0))
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Action Button
                        PrimaryButton(
                            text = if (currentPage == pages.size - 1) "Bắt đầu" else "Tiếp theo",
                            onClick = {
                                if (currentPage < pages.size - 1) {
                                    currentPage++
                                } else {
                                    onFinishOnboarding()
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Skip Button
                        Box(
                            modifier = Modifier.padding(4.dp), // Shrunk to save space
                            contentAlignment = Alignment.Center
                        ) {
                            if (currentPage < pages.size - 1) {
                                Text(
                                    text = "Bỏ qua",
                                    color = Color(0xFF64748B),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier
                                        .clickable(onClick = onFinishOnboarding)
                                        .padding(4.dp)
                                )
                            } else {
                                Spacer(modifier = Modifier.height(27.dp)) // Match height when invisible
                            }
                        }
                    }
                }
            }
        }
    }
}
