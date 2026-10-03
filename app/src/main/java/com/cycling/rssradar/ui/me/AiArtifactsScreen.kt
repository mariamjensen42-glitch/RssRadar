package com.cycling.rssradar.ui.me

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RotateCw
import com.composables.icons.lucide.Sparkles
import com.cycling.rssradar.R
import com.cycling.rssradar.core.data.ai.AiArtifactItem
import com.cycling.rssradar.core.data.ai.AiPayloadText
import com.cycling.rssradar.core.model.AiScope
import com.cycling.rssradar.core.ui.components.AppSnackbarHost
import com.cycling.rssradar.core.ui.components.EmptyState
import com.cycling.rssradar.core.ui.theme.radarColors
import com.cycling.rssradar.i18n.resolve
import java.text.SimpleDateFormat
import java.util.Locale

// AiArtifactDetail 定义在同包的 AiArtifactsViewModel 里，同包无需 import。

/**
 * AI 产物中心：把 `ai_artifacts` 里的全部产物按功能摊开，随手可查。
 *
 * 为什么要有这一页：35 项 AI 功能里只有一部分有专属展示位，其余的执行器照常跑、
 * 产物照常落库，但 App 里没有任何地方能看到它们，用户只觉得"跑成功了，结果呢？"。
 * 这一页不认识任何 payload 的具体类型（渲染交给 [AiPayloadText]），
 * 因此新增功能**零成本**自动纳入，不需要为每项功能再写一个页面。
 *
 * 三条刻意的呈现决定：
 * 1. **先按功能分组、再按时间倒序**——用户来这里是带着"XX 功能到底出结果没有"
 *    这个问题来的，按功能聚合能一眼扫到；同一功能内部按时间排，最新的在最前。
 * 2. **每条都显示主体标题**（文章标题 / 订阅源名），不是一串 subjectId。
 *    数字 id 对人没有意义，看不出这条结果挂在哪篇文章上。
 * 3. **原文可展开**。模型输出是唯一的原始证据，"AI 说它做了什么"和"模型实际说了什么"
 *    必须都能看到，否则排查时只能靠猜。
 */
@OptIn(ExperimentalMaterial3Api::class)

@Composable

fun AiArtifactsScreen(
    viewModel: AiArtifactsViewModel = hiltViewModel(),
    onBack: () -> Unit,
    /** 文章级产物跳详情；不传则该按钮不出现（不让用户点一个没有落点的按钮）。 */
    onOpenArticle: (Long) -> Unit = {},
    /** 订阅源级产物跳该源文章列表。 */
    onOpenFeed: (Long) -> Unit = {},
    /**
     * 从总览页「查看结果」进来时预选的功能（dbValue）。
     * 用 LaunchedEffect 应用而不是在 VM 构造时读路由参数：VM 是 hiltViewModel
     * 默认创建的，不知道路由；而筛选一次即可，不该把路由耦合进 VM 生命周期。
     */
    initialFeatureDbValue: Int? = null,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val timeFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    LaunchedEffect(initialFeatureDbValue) {
        if (initialFeatureDbValue != null) {
            viewModel.onIntent(AiArtifactsIntent.SelectKind(initialFeatureDbValue))
        }
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(message.resolve(context))
        viewModel.onIntent(AiArtifactsIntent.ConsumeMessage)
    }

    Scaffold(
        containerColor = radarColors().bgRoot,
        snackbarHost = { AppSnackbarHost(snackbar) },
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Lucide.ArrowLeft,
                        contentDescription = stringResource(R.string.back),
                        tint = radarColors().textPrimary,
                    )
                }
                Text(
                    text = stringResource(R.string.ai_results_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = radarColors().textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { viewModel.onIntent(AiArtifactsIntent.Refresh) }) {
                    Icon(
                        imageVector = Lucide.RotateCw,
                        contentDescription = stringResource(R.string.refresh),
                        tint = radarColors().textSecondary,
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
        ) {
            OverviewRow(
                total = state.items.size,
                featureCount = state.groups.size,
                outputChars = state.groups.sumOf { it.outputChars },
            )
            Spacer(Modifier.height(12.dp))

            if (state.groups.isNotEmpty()) {
                FeatureFilterRow(
                    groups = state.groups,
                    selected = state.selectedKind,
                    onSelect = { viewModel.onIntent(AiArtifactsIntent.SelectKind(it)) },
                )
                Spacer(Modifier.height(12.dp))
            }

            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = radarColors().accent)
                }

                state.items.isEmpty() -> EmptyState(
                    icon = Lucide.Sparkles,
                    message = if (state.groups.isEmpty()) stringResource(R.string.artifacts_empty_none) else stringResource(R.string.artifacts_empty_feature),
                    hint = if (state.groups.isEmpty()) {
                        stringResource(R.string.artifacts_empty_hint)
                    } else {
                        stringResource(R.string.artifacts_empty_queue_hint)
                    },
                    // 同理必须给权重：EmptyState 内部是 fillMaxSize，不给权重会顶掉剩余空间。
                    modifier = Modifier.weight(1f),
                )

                else -> ArtifactList(
                    items = state.items,
                    timeFormat = timeFormat,
                    onOpen = { viewModel.onIntent(AiArtifactsIntent.OpenDetail(it)) },
                    // weight 只能在这一层给：ArtifactList 是独立的 @Composable，
                    // 它函数体内拿不到 ColumnScope，在里面写 Modifier.weight 编译不过。
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    state.detail?.let { detail ->
        AiArtifactDetailSheet(
            detail = detail,
            timeFormat = timeFormat,
            onOpenArticle = onOpenArticle,
            onOpenFeed = onOpenFeed,
            onDelete = { viewModel.onIntent(AiArtifactsIntent.Delete(detail.item)) },
            onDismiss = { viewModel.onIntent(AiArtifactsIntent.DismissDetail) },
        )
    }
}

// ── 总览与筛选 ──────────────────────────────────────────────────────────────

/** 条目上的主体名。查不到标题时不留空——给「文章 #id」比一片空白好定位。 */
@Composable

internal fun subjectLabel(item: AiArtifactItem): String = when (item.scope) {
    AiScope.ARTICLE -> item.subjectTitle ?: stringResource(R.string.subject_article, item.subjectId)
    AiScope.FEED -> item.subjectTitle ?: stringResource(R.string.subject_feed, item.subjectId)
    AiScope.GLOBAL -> stringResource(R.string.subject_global, item.subjectId)
}

/** 列表预览：取渲染结果的第一行有意义文本，省得用户为了看一句结论点开每一条。 */
internal fun previewOf(item: AiArtifactItem): String {
    val lines = AiPayloadText.lines(item.payload)
    val first = lines.firstOrNull { it.value.length >= 2 } ?: return ""
    return if (first.label != null) "${first.label}：${first.value}" else first.value
}

// ── 详情面板 ────────────────────────────────────────────────────────────────
