package com.cycling.rssradar.ui.me

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.rssradar.data.rsshub.RssHubInstanceStore
import com.cycling.rssradar.data.store.ThemeMode
import com.cycling.rssradar.data.store.ThemeStore
import com.cycling.rssradar.ui.theme.Accent
import com.cycling.rssradar.ui.theme.BgRoot
import com.cycling.rssradar.ui.theme.OnAccent
import com.cycling.rssradar.ui.theme.Surface1
import com.cycling.rssradar.ui.theme.Surface2
import com.cycling.rssradar.ui.theme.Surface3
import com.cycling.rssradar.ui.theme.TextPrimary
import com.cycling.rssradar.ui.theme.TextSecondary
import com.cycling.rssradar.ui.theme.TextTertiary
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.CircleCheckBig
import com.composables.icons.lucide.Lucide
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

import com.cycling.rssradar.ui.subscriptions.String

data class RssHubSettingsUiState(
    /** 当前生效的实例。 */
    val activeHost: String = "",
    /** 用户自定义实例输入。 */
    val customInput: String = "",
    val probing: Boolean = false,
    /** 最近一次探测的提示文案；null 表示没有要展示的提示。 */
    val probeMessage: String? = null,
    /** 当前主题模式。 */
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)

/**
 * 「我的」页：RSSHub 实例设置 + 主题设置。
 * 实例：查看当前实例、修改自定义实例、并发探测可达性（issue #14）。
 * 主题：浅色 / 深色 / 跟随系统（issue #9）。
 */
@HiltViewModel
class RssHubSettingsViewModel @Inject constructor(
    private val store: RssHubInstanceStore,
    private val themeStore: ThemeStore,
) : ViewModel() {

    private val _state = MutableStateFlow(RssHubSettingsUiState(activeHost = store.currentOrDefault()))
    val state: StateFlow<RssHubSettingsUiState> = _state.asStateFlow()

    init {
        // 主题模式跟随 ThemeStore 的 flow，设置页外（系统切换）也同步
        viewModelScope.launch {
            themeStore.mode.collect { mode ->
                _state.value = _state.value.copy(themeMode = mode)
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        themeStore.setMode(mode)
    }

    fun onCustomInputChange(value: String) {
        _state.value = _state.value.copy(customInput = value)
    }

    fun saveCustomHost() {
        val raw = _state.value.customInput.trim()
        if (raw.isEmpty()) {
            store.customHost = null
            _state.value = _state.value.copy(activeHost = store.currentOrDefault(), probeMessage = "已清除自定义实例")
            return
        }
        val normalized = normalizeHost(raw) ?: run {
            _state.value = _state.value.copy(probeMessage = "实例地址格式不正确")
            return
        }
        store.customHost = normalized
        _state.value = _state.value.copy(
            activeHost = store.currentOrDefault(),
            customInput = normalized,
            probeMessage = "已保存：$normalized",
        )
    }

    /** 并发探测内置镜像 + 自定义实例，选首个可达者并记住。 */
    fun probeNow() {
        if (_state.value.probing) return
        viewModelScope.launch {
            _state.value = _state.value.copy(probing = true, probeMessage = null)
            val available = store.refreshAvailableHost()
            _state.value = _state.value.copy(
                probing = false,
                activeHost = store.currentOrDefault(),
                probeMessage = if (available != null) {
                    "探测到可用实例：$available"
                } else {
                    "所有内置实例均不可达，请检查网络或填入自建实例"
                },
            )
        }
    }

    private fun normalizeHost(raw: String): String? {
        val withScheme = if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://$raw"
        return runCatching {
            java.net.URL(withScheme).let { it.protocol + "://" + it.host + (it.port.takeIf { p -> p != -1 }?.let { p -> ":$p" } ?: "") }
        }.getOrNull()
    }
}

@Composable
fun RssHubSettingsScreen(
    viewModel: RssHubSettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Text(
            text = "我的",
            color = TextPrimary,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 12.dp),
        )

        Text(
            text = "外观",
            color = TextSecondary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Surface(shape = RoundedCornerShape(14.dp), color = Surface1) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "主题",
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            val selected = state.themeMode == mode
                            val bg = if (selected) Accent else Surface2
                            val fg = if (selected) OnAccent else TextPrimary
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = bg,
                                modifier = Modifier.clickable { viewModel.setThemeMode(mode) },
                            ) {
                                Text(
                                    text = when (mode) {
                                        ThemeMode.SYSTEM -> "跟随系统"
                                        ThemeMode.LIGHT -> "浅色"
                                        ThemeMode.DARK -> "深色"
                                    },
                                    color = fg,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        Text(
            text = "RSSHub 实例",
            color = TextSecondary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "路由解析由 RSSHub 实例完成。官方实例在部分网络环境不可达，可自动探测或填入自建实例。",
            color = TextTertiary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )

        Surface(shape = RoundedCornerShape(14.dp), color = Surface1) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "当前实例",
                        color = TextTertiary,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = state.activeHost,
                        color = TextPrimary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = viewModel::probeNow,
                    enabled = !state.probing,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Accent,
                        contentColor = OnAccent,
                    ),
                ) {
                    if (state.probing) {
                        CircularProgressIndicator(color = OnAccent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("探测中…", style = MaterialTheme.typography.labelLarge)
                    } else {
                        Text("自动探测可用实例", style = MaterialTheme.typography.labelLarge)
                    }
                }
                state.probeMessage?.let { message ->
                    Spacer(Modifier.height(8.dp))
                    Text(text = message, color = TextTertiary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = "自定义实例（可选）",
            color = TextSecondary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.customInput,
            onValueChange = viewModel::onCustomInputChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("https://your-rsshub.example.com", color = TextTertiary, style = MaterialTheme.typography.bodyMedium) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Surface2,
                unfocusedContainerColor = Surface2,
                focusedBorderColor = Accent,
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = Accent,
            ),
        )
        Text(
            text = "自定义实例优先于探测结果；留空并保存则清除。",
            color = TextTertiary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = viewModel::saveCustomHost) {
            Text("保存", color = Accent, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(24.dp))
        Text(
            text = "内置镜像（按优先级）",
            color = TextTertiary,
            style = MaterialTheme.typography.labelMedium,
        )
        Spacer(Modifier.height(6.dp))
        RssHubInstanceStore.BUILTIN_INSTANCES.forEachIndexed { index, host ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Surface1,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clickable { viewModel.onCustomInputChange(host) },
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${index + 1}. $host",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    if (host == state.activeHost) {
                        Text("当前", color = Accent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}
