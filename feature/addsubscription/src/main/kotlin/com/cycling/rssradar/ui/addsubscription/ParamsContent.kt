package com.cycling.rssradar.ui.addsubscription

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Zap
import com.cycling.rssradar.core.model.rsshub.RouteParam
import com.cycling.rssradar.core.model.rsshub.RssHubRoute
import com.cycling.rssradar.core.ui.theme.radarOutlinedTextFieldColors

@Composable

internal fun ColumnScope.ParamsContent(
    state: AddSubscriptionUiState,
    route: RssHubRoute,
    viewModel: AddSubscriptionViewModel,
) {
    val listState = rememberLazyListState()
    // 生成地址后把结果区滚进视野：参数一多，结果就在屏幕外，用户会以为点了没反应。
    // 等一帧再滚——重组刚把结果 item 加进列表，这一帧的 layoutInfo 还没它。
    LaunchedEffect(state.url) {
        if (state.url.isNotBlank()) {
            withFrameMillis { }
            listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .imePadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column {
                CodeBlock(text = route.path)
                if (route.description.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = route.description,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        if (route.params.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.add_no_params),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        } else {
            items(route.params, key = { it.key }) { param ->
                ParamField(
                    param = param,
                    value = state.paramValues[param.key].orEmpty(),
                    onChange = { viewModel.onIntent(AddSubscriptionIntent.ParamChange(param.key, it)) },
                )
            }
        }

        if (route.examples.isNotEmpty()) {
            item {
                ExamplePicker(
                    examples = route.examples,
                    onSelect = { viewModel.onIntent(AddSubscriptionIntent.ExampleSelected(it)) },
                )
            }
        }

        item {
            Column {
                // 结果由哪个实例解析，写在按钮上方：实例不可达时这是最先要核对的信息
                Text(
                    text = stringResource(R.string.add_resolved_by, state.host),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Button(
                    // 缺参数时按钮照样可点——点了就在下面说缺哪个。置灰不吭声 = 用户只会以为坏了。
                    onClick = { viewModel.onIntent(AddSubscriptionIntent.PreviewRoute) },
                    enabled = !state.isValidating && !state.isAdding,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f),
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    Icon(
                        imageVector = Lucide.Zap,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.add_generate_preview),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (state.missingParams.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.add_still_need) + state.missingParams.joinToString("、") {
                            it.label.ifBlank { it.key }
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        // 结果区：校验中 / 成功 / 失败都在这里出，且 loading 与结果不互斥——
        // 之前两者写成 if/else，生成地址后校验的那十几秒里界面上什么都没有。
        if (state.isUrlFromRoute || state.isValidating || state.validation !is ValidationInfo.Idle) {
            item {
                PreviewResult(state = state, viewModel = viewModel)
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable

private fun ParamField(
    param: RouteParam,
    value: String,
    onChange: (String) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${param.label} · :${param.key}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (param.optional) {
                Spacer(Modifier.width(6.dp))
                OptionalTag()
            }
        }

        // 有枚举值就用 chips 选：比让用户照着说明手打可靠
        if (param.options.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(param.options, key = { it.value }) { option ->
                    FilterChip(
                        selected = value == option.value,
                        onClick = { onChange(option.value) },
                        label = { Text(option.label) },
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = param.fallback ?: stringResource(R.string.add_required),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = radarOutlinedTextFieldColors(),
        )
        // 说明与标签重复时不再啰嗦一遍
        if (param.description.isNotBlank() && param.description != param.label) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = param.description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable

private fun OptionalTag() {
    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp),
    ) {
        Text(
            text = stringResource(R.string.add_optional),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable

internal fun FieldLabel(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
}
