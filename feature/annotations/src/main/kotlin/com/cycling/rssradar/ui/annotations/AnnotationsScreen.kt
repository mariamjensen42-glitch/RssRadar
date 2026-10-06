package com.cycling.rssradar.ui.annotations

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.cycling.rssradar.core.ui.R as UiR
import com.cycling.rssradar.core.data.db.AnnotationWithArticle
import com.cycling.rssradar.core.domain.annotation.AnnotationPalette
import com.cycling.rssradar.core.ui.components.ConfirmDialog
import com.cycling.rssradar.core.ui.components.EmptyState
import com.cycling.rssradar.core.ui.theme.radarColors
import com.cycling.rssradar.ui.annotations.R
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Highlighter
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Trash2

/**
 * 标注列表：全库的高亮与笔记，按标注时间倒序。
 *
 * 点击一条 → 回到那篇文章继续读（标注只有回到上下文里才有意义）。
 * 每条的左侧色条就是它被划下时的颜色，与正文里的底色同源（[AnnotationPalette]）。
 */
@Composable
fun AnnotationsDestination(
    onBack: () -> Unit,
    onOpenArticle: (Long) -> Unit,
    viewModel: AnnotationsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AnnotationsScreen(
        items = state.items,
        pending = state.pendingDelete,
        onBack = onBack,
        onOpenArticle = onOpenArticle,
        onAskDelete = viewModel::askDelete,
        onConfirmDelete = viewModel::confirmDelete,
        onCancelDelete = viewModel::cancelDelete,
    )
}

@Composable
fun AnnotationsScreen(
    items: List<AnnotationWithArticle>,
    pending: AnnotationWithArticle?,
    onBack: () -> Unit,
    onOpenArticle: (Long) -> Unit,
    onAskDelete: (AnnotationWithArticle) -> Unit = {},
    onConfirmDelete: () -> Unit = {},
    onCancelDelete: () -> Unit = {},
) {
    val colors = radarColors()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgRoot)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Lucide.ArrowLeft, contentDescription = stringResource(UiR.string.back), tint = colors.textPrimary)
            }
            Text(
                text = stringResource(R.string.annotations_title),
                color = colors.textPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        if (items.isEmpty()) {
            EmptyState(
                icon = Lucide.Highlighter,
                message = stringResource(R.string.annotations_empty),
                hint = stringResource(R.string.annotations_empty_hint),
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items, key = { it.annotation.id }) { item ->
                    AnnotationCard(
                        item = item,
                        onOpen = { onOpenArticle(item.annotation.articleId) },
                        onDelete = { onAskDelete(item) },
                    )
                }
            }
        }
    }

    if (pending != null) {
        ConfirmDialog(
            title = stringResource(R.string.ann_delete_title),
            text = stringResource(R.string.ann_delete_message),
            confirmText = stringResource(UiR.string.delete),
            dismissText = stringResource(UiR.string.cancel),
            destructive = true,
            onConfirm = onConfirmDelete,
            onDismiss = onCancelDelete,
        )
    }
}

@Composable
private fun AnnotationCard(
    item: AnnotationWithArticle,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = radarColors()
    val annotation = item.annotation
    val shape = RoundedCornerShape(12.dp)
    Surface(
        color = colors.surface1,
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onOpen),
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(Color(AnnotationPalette.colorAt(annotation.color))),
            )
            Column(modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, top = 12.dp, bottom = 12.dp)) {
                Text(
                    text = annotation.quote,
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                annotation.note?.let { note ->
                    Text(
                        text = note,
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.articleTitle ?: stringResource(R.string.ann_unknown_article),
                        color = colors.textTertiary,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = relativeTime(annotation.createdAt),
                        color = colors.textTertiary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Lucide.Trash2,
                    contentDescription = stringResource(UiR.string.delete),
                    tint = colors.textSecondary,
                )
            }
        }
    }
}

private fun relativeTime(millis: Long): String =
    DateUtils.getRelativeTimeSpanString(
        millis,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
    ).toString()
