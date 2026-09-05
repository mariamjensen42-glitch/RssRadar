package com.cycling.rssradar.ui.me

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.cycling.rssradar.core.data.ai.AiFeature
import com.cycling.rssradar.core.data.ai.AiPrompts
import com.cycling.rssradar.core.data.db.FeedAiProfileDao
import com.cycling.rssradar.core.data.db.FeedAiProfileEntity
import com.cycling.rssradar.core.data.db.FeedDao
import com.cycling.rssradar.core.ui.theme.radarColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 一个已配置摘要提示词覆盖的订阅源。 */
data class FeedPromptOverride(val feedId: Long, val feedTitle: String, val prompt: String)

/** 供「新增覆盖」选择用的订阅源（只带 id 与标题，全部源都可选，不止已配置的）。 */
data class FeedPickOption(val feedId: Long, val feedTitle: String)

data class PromptTemplatesUiState(
    /** 已配置覆盖的订阅源（管理列表）。 */
    val overrides: List<FeedPromptOverride> = emptyList(),
    /** 全部订阅源（新增覆盖时选择用）。 */
    val feeds: List<FeedPickOption> = emptyList(),
    val loading: Boolean = true,
    val message: String? = null,
)

sealed interface PromptTemplatesIntent {
    data object Refresh : PromptTemplatesIntent
    data class SavePrompt(val feedId: Long, val prompt: String) : PromptTemplatesIntent
    data class ClearPrompt(val feedId: Long) : PromptTemplatesIntent
    data object ConsumeMessage : PromptTemplatesIntent
}

/**
 * 提示词模板管理（AiFeature.PROMPT_TEMPLATE）的状态宿主。
 *
 * 存储口径与 SubscriptionsViewModel.setFeedSummaryPrompt 完全一致：
 * 空白模板一律当「清除覆盖」——存一个只有空格的模板等于让模型收到空 system；
 * 新建行时用 upsert 兜底（该源没有 profile 行时 updateSummaryPrompt 更新 0 行等于白存）。
 * 刻意**不清除**已生成的摘要：旧摘要忠实于原文，用户点「重新生成」即可套用新提示词。
 */
@HiltViewModel
class PromptTemplatesViewModel @Inject constructor(
    private val profileDao: FeedAiProfileDao,
    private val feedDao: FeedDao,
) : ViewModel() {

    private val _state = MutableStateFlow(PromptTemplatesUiState())
    val state: StateFlow<PromptTemplatesUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun onIntent(intent: PromptTemplatesIntent) {
        when (intent) {
            PromptTemplatesIntent.Refresh -> refresh()
            is PromptTemplatesIntent.SavePrompt -> save(intent.feedId, intent.prompt)
            is PromptTemplatesIntent.ClearPrompt -> clear(intent.feedId)
            PromptTemplatesIntent.ConsumeMessage -> _state.update { it.copy(message = null) }
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val profiles = profileDao.getAll().filter { !it.summaryPrompt.isNullOrBlank() }
            val feeds = feedDao.getAll().map { FeedPickOption(it.id, it.title) }
            val titleOf = feeds.associate { it.feedId to it.feedTitle }
            _state.update {
                it.copy(
                    loading = false,
                    overrides = profiles
                        .map { p -> FeedPromptOverride(p.feedId, titleOf[p.feedId] ?: "已删除的源", p.summaryPrompt.orEmpty()) }
                        .sortedBy { it.feedTitle },
                    feeds = feeds,
                )
            }
        }
    }

    private fun save(feedId: Long, prompt: String) {
        val normalized = prompt.trim().takeIf { it.isNotBlank() }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val current = profileDao.get(feedId)
            if (current == null && normalized == null) return@launch
            if (current == null) {
                profileDao.upsert(FeedAiProfileEntity(feedId = feedId, summaryPrompt = normalized, updatedAt = now))
            } else {
                profileDao.updateSummaryPrompt(feedId, normalized, now)
            }
            _state.update {
                it.copy(
                    message = if (normalized == null) "已改用内置摘要提示词" else "已保存该订阅源的摘要提示词",
                )
            }
            refresh()
        }
    }

    private fun clear(feedId: Long) = save(feedId, "")
}

