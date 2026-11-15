package gitee.leolin_me.erkepp.ui.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import gitee.leolin_me.erkepp.R
import gitee.leolin_me.erkepp.data.AppPreferences

/**
 * 小组件外观：用户可在「更多 → 桌面小组件」里自己调，默认跟随应用主题。
 *
 * 番茄 Todo 那种做法是对的：壁纸千差万别，与其替用户选一种底色，不如把四件事交出去——
 * 背景色、背景透明度、文字颜色、文字大小。
 */
data class WidgetAppearance(
    /** null 表示跟随主题（`values{,-night}` 里的 widget_surface）。 */
    val backgroundColor: Int?,
    /** 0..100，100 = 不透明。 */
    val backgroundAlpha: Int,
    /** null 表示跟随主题。 */
    val textColor: Int?,
    /** 90 / 100 / 115，百分比。 */
    val textScale: Int,
) {
    val effectiveTextScale: Float get() = textScale.coerceIn(80, 140) / 100f

    companion object {
        fun of(context: Context): WidgetAppearance = of(AppPreferences(context))

        fun of(preferences: AppPreferences): WidgetAppearance = WidgetAppearance(
            backgroundColor = parseColor(preferences.widgetBackgroundColor),
            backgroundAlpha = preferences.widgetBackgroundAlpha.coerceIn(0, 100),
            textColor = parseColor(preferences.widgetTextColor),
            textScale = preferences.widgetTextScale,
        )

        /** `#RRGGBB` 或 `#AARRGGBB`；空串（跟随主题）与非法值都返回 null。 */
        fun parseColor(text: String): Int? {
            val value = text.trim()
            if (!value.startsWith("#")) return null
            return runCatching { Color.parseColor(value) }.getOrNull()
        }
    }

    /** 实际画到桌面上的背景色（含透明度）。 */
    fun resolvedBackground(context: Context): Int {
        val base = backgroundColor ?: context.getColor(R.color.widget_surface)
        val alpha = (backgroundAlpha.coerceIn(0, 100) * 255 / 100)
        return (base and 0x00FFFFFF) or (alpha shl 24)
    }

    /** 主文字色；null 表示交给布局里的 `values-night` 资源。 */
    fun resolvedTextColor(): Int? = textColor

    /** 次要文字：同色但压暗一点，保留主次层级。 */
    fun resolvedSecondaryTextColor(): Int? {
        val primary = textColor ?: return null
        val alpha = (Color.alpha(primary) * 0.72f).toInt().coerceIn(0, 255)
        return (primary and 0x00FFFFFF) or (alpha shl 24)
    }
}

/**
 * 背景必须带圆角，而 RemoteViews 没法给任意颜色的圆角图形——所以这里直接画一张位图交给
 * `ImageView`。尺寸取小组件当前的 dp 尺寸（系统每次改尺寸都会重新发一次渲染）。
 */
object WidgetBackground {

    /** 圆角 16dp：再大就会把顶部那行日期压进弧线里，看着像跑到卡片外面。 */
    private const val CORNER_DP = 16f

    /** 系统没报尺寸时的兜底（4×2 的常见值）。 */
    private const val FALLBACK_WIDTH_DP = 320
    private const val FALLBACK_HEIGHT_DP = 140

    fun bitmap(context: Context, appearance: WidgetAppearance, widthDp: Int, heightDp: Int): Bitmap {
        val density = context.resources.displayMetrics.density
        val width = ((if (widthDp > 0) widthDp else FALLBACK_WIDTH_DP) * density).toInt().coerceIn(1, 4096)
        val height = ((if (heightDp > 0) heightDp else FALLBACK_HEIGHT_DP) * density).toInt().coerceIn(1, 4096)
        val color = appearance.resolvedBackground(context)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        val radius = CORNER_DP * density
        canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), radius, radius, paint)
        return bitmap
    }
}
