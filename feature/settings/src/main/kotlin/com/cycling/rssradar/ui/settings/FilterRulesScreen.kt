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
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.cycling.rssradar.core.domain.filter.FilterRule
import com.cycling.rssradar.core.ui.theme.Danger
import com.cycling.rssradar.core.ui.theme.radarColors
import com.cycling.rssradar.core.ui.text.resolve
import com.composables.icons.lucide.ArrowDown
import com.composables.icons.lucide.ArrowUp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Trash2
import com.cycling.rssradar.core.ui.components.SettingsSubPage
import androidx.compose.runtime.setValue

/** 本地关键词过滤规则：规则命中后的动作由 FilterRuleEngine 在刷新链与通知链执行。 */
@Composable
fun FilterRulesScreen(
    viewModel: FilterRulesViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()

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
                        onToggle = { viewModel.toggle(rule, it) },
                        onEdit = { viewModel.startEdit(rule) },
                        onMoveUp = { viewModel.move(rule, -1) },
                        onMoveDown = { viewModel.move(rule, 1) },
                        onDelete = { viewModel.delete(rule.id) },
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
            onClick = viewModel::startCreate,
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
            onPreview = viewModel::preview,
            onSave = viewModel::save,
            onDelete = viewModel::delete,
            onDismiss = viewModel::cancelEdit,
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
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = radarColors().onAccent,
                        checkedTrackColor = radarColors().accent,
                    ),
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
