package com.example.bai1.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bai1.ui.theme.*

@Composable
fun MapMockupCanvas(
    modifier: Modifier = Modifier,
    isMiniMap: Boolean = false,
    showLegend: Boolean = true,
    onMarkerClick: (String) -> Unit = {}
) {
    var zoomLevel by remember { mutableFloatStateOf(1.0f) }

    Box(
        modifier = modifier.clip(
            if (isMiniMap) RoundedCornerShape(16.dp) else RoundedCornerShape(0.dp)
        )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // 1. Draw Sea Background (Biển Đông) with subtle deep ocean gradient
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFCBE5F8),
                        Color(0xFFB3D9F8),
                        Color(0xFFA1CDF5)
                    )
                )
            )

            // Sea depth contour curves (Bathymetry lines)
            val seaContour1 = Path().apply {
                moveTo(width * 0.55f, 0f)
                cubicTo(width * 0.50f, height * 0.35f, width * 0.45f, height * 0.70f, width * 0.60f, height)
            }
            drawPath(
                path = seaContour1,
                color = Color(0xFF8DC0F0).copy(alpha = 0.5f),
                style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
            )

            // 2. Realistic Landmass (Central Vietnam Coastline: Thừa Thiên Huế, Đà Nẵng, Quảng Nam)
            // Coastline shape including Tam Giang lagoon, Son Tra peninsula, Da Nang bay, and Hoi An coast
            val landPath = Path().apply {
                moveTo(0f, 0f)
                lineTo(width * 0.45f, 0f) // North of Hue
                // Hue coastline with Tam Giang lagoon indentation
                cubicTo(
                    width * 0.42f, height * 0.15f,
                    width * 0.40f, height * 0.28f,
                    width * 0.36f, height * 0.36f // Hai Van pass base
                )
                // Son Tra Peninsula (jutting eastward into the sea)
                cubicTo(
                    width * 0.42f, height * 0.38f,
                    width * 0.45f, height * 0.41f,
                    width * 0.42f, height * 0.44f // Da Nang bay curve
                )
                // Da Nang to Hoi An / Quang Nam coast
                cubicTo(
                    width * 0.37f, height * 0.50f,
                    width * 0.39f, height * 0.68f,
                    width * 0.48f, height * 0.85f // Quang Nam coastal bulge
                )
                cubicTo(
                    width * 0.45f, height * 0.92f,
                    width * 0.42f, height * 0.96f,
                    width * 0.44f, height
                )
                lineTo(0f, height)
                close()
            }
            // Land base color (gentle topographical green-beige)
            drawPath(path = landPath, color = Color(0xFFE8EFE5))

            // Coastline border line
            drawPath(
                path = landPath,
                color = Color(0xFFC2D6C0),
                style = Stroke(width = 3f)
            )

            // Cu Lao Cham island
            drawCircle(
                color = Color(0xFFD4E5D1),
                radius = 12f * zoomLevel,
                center = Offset(width * 0.52f, height * 0.58f)
            )
            drawCircle(
                color = Color(0xFFA5C4A2),
                radius = 12f * zoomLevel,
                center = Offset(width * 0.52f, height * 0.58f),
                style = Stroke(width = 1.5f)
            )

            // 3. River Inundation Area (Thu Bồn & Vu Gia River Basin in Quảng Nam)
            val riverMainPath = Path().apply {
                moveTo(width * 0.08f, height * 0.64f)
                cubicTo(
                    width * 0.20f, height * 0.61f,
                    width * 0.28f, height * 0.67f,
                    width * 0.39f, height * 0.62f
                )
            }
            drawPath(
                path = riverMainPath,
                color = Color(0xFF42A5F5),
                style = Stroke(width = 7f * zoomLevel)
            )

            // River tributary
            val riverTributary = Path().apply {
                moveTo(width * 0.16f, height * 0.76f)
                cubicTo(
                    width * 0.24f, height * 0.72f,
                    width * 0.28f, height * 0.67f,
                    width * 0.35f, height * 0.68f
                )
            }
            drawPath(
                path = riverTributary,
                color = Color(0xFF64B5F6),
                style = Stroke(width = 4f * zoomLevel)
            )

            // Severe Inundation zone polygon (Đại Lộc - Điện Bàn flood basin)
            val floodPolygon = Path().apply {
                moveTo(width * 0.20f, height * 0.60f)
                lineTo(width * 0.35f, height * 0.62f)
                lineTo(width * 0.32f, height * 0.73f)
                lineTo(width * 0.22f, height * 0.74f)
                lineTo(width * 0.18f, height * 0.68f)
                close()
            }
            drawPath(
                path = floodPolygon,
                color = Color(0x7729B6F6) // translucent flood blue
            )
            drawPath(
                path = floodPolygon,
                color = Color(0xFF0288D1),
                style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            )

            // 4. Typhoon Radar Spiral Vortex (Bão số 3 YAGI)
            val stormCenter = Offset(width * 0.65f, height * 0.32f)

            // Radar spiral cloud bands (radar reflectivity rain bands)
            drawCircle(
                color = Color(0x22FF5252),
                radius = 135f * zoomLevel,
                center = stormCenter
            )
            drawCircle(
                color = Color(0x66FF7043),
                radius = 135f * zoomLevel,
                center = stormCenter,
                style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
            )

            // Inner hurricane warning radius
            drawCircle(
                color = Color(0x44E53935),
                radius = 75f * zoomLevel,
                center = stormCenter
            )
            drawCircle(
                color = EmergencyRed,
                radius = 75f * zoomLevel,
                center = stormCenter,
                style = Stroke(width = 2.5f)
            )

            // Projected Storm Track Line (Sea -> Coastline)
            val trackPath = Path().apply {
                moveTo(width * 0.88f, height * 0.18f)
                lineTo(stormCenter.x, stormCenter.y)
                lineTo(width * 0.44f, height * 0.42f)
                lineTo(width * 0.25f, height * 0.50f)
            }
            drawPath(
                path = trackPath,
                color = EmergencyRed,
                style = Stroke(width = 3.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)))
            )

            // Forecast milestone dots on track
            val fCast1 = Offset(width * 0.44f, height * 0.42f)
            val fCast2 = Offset(width * 0.25f, height * 0.50f)
            drawCircle(color = EmergencyRed, radius = 5f, center = fCast1)
            drawCircle(color = Color.White, radius = 2.5f, center = fCast1)
            drawCircle(color = EmergencyRed, radius = 5f, center = fCast2)
            drawCircle(color = Color.White, radius = 2.5f, center = fCast2)

            // Storm Eye Icon
            drawCircle(color = EmergencyRed, radius = 18f * zoomLevel, center = stormCenter)
            drawCircle(color = Color.White, radius = 8f * zoomLevel, center = stormCenter)
            drawCircle(color = EmergencyRed, radius = 4f * zoomLevel, center = stormCenter)

            // 5. Cities & Landmarks
            // Huế
            val huePos = Offset(width * 0.32f, height * 0.26f)
            drawCircle(color = TextPrimary, radius = 6f, center = huePos)
            drawCircle(color = Color.White, radius = 2.5f, center = huePos)

            // Đà Nẵng (Target location)
            val daNangPos = Offset(width * 0.38f, height * 0.44f)
            drawCircle(color = PrimaryBlue, radius = 8f, center = daNangPos)
            drawCircle(color = Color.White, radius = 3.5f, center = daNangPos)

            // Quảng Nam
            val quangNamPos = Offset(width * 0.34f, height * 0.70f)
            drawCircle(color = TextPrimary, radius = 6f, center = quangNamPos)

            // Current GPS Location (Pulsing ring at Da Nang)
            drawCircle(
                color = PrimaryBlue.copy(alpha = 0.25f),
                radius = 24f,
                center = daNangPos
            )

            // Rescue Station Markers (Green cross pins)
            val station1 = Offset(width * 0.28f, height * 0.48f)
            val station2 = Offset(width * 0.22f, height * 0.72f)
            listOf(station1, station2).forEach { rPos ->
                drawCircle(color = SuccessGreen, radius = 10f, center = rPos)
                drawLine(
                    color = Color.White,
                    start = Offset(rPos.x - 5f, rPos.y),
                    end = Offset(rPos.x + 5f, rPos.y),
                    strokeWidth = 2.5f
                )
                drawLine(
                    color = Color.White,
                    start = Offset(rPos.x, rPos.y - 5f),
                    end = Offset(rPos.x, rPos.y + 5f),
                    strokeWidth = 2.5f
                )
            }

            // Geographic labels
            val cityPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.DKGRAY
                textSize = 28f
                isFakeBoldText = true
            }
            val seaPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.rgb(55, 110, 160)
                textSize = 30f
                isFakeBoldText = true
                letterSpacing = 0.2f
            }
            val trackPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.rgb(200, 30, 30)
                textSize = 22f
                isFakeBoldText = true
            }

            drawContext.canvas.nativeCanvas.drawText("Huế", huePos.x + 10f, huePos.y + 8f, cityPaint)
            drawContext.canvas.nativeCanvas.drawText("Đà Nẵng", daNangPos.x + 14f, daNangPos.y + 8f, cityPaint)
            drawContext.canvas.nativeCanvas.drawText("Quảng Nam", quangNamPos.x + 10f, quangNamPos.y + 8f, cityPaint)
            drawContext.canvas.nativeCanvas.drawText("BIỂN ĐÔNG", width * 0.68f, height * 0.85f, seaPaint)
            drawContext.canvas.nativeCanvas.drawText("+24h", fCast1.x + 8f, fCast1.y - 8f, trackPaint)
            drawContext.canvas.nativeCanvas.drawText("+48h", fCast2.x + 8f, fCast2.y - 8f, trackPaint)
        }

        // Interactive Storm Badge Card (Top right, below search bar)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 76.dp, end = 16.dp)
                .shadow(4.dp, RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White)
                .clickable { onMarkerClick("storm_1") }
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(EmergencyRed)
                )
                Column {
                    Text(
                        text = "Bão số 3 (YAGI)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = EmergencyRed
                    )
                    Text(
                        text = "Cấp 13, giật cấp 15 • Xem",
                        fontWeight = FontWeight.Medium,
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                }
            }
        }

        // Interactive Flood Badge (near Quang Nam flood zone)
        if (!isMiniMap) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 16.dp, top = 130.dp)
                .shadow(4.dp, RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White)
                .clickable { onMarkerClick("flood_quangnam") }
                .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0288D1))
                    )
                    Text(
                        text = "Vùng ngập Thu Bồn",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = Color(0xFF0288D1)
                    )
                }
            }
        }

        // Floating Map Controls (Right side)
        if (!isMiniMap) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MapIconButton(icon = Icons.Default.Layers) {}
                MapIconButton(icon = Icons.Default.MyLocation) {}
                MapIconButton(icon = Icons.Default.Add) { zoomLevel = (zoomLevel + 0.15f).coerceAtMost(1.8f) }
                MapIconButton(icon = Icons.Default.Remove) { zoomLevel = (zoomLevel - 0.15f).coerceAtLeast(0.7f) }
            }
        }

        // Floating Map Legend (Bottom left, responsive width)
        if (showLegend && !isMiniMap) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 16.dp)
                    .widthIn(max = 210.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.96f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    LegendItem(color = EmergencyRed, label = "Ảnh hưởng trực tiếp")
                    LegendItem(color = Color(0xFFFF7043), label = "Ảnh hưởng gián tiếp")
                    LegendItem(color = EmergencyRed, label = "Tâm bão (dự báo)", isTarget = true)
                    LegendItem(color = Color(0xFF29B6F6), label = "Khu vực ngập lụt")
                    LegendItem(color = SuccessGreen, label = "Trạm cứu hộ", isCross = true)
                }
            }
        }
    }
}

@Composable
private fun MapIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .shadow(4.dp, CircleShape)
            .clip(CircleShape)
            .background(Color.White)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextPrimary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun LegendItem(
    color: Color,
    label: String,
    isTarget: Boolean = false,
    isCross: Boolean = false
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            if (isTarget) {
                Box(
                    modifier = Modifier
                        .size(3.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
        }
        Text(
            text = label,
            fontSize = 10.sp,
            color = TextPrimary,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}
