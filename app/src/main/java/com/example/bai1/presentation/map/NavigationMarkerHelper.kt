package com.example.bai1.presentation.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.example.bai1.R
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory

enum class NavigationVehicleType(val displayName: String, val iconRes: Int) {
    CAR("Ô tô", R.drawable.ic_navigation_car),
    MOTORCYCLE("Xe máy", R.drawable.ic_navigation_motorcycle),
    ARROW("Mũi tên", R.drawable.ic_navigation_arrow)
}

object NavigationMarkerHelper {
    private val cache = mutableMapOf<NavigationVehicleType, BitmapDescriptor>()
    private var victimDescriptorCache: BitmapDescriptor? = null

    fun getVehicleDescriptor(context: Context, type: NavigationVehicleType): BitmapDescriptor {
        cache[type]?.let { return it }
        val sizeDp = when (type) {
            NavigationVehicleType.CAR -> 54
            NavigationVehicleType.MOTORCYCLE -> 48
            NavigationVehicleType.ARROW -> 48
        }
        val descriptor = createBitmapDescriptor(context, type.iconRes, sizeDp)
        cache[type] = descriptor
        return descriptor
    }

    fun getVictimDescriptor(context: Context): BitmapDescriptor {
        victimDescriptorCache?.let { return it }
        val density = context.resources.displayMetrics.density
        val sizePx = (52 * density).toInt().coerceAtLeast(48)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = sizePx / 2f

        // Vòng tròn hào quang bên ngoài (pulsing red halo)
        val outerPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#33E53935")
            style = android.graphics.Paint.Style.FILL
        }
        canvas.drawCircle(center, center, center - 2f, outerPaint)

        // Vòng tròn trung gian
        val midPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#66E53935")
            style = android.graphics.Paint.Style.FILL
        }
        canvas.drawCircle(center, center, center * 0.72f, midPaint)

        // Vòng tròn đỏ đặc nổi bật
        val innerPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#E53935")
            style = android.graphics.Paint.Style.FILL
        }
        canvas.drawCircle(center, center, center * 0.48f, innerPaint)

        // Viền trắng sắc nét
        val borderPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 2.5f * density
        }
        canvas.drawCircle(center, center, center * 0.48f, borderPaint)

        // Chữ "SOS" màu trắng ở giữa
        val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 10f * density
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            textAlign = android.graphics.Paint.Align.CENTER
        }
        val textY = center - ((textPaint.descent() + textPaint.ascent()) / 2)
        canvas.drawText("SOS", center, textY, textPaint)

        val desc = BitmapDescriptorFactory.fromBitmap(bitmap)
        victimDescriptorCache = desc
        return desc
    }

    private fun createBitmapDescriptor(context: Context, @DrawableRes resId: Int, sizeDp: Int): BitmapDescriptor {
        val drawable = ContextCompat.getDrawable(context, resId) ?: return BitmapDescriptorFactory.defaultMarker()
        val density = context.resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt().coerceAtLeast(32)
        drawable.setBounds(0, 0, sizePx, sizePx)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    fun clearCache() {
        cache.clear()
        victimDescriptorCache = null
    }
}

