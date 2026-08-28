package com.cycling.rssradar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cycling.rssradar.data.GROUP_DESIGN
import com.cycling.rssradar.data.GROUP_DEV
import com.cycling.rssradar.data.GROUP_TECH
import com.cycling.rssradar.ui.components.FeedIcon
import com.cycling.rssradar.ui.theme.Accent
import com.cycling.rssradar.ui.theme.BgRoot
import com.cycling.rssradar.ui.theme.Link
import com.cycling.rssradar.ui.theme.OnAccent
import com.cycling.rssradar.ui.theme.Surface1
import com.cycling.rssradar.ui.theme.Surface2
import com.cycling.rssradar.ui.theme.Success
import com.cycling.rssradar.ui.theme.TextPrimary
import com.cycling.rssradar.ui.theme.TextSecondary
import com.cycling.rssradar.ui.theme.TextTertiary
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.CircleCheckBig
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Link2
import com.composables.icons.lucide.Lucide

@Composable
fun AddSubscriptionScreen(
    viewModel: AddSubscriptionViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val recentlyAdded by viewModel.recentlyAdded.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val message = viewModel.uiMessage

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onMessageShown()
        }
    }

    Scaffold(
        containerColor = BgRoot,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { AddSubscriptionTopBar(onBack = onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                FieldLabel("订阅源链接")
                Spacer(Modifier.height(8.dp))
                UrlField(
                    value = state.url,
                    onChange = viewModel::onUrlChange,
                    isLoading = state.isValidating,
                )
                state.validation?.let { ValidationBanner(it) }
            }

            item {
                FieldLabel("添加到分组")
                Spacer(Modifier.height(8.dp))
                GroupChips(
                    options = listOf(GROUP_TECH, GROUP_DEV, GROUP_DESIGN),
                    selected = state.selectedGroup,
                    onSelect = viewModel::onGroupSelected,
                    onCreate = { viewModel.onGroupSelected("新分组") },
                )
            }

            item {
                FieldLabel("RSSHub 路由示例")
                Text(
                    text = "同时支持 RSSHub 路由与常规 RSS / Atom 链接",
                    color = TextTertiary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.routeSamples.forEach { sample ->
                        RouteSampleRow(sample = sample, onUse = {
                            viewModel.onUrlChange(sample.path)
                            viewModel.onGroupSelected(sample.suggestedGroup)
                        })
                    }
                }
            }

            item {
                Button(
                    onClick = { viewModel.submit() },
                    enabled = state.canSubmit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Accent,
                        contentColor = OnAccent,
                        disabledContainerColor = Accent.copy(alpha = 0.4f),
                        disabledContentColor = OnAccent,
                    ),
                ) {
                    if (state.isAdding) {
                        CircularProgressIndicator(
                            color = OnAccent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp),
                        )
                    } else {
                        Text(
                            text = "添加订阅",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "添加时将校验链接有效性，校验失败会给出具体原因",
                    color = TextTertiary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (recentlyAdded.isNotEmpty()) {
                item {
                    FieldLabel("最近添加")
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        recentlyAdded.forEach { item ->
                            RecentlyAddedRow(item = item)
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun AddSubscriptionTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Lucide.ArrowLeft, contentDescription = "返回", tint = TextPrimary)
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = "添加订阅",
            color = TextPrimary,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        color = TextSecondary,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun UrlField(
    value: String,
    onChange: (String) -> Unit,
    isLoading: Boolean,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = {
            Text(
                "https://example.com/feed.xml",
                color = TextTertiary,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        singleLine = true,
        leadingIcon = {
            Icon(Lucide.Link2, contentDescription = null, tint = TextTertiary)
        },
        trailingIcon = {
            if (isLoading) {
                CircularProgressIndicator(
                    color = Accent,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp),
                )
            }
        },
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Surface1,
            unfocusedContainerColor = Surface1,
            focusedBorderColor = Accent,
            unfocusedBorderColor = Surface2,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            cursorColor = Accent,
        ),
    )
}

@Composable
private fun ValidationBanner(info: ValidationInfo) {
    val color = when (info) {
        is ValidationInfo.Valid -> Success
        is ValidationInfo.Invalid -> MaterialTheme.colorScheme.error
        is ValidationInfo.Network -> MaterialTheme.colorScheme.error
        is ValidationInfo.Idle -> Color.Transparent
    }
    if (info is ValidationInfo.Idle) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (info is ValidationInfo.Valid) {
            Icon(
                Lucide.CircleCheckBig,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = info.message,
            color = color,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun GroupChips(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onCreate: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { name ->
            GroupChip(label = name, selected = name == selected, onClick = { onSelect(name) })
        }
        GroupChip(label = "+ 新建分组", selected = false, onClick = onCreate, accentText = true)
    }
}

@Composable
private fun GroupChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    accentText: Boolean = false,
) {
    val bg = if (selected) Accent else Surface1
    val border = if (selected) Color.Transparent else Surface2
    val fg = when {
        selected -> OnAccent
        accentText -> Link
        else -> TextPrimary
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = bg,
        modifier = Modifier
            .border(1.dp, border, RoundedCornerShape(50))
            .clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            color = fg,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun RouteSampleRow(sample: RouteSample, onUse: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Surface1,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onUse),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sample.path,
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = sample.suggestedTitle,
                    color = TextTertiary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = { /* 复制到剪贴板 */ }) {
                Icon(Lucide.Copy, contentDescription = "复制", tint = TextSecondary)
            }
        }
    }
}

@Composable
private fun RecentlyAddedRow(item: RecentlyAdded) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Surface1,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FeedIcon(title = item.feed.title, size = 28.dp, cornerRadius = 7.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                text = item.feed.title,
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .background(Surface2, RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            ) {
                Text(
                    text = item.relativeTime,
                    color = TextTertiary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}