/**
 * 提示词模板管理页（AiFeature.PROMPT_TEMPLATE 的专属出口）。
 *
 * 覆盖能力只有一条真实存在的路径：**订阅源级摘要提示词**（SUMMARY 是 35 项里
 * 唯一支持覆盖的功能——不同源的信息密度差得远，共用一套模板必然有一边不合适）。
 * 本页因此做三件事，且只做这三件：
 * 1. 集中管理已有覆盖（单源入口在订阅源操作页，那里有上下文；这里是全局视图）；
 * 2. 预览内置摘要模板原文——用户改之前有权知道"默认长什么样"；
 * 3. 变量说明。不提供「为每项功能编辑全局模板」——那个覆盖链路不存在，
 *    造一个假的编辑框存进无人读取的地方，比不提供更糟。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptTemplatesScreen(
    viewModel: PromptTemplatesViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var editing by remember { mutableStateOf<FeedPromptOverride?>(null) }
    var adding by remember { mutableStateOf(false) }
    val colors = radarColors()

    Scaffold(
        containerColor = colors.bgRoot,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Lucide.ArrowLeft, contentDescription = "返回", tint = colors.textPrimary)
                }
                Text(
                    text = "提示词模板",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { adding = true }) {
                    Icon(Lucide.Plus, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("新增覆盖", color = colors.accent, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.accent)
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = "目前支持订阅源级摘要提示词覆盖。${AiPrompts.summaryVariableHelp()}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textTertiary,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            Text(
                text = "自定义覆盖 · ${state.overrides.size}",
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
            )
            if (state.overrides.isEmpty()) {
                Surface(shape = RoundedCornerShape(12.dp), color = colors.surface1) {
                    Text(
                        text = "还没有订阅源自定义提示词。所有订阅源目前使用下方内置模板；点右上角「新增覆盖」为单个源定制。",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            } else {
                state.overrides.forEach { item ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colors.surface1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .clickable { editing = item },
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                text = item.feedTitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = item.prompt,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            Text(
                text = "内置摘要模板",
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
            )
            Surface(shape = RoundedCornerShape(12.dp), color = colors.surface1) {
                Text(
                    text = AiPrompts.builtInSummaryPrompt(),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp),
                )
            }
            Spacer(Modifier.height(96.dp))
        }
    }

    editing?.let { item ->
        PromptEditSheet(
            title = item.feedTitle,
            initial = item.prompt,
            onSave = { text ->
                viewModel.onIntent(PromptTemplatesIntent.SavePrompt(item.feedId, text))
                editing = null
            },
            onClear = {
                viewModel.onIntent(PromptTemplatesIntent.ClearPrompt(item.feedId))
                editing = null
            },
            onDismiss = { editing = null },
        )
    }

    if (adding) {
        FeedPickSheet(
            feeds = state.feeds,
            onPicked = { picked ->
                adding = false
                editing = FeedPromptOverride(picked.feedId, picked.feedTitle, "")
            },
            onDismiss = { adding = false },
        )
    }
}

/** 编辑单个订阅源的摘要提示词。有已存模板时才出现「清除」。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PromptEditSheet(
    title: String,
    initial: String,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = radarColors()
    var text by remember { mutableStateOf(initial) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.bgRoot) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = AiPrompts.summaryVariableHelp(),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textTertiary,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                placeholder = { Text("留空保存即改用内置模板", color = colors.textTertiary, style = MaterialTheme.typography.bodyMedium) },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colors.surface2,
                    unfocusedContainerColor = colors.surface2,
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    cursorColor = colors.accent,
                ),
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (initial.isNotBlank()) {
                    TextButton(onClick = onClear) { Text("清除覆盖", color = colors.textTertiary) }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { onSave(text) }) {
                    Text("保存", color = colors.accent, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** 选一个订阅源来新增覆盖。源可能上千，必须带搜索过滤，列表只渲染命中的前 30 条。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedPickSheet(
    feeds: List<FeedPickOption>,
    onPicked: (FeedPickOption) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = radarColors()
    var query by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.bgRoot) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
            Text(
                text = "选择订阅源",
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜索订阅源", color = colors.textTertiary, style = MaterialTheme.typography.bodyMedium) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colors.surface2,
                    unfocusedContainerColor = colors.surface2,
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    cursorColor = colors.accent,
                ),
            )
            Spacer(Modifier.height(10.dp))
            val matched = remember(query, feeds) {
                val q = query.trim()
                (if (q.isEmpty()) feeds else feeds.filter { it.feedTitle.contains(q, ignoreCase = true) }).take(30)
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(matched, key = { it.feedId }) { option ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = colors.surface1,
                        modifier = Modifier.fillMaxWidth().clickable { onPicked(option) },
                    ) {
                        Text(
                            text = option.feedTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
