package gitee.leolin_me.erkepp.ui.more

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gitee.leolin_me.erkepp.data.AppPreferences
import gitee.leolin_me.erkepp.ui.widget.WidgetAppearance
import gitee.leolin_me.erkepp.ui.widget.WidgetEntry
import gitee.leolin_me.erkepp.ui.widget.WidgetRefresh
import gitee.leolin_me.erkepp.ui.widget.WidgetRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 「更多 → 桌面小组件」：外观自己调 + 把组件放上桌面。
 *
 * 为什么要有这一页：国产 ROM 把「添加小部件」藏得很深（长按桌面 → 编辑 → 小部件 → 找应用），
 * 用户很难找到；这里先替用户调起系统的「固定到桌面」，调不起来再给出明确的手动路径。
 *
 * 外观做成可调而不是固定三档，是因为壁纸千差万别：背景色、透明度、文字颜色、文字大小
 * 交给用户，比自己替用户猜一种「万能配色」靠谱。
 */
@Composable
fun WidgetScreen(preferences: AppPreferences, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    // 失败时一并给出「去开权限」的按钮：国产 ROM 常把「创建桌面快捷方式」默认关掉
    var messageNeedsPermission by remember { mutableStateOf(false) }

    fun fail(text: String) {
        message = text
        messageNeedsPermission = true
    }

    fun pushToDesktop(entry: WidgetEntry) {
        val manager = AppWidgetManager.getInstance(context)
        val provider = ComponentName(context, entry.provider)
        val before = manager.getAppWidgetIds(provider).size
        val supported = runCatching { manager.isRequestPinAppWidgetSupported }.getOrDefault(false)
        val requested = supported && runCatching {
            manager.requestPinAppWidget(provider, null, null)
        }.getOrDefault(false)
        if (!requested) {
            fail("这个桌面不支持从应用里直接添加。\n\n长按桌面空白处 → 小组件（小部件）→ 二课++，" +
                "选「${entry.label}」；如果长按也找不到，去系统设置里允许本应用「创建桌面快捷方式」再试。")
            return
        }
        // 有些桌面会收下请求却不弹确认框，过两秒看一眼有没有真的加上
        scope.launch {
            delay(2500)
            val after = AppWidgetManager.getInstance(context).getAppWidgetIds(provider).size
            if (after <= before) {
                fail("桌面没有接住这次添加。\n\n" +
                    "多半是系统关掉了「创建桌面快捷方式」权限：点下面的「去设置」打开应用权限，允许后再回来添加；" +
                    "也可以长按桌面空白处 → 小组件（小部件）→ 二课++，选「${entry.label}」。")
            }
        }
    }

    fun refreshWidgets() {
        WidgetRefresh.all(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("返回") }
            Text(
                text = "桌面小组件",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("外观", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "改完立刻生效，桌面上已经摆着的组件会跟着变。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(12.dp))
                    Text("背景色", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    ColorSwatches(
                        options = BACKGROUND_COLORS,
                        selected = preferences.widgetBackgroundColor,
                        onPick = {
                            preferences.widgetBackgroundColor = it
                            refreshWidgets()
                        },
                    )

                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("背景透明度", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "${preferences.widgetBackgroundAlpha}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Slider(
                        value = preferences.widgetBackgroundAlpha.toFloat(),
                        onValueChange = { preferences.widgetBackgroundAlpha = it.toInt() },
                        onValueChangeFinished = { refreshWidgets() },
                        valueRange = 0f..100f,
                        steps = 19,
                    )
                    Text(
                        text = "100% 不透、能看到卡片底色；调低会透出壁纸。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("毛玻璃", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "小组件读不到壁纸，做不了真模糊；这里用半透明近似（背景 80%）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = preferences.widgetBackgroundAlpha in 1..95,
                            onCheckedChange = { on ->
                                preferences.widgetBackgroundAlpha = if (on) 80 else 100
                                refreshWidgets()
                            },
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    Text("文字颜色", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    ColorSwatches(
                        options = TEXT_COLORS,
                        selected = preferences.widgetTextColor,
                        onPick = {
                            preferences.widgetTextColor = it
                            refreshWidgets()
                        },
                    )

                    Spacer(Modifier.height(14.dp))
                    Text("文字大小", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TEXT_SCALES.forEach { (scale, label) ->
                            FilterChip(
                                selected = preferences.widgetTextScale == scale,
                                onClick = {
                                    preferences.widgetTextScale = scale
                                    refreshWidgets()
                                },
                                // 三档用同一套字号，选中与否只靠底色和勾表示：
                                // 之前选中项的字被加粗，看着像「标准」比「大」还大
                                label = {
                                    Text(text = label, style = MaterialTheme.typography.bodyMedium)
                                },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("添加到桌面", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "选一种放上去，之后可以长按拖动改大小。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    WidgetRegistry.all.forEach { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { pushToDesktop(entry) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(entry.label, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = entry.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = "添加",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "小组件只读手机里的课表缓存，不会自己联网；课表在应用里刷新后，桌面会跟着更新。" +
                    "点一下任何一块小组件都能直接进课表。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))
        }
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            title = { Text("添加到桌面") },
            text = { Text(text) },
            confirmButton = {
                if (messageNeedsPermission) {
                    TextButton(onClick = {
                        message = null
                        messageNeedsPermission = false
                        openDesktopShortcutSettings(context)
                    }) { Text("去设置") }
                } else {
                    TextButton(onClick = { message = null }) { Text("知道了") }
                }
            },
            dismissButton = if (messageNeedsPermission) {
                { TextButton(onClick = { message = null; messageNeedsPermission = false }) { Text("知道了") } }
            } else {
                null
            },
        )
    }
}

/**
 * 打开「创建桌面快捷方式」权限页。
 *
 * 小米/HyperOS 在权限编辑器里，但入口各家不一：先试 MIUI 的权限编辑器，
 * 不行再退到应用详情页，用户自己往下找。
 */
private fun openDesktopShortcutSettings(context: Context) {
    val packageName = context.packageName
    val miuiPages = listOf(
        "com.miui.permcenter.permissions.PermissionsEditorActivity",
        "com.miui.permcenter.permissions.AppPermissionsEditorActivity",
    )
    val opened = miuiPages.any { page ->
        runCatching {
            context.startActivity(
                Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                    setClassName("com.miui.securitycenter", page)
                    putExtra("extra_pkgname", packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }.isSuccess
    }
    if (opened) return
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/** 颜色选项：`null` 表示「跟随主题」。 */
private data class ColorOption(val value: String?, val label: String)

private val BACKGROUND_COLORS = listOf(
    ColorOption(null, "主题"),
    ColorOption("#FFFFFFFF", "白"),
    ColorOption("#FFF2F3F5", "浅灰"),
    ColorOption("#FFE7E0D4", "米白"),
    ColorOption("#FF1B1C1E", "深灰"),
    ColorOption("#FF000000", "黑"),
    ColorOption("#FF17325C", "深蓝"),
    ColorOption("#FF14352F", "墨绿"),
    ColorOption("#FF3A2540", "深紫"),
)

private val TEXT_COLORS = listOf(
    ColorOption(null, "主题"),
    ColorOption("#FFFFFFFF", "白"),
    ColorOption("#FFE3E4E6", "浅灰"),
    ColorOption("#FF1B1C1E", "深灰"),
    ColorOption("#FF000000", "黑"),
    ColorOption("#FF2F6FE0", "蓝"),
)

private val TEXT_SCALES = listOf(90 to "小", 100 to "标准", 115 to "大")

@Composable
private fun ColorSwatches(
    options: List<ColorOption>,
    selected: String,
    onPick: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        options.forEach { option ->
            val isSelected = (option.value ?: "") == selected
            Column(
                modifier = Modifier
                    .clickable { onPick(option.value.orEmpty()) }
                    .padding(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val color = option.value
                    ?.let { WidgetAppearance.parseColor(it) }
                    ?.let { Color(it) }
                    ?: Color.Unspecified
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            color = if (color == Color.Unspecified) {
                                MaterialTheme.colorScheme.surface
                            } else {
                                color
                            },
                            shape = CircleShape,
                        )
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            shape = CircleShape,
                        ),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}
