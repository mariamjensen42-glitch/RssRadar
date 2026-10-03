package com.cycling.rssradar.ui.search

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.FolderOpen
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.X
import com.cycling.rssradar.R
import com.cycling.rssradar.core.data.db.projection.ArticleWithFeed
import com.cycling.rssradar.core.ui.components.AppSnackbarHost
import com.cycling.rssradar.core.ui.theme.radarColors
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onOpenArticle: (ArticleWithFeed) -> Unit = {},
    /** 空态托底入口：跳订阅管理页（订阅源多的时候按源浏览比关键词更顺手）。 */
    onOpenSubscriptions: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val feeds by viewModel.feeds.collectAsState()
    val focusRequester = remember { FocusRequester() }
    val snackbarHostState = remember { SnackbarHostState() }
    // LaunchedEffect 不是组合作用域，文案只能经 context.getString 取（ADR-0017）
    val context = LocalContext.current

    // 删除撤销（issue #46），与信息流一致
    LaunchedEffect(state.pendingUndoDelete) {
        state.pendingUndoDelete?.let { deleted ->
            val result = snackbarHostState.showSnackbar(
                message = context.getString(R.string.search_deleted, deleted.title),
                actionLabel = context.getString(R.string.search_undo),
                duration = SnackbarDuration.Short,
            )
            when (result) {
                SnackbarResult.ActionPerformed -> viewModel.onIntent(SearchIntent.UndoDeleteArticle)
                SnackbarResult.Dismissed -> viewModel.onIntent(SearchIntent.DiscardUndo)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(radarColors().bgRoot)) {
        Column(modifier = Modifier.fillMaxSize()) {
            SearchBar(
                query = state.query,
                onQueryChange = { viewModel.onIntent(SearchIntent.QueryChange(it)) },
                onClear = { viewModel.onIntent(SearchIntent.QueryChange("")) },
                onSubmit = { viewModel.onIntent(SearchIntent.Submit) },
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .focusRequester(focusRequester),
            )

            if (state.query.isBlank()) {
                RecentSearches(
                    history = state.history,
                    onPick = { viewModel.onIntent(SearchIntent.QueryChange(it)) },
                    onClear = { viewModel.onIntent(SearchIntent.ClearHistory) },
                    onDeleteItem = { viewModel.onIntent(SearchIntent.DeleteHistoryItem(it)) },
                )
                // 无历史时的托底内容：整页只剩一句"暂无搜索记录"太空洞
                if (state.history.isEmpty()) {
                    IdleSuggestions(onOpenSubscriptions = onOpenSubscriptions)
                }
            } else {
                SearchResults(
                    state = state,
                    feeds = feeds,
                    onOpenArticle = onOpenArticle,
                    onToggleRead = { id, read -> viewModel.onIntent(SearchIntent.SetRead(id, read)) },
                    onToggleStarred = { id -> viewModel.onIntent(SearchIntent.ToggleStarred(id)) },
                    onToggleBookmarked = { id -> viewModel.onIntent(SearchIntent.ToggleBookmarked(id)) },
                    onDelete = { id -> viewModel.onIntent(SearchIntent.DeleteArticle(id)) },
                    onIntent = viewModel::onIntent,
                    onLoadMore = { viewModel.onIntent(SearchIntent.LoadMore) },
                )
            }
        }
        AppSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 键盘 action 触发搜索（UI 审计 S1）：移动端惯例，去掉右上角文字按钮
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = {
            Text(
                stringResource(R.string.search_placeholder),
                color = radarColors().textTertiary,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        singleLine = true,
        shape = RoundedCornerShape(50),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        leadingIcon = {
            Icon(Lucide.Search, contentDescription = null, tint = radarColors().textTertiary, modifier = Modifier.size(18.dp))
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Lucide.X, contentDescription = stringResource(R.string.search_clear), tint = radarColors().textTertiary, modifier = Modifier.size(18.dp))
                }
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = radarColors().surface1,
            unfocusedContainerColor = radarColors().surface1,
            focusedBorderColor = radarColors().accent,
            unfocusedBorderColor = radarColors().surface2,
            focusedTextColor = radarColors().textPrimary,
            unfocusedTextColor = radarColors().textPrimary,
            cursorColor = radarColors().accent,
        ),
    )
}

@Composable
private fun RecentSearches(
    history: List<String>,
    onPick: (String) -> Unit,
    onClear: () -> Unit,
    /** 删除单条历史（UI 审计 S2）。 */
    onDeleteItem: (String) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.search_recent),
                color = radarColors().textPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (history.isNotEmpty()) {
                TextButton(onClick = onClear) {
                    Text(
                        text = stringResource(R.string.search_clear_history),
                        color = radarColors().textTertiary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (history.isEmpty()) {
            Text(
                text = stringResource(R.string.search_no_history),
                color = radarColors().textTertiary,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            // 用 FlowRow 效果的最简实现：3 个一行手写（如果 chips 太多可换 FlowRow）
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                history.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { term ->
                            HistoryChip(
                                term = term,
                                onClick = { onPick(term) },
                                onDelete = { onDeleteItem(term) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 无搜索历史时的托底：给「按订阅源浏览」一条出路，页面不再是空洞的一片。 */
@Composable
private fun IdleSuggestions(onOpenSubscriptions: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = radarColors().surface1,
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .fillMaxWidth()
            .clickable(onClick = onOpenSubscriptions),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Lucide.FolderOpen,
                contentDescription = null,
                tint = radarColors().accent,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.search_browse_feeds),
                    color = radarColors().textPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.search_browse_feeds_hint),
                    color = radarColors().textTertiary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Icon(
                Lucide.ChevronRight,
                contentDescription = null,
                tint = radarColors().textTertiary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun HistoryChip(term: String, onClick: () -> Unit, onDelete: (String) -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = radarColors().surface1,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = term,
                color = radarColors().textPrimary,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 14.dp),
            )
            // 尾随 × 删除单条（UI 审计 S2）
            IconButton(onClick = { onDelete(term) }, modifier = Modifier.size(24.dp)) {
                Icon(
                    Lucide.X,
                    contentDescription = stringResource(R.string.search_delete_history_item, term),
                    tint = radarColors().textTertiary,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}
