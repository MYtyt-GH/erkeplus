package gitee.leolin_me.erkepp.debug

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import gitee.leolin_me.erkepp.data.AppPreferences
import gitee.leolin_me.erkepp.ui.widget.BaseWidgetProvider
import gitee.leolin_me.erkepp.ui.widget.WidgetRefresh
import gitee.leolin_me.erkepp.ui.widget.board.BoardWidgetProvider
import gitee.leolin_me.erkepp.ui.widget.halfday.HalfDayWidgetProvider
import gitee.leolin_me.erkepp.ui.widget.recent.RecentWidgetProvider

/**
 * 只在 debug 包里存在的「小组件预览」：按指定尺寸渲染一遍真实 RemoteViews。
 *
 * 为什么需要它：桌面上的小组件要靠启动器的长按菜单拖出来，自动化很难稳定重现；
 * 而 RemoteViews.apply 与桌面的渲染是同一段代码，所以这里看到的就是桌面上的样子。
 *
 * ```
 * adb shell am start -n gitee.leolin_me.erkepp.debug/gitee.leolin_me.erkepp.debug.WidgetPreviewActivity \
 *     --es which board --ei width 320 --ei height 140 --es style card
 * ```
 *
 * `which`：board / recent / halfday；`width`/`height` 是 dp；`style`：card / glass / clear（可选）。
 */
class WidgetPreviewActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 可选：临时改外观，配合截图看不同配色/字号
        val preferences = AppPreferences(this)
        var appearanceChanged = false
        // `--es bg theme` 表示恢复「跟随主题」（am 不接受空字符串参数）
        intent.getStringExtra("bg")?.let { preferences.widgetBackgroundColor = themeIfAsked(it); appearanceChanged = true }
        if (intent.hasExtra("alpha")) {
            preferences.widgetBackgroundAlpha = intent.getIntExtra("alpha", 100)
            appearanceChanged = true
        }
        intent.getStringExtra("fg")?.let { preferences.widgetTextColor = themeIfAsked(it); appearanceChanged = true }
        if (intent.hasExtra("scale")) {
            preferences.widgetTextScale = intent.getIntExtra("scale", 100)
            appearanceChanged = true
        }
        if (appearanceChanged) WidgetRefresh.all(this)

        val which = intent.getStringExtra("which").orEmpty().ifBlank { "board" }
        val widthDp = intent.getIntExtra("width", 320).coerceAtLeast(80)
        val heightDp = intent.getIntExtra("height", 140).coerceAtLeast(60)

        val provider: BaseWidgetProvider = when (which) {
            "recent" -> RecentWidgetProvider()
            "halfday" -> HalfDayWidgetProvider()
            else -> BoardWidgetProvider()
        }
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
        }

        val density = resources.displayMetrics.density
        val widthPx = (widthDp * density).toInt()
        val heightPx = (heightDp * density).toInt()

        // 一块「假装是壁纸」的深色底，方便看圆角与描边；预览页本身不参与入库
        val root = FrameLayout(this).apply {
            setBackgroundColor(0xFF35405C.toInt())
            setPadding(32, 32, 32, 32)
        }
        val host = FrameLayout(this)
        val applied = provider.render(this, 0, options).apply(this, host)
        root.addView(applied, FrameLayout.LayoutParams(widthPx, heightPx).apply {
            gravity = Gravity.CENTER
        })

        val caption = TextView(this).apply {
            text = "$which ${widthDp}x${heightDp}dp"
            setTextColor(0xCCFFFFFF.toInt())
            textSize = 11f
        }
        root.addView(caption, FrameLayout.LayoutParams(-2, -2).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        })

        setContentView(root)
    }

    private fun themeIfAsked(value: String): String = if (value == "theme") "" else value
}
