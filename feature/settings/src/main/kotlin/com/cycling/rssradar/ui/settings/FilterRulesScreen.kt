package com.cycling.rssradar.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.ArrowDown
import com.composables.icons.lucide.ArrowUp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Trash2
import com.cycling.rssradar.core.domain.filter.FilterRule
import com.cycling.rssradar.core.ui.components.SettingsSubPage
import com.cycling.rssradar.core.ui.text.resolve
import com.cycling.rssradar.core.ui.theme.Danger
import com.cycling.rssradar.core.ui.theme.RssRadarTheme
import com.cycling.rssradar.core.ui.theme.radarColors
import com.cycling.rssradar.core.ui.theme.radarSwitchColors

@Composable
fun FilterRulesDestination(
    onBack: () -> Unit = {},
    viewModel: FilterRulesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    FilterRulesScreen(
        state = state,
        onBack = onBack,
        onStartCreate = viewModel::startCreate,
        onStartEdit = viewModel::startEdit,
        onCancelEdit = viewModel::cancelEdit,
        onToggle = viewModel::toggle,
        onMove = viewModel::move,
        onDelete = viewModel::delete,
        onPreview = viewModel::preview,
        onSave = viewModel::save,
    )
}

/** 本地关键词过滤规则：规则命中后的动作由 FilterRuleEngine 在刷新链与通知链执行。 */
@Composable
fun FilterRulesScreen(
    state: FilterRulesViewModel.UiState,
    onBack: () -> Unit = {},
    onStartCreate: () -> Unit = {},
    onStartEdit: (FilterRule) -> Unit = {},
    onCancelEdit: () -> Unit = {},
    onToggle: (FilterRule, Boolean) -> Unit = { _, _ -> },
    onMove: (FilterRule, Int) -> Unit = { _, _ -> },
    onDelete: (Long) -> Unit = {},
    onPreview: (FilterRule) -> Unit = {},
    onSave: (FilterRule) -> Unit = {},
) {
    Box(modifier = Modifier.fillMaxSize()) {
        SettingsSubPage(title = stringResource(R.string.rule_title), onBack = onBack) {
            if (state.rules.isEmpty()) {
                Text(
                    text = stringResource(R.string.rule_empty),
                    color = radarColors().textTertiary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else {
                state.rules.forEachIndexed { index, rule ->
                    RuleCard(
                        rule = rule,
                        canMoveUp = index > 0,
                        canMoveDown = index < state.rules.lastIndex,
                        onToggle = { onToggle(rule, it) },
                        onEdit = { onStartEdit(rule) },
                        onMoveUp = { onMove(rule, -1) },
                        onMoveDown = { onMove(rule, 1) },
                        onDelete = { onDelete(rule.id) },
                    )
                }
            }
            state.message?.let { message ->
                Spacer(Modifier.height(12.dp))
                Text(
                    text = message.resolve(),
                    color = radarColors().textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.height(72.dp))
        }

        FloatingActionButton(
            onClick = onStartCreate,
            containerColor = radarColors().accent,
            contentColor = radarColors().onAccent,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 28.dp),
        ) {
            Icon(Lucide.Plus, contentDescription = stringResource(R.string.rule_new))
        }
    }

    state.editing?.let { rule ->
        RuleEditorSheet(
            rule = rule,
            feeds = state.feeds,
            groups = state.groups,
            previewCount = state.previewCount,
            onPreview = onPreview,
            onSave = onSave,
            onDelete = onDelete,
            onDismiss = onCancelEdit,
        )
    }
}

@Composable
private fun RuleCard(
    rule: FilterRule,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = radarColors().surface1,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    ) {
        Column(modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onEdit),
                ) {
                    Text(
                        text = rule.name,
                        color = radarColors().textPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "${stringResource(rule.matchType.labelRes())} · ${stringResource(rule.action.labelRes())}",
                        color = radarColors().textTertiary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = rule.pattern,
                        color = radarColors().textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = onToggle,
                    colors = radarSwitchColors(),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                    Icon(
                        Lucide.ArrowUp,
                        contentDescription = stringResource(R.string.rule_move_up),
                        tint = if (canMoveUp) radarColors().textSecondary else radarColors().textTertiary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                    Icon(
                        Lucide.ArrowDown,
                        contentDescription = stringResource(R.string.rule_move_down),
                        tint = if (canMoveDown) radarColors().textSecondary else radarColors().textTertiary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(
                        Lucide.Pencil,
                        contentDescription = stringResource(R.string.rule_edit),
                        tint = radarColors().textSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Lucide.Trash2,
                        contentDescription = stringResource(R.string.rule_delete),
                        tint = Danger,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "过滤规则 · 空态")
@Composable
private fun FilterRulesScreenEmptyPreview() {
    RssRadarTheme(darkTheme = false) {
        FilterRulesScreen(state = FilterRulesViewModel.UiState())
    }
}

@Preview(showBackground = true, name = "过滤规则 · 有规则")
@Composable
private fun FilterRulesScreenPreview() {
    RssRadarTheme(darkTheme = true) {
        FilterRulesScreen(
            state = FilterRulesViewModel.UiState(
                rules = listOf(
                    FilterRule(name = "隐藏剧透", pattern = "剧透"),
                    FilterRule(name = "屏蔽广告", pattern = "广告", enabled = false),
                ),
            ),
        )
    }
}
