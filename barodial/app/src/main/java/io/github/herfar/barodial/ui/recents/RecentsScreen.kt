package io.github.herfar.barodial.ui.recents

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import io.github.herfar.barodial.R
import io.github.herfar.barodial.data.calllog.CallFilter
import io.github.herfar.barodial.data.calllog.CallGroup
import io.github.herfar.barodial.data.calllog.CallHistoryAvailability
import io.github.herfar.barodial.data.calllog.CallType
import io.github.herfar.barodial.data.contacts.Contact
import io.github.herfar.barodial.ui.RecentsUiState
import io.github.herfar.barodial.ui.components.CallIconButton
import io.github.herfar.barodial.ui.components.CircleIcon
import io.github.herfar.barodial.ui.components.ContactAvatar
import io.github.herfar.barodial.ui.components.EmptyMessage
import io.github.herfar.barodial.ui.components.InfoCard
import io.github.herfar.barodial.ui.components.ListRow
import io.github.herfar.barodial.ui.components.PhoneDisplay
import io.github.herfar.barodial.ui.components.SectionHeader
import io.github.herfar.barodial.ui.components.TimeDisplay
import io.github.herfar.barodial.ui.components.asAnnotated
import io.github.herfar.barodial.ui.icons.AppIcons
import io.github.herfar.barodial.ui.theme.LocalCallColors

class RecentsActions(
    val openSystemCallLog: () -> Unit,
    val requestPermission: () -> Unit,
    val openAppSettings: () -> Unit,
    val setFilter: (CallFilter) -> Unit,
    val callNumber: (String) -> Unit,
    val openContact: (Contact) -> Unit,
    val openKeypadWith: (String) -> Unit,
)

/**
 * 통화기록 탭.
 * 통화기록 권한이 없으면 실제 기록처럼 보이는 어떤 목록도 표시하지 않고,
 * 이유를 설명한 뒤 기본 전화 앱(삼성 전화)의 기록 화면으로 보내 준다.
 */
@Composable
fun RecentsScreen(
    availability: CallHistoryAvailability,
    state: RecentsUiState,
    filter: CallFilter,
    dialerLabel: String?,
    canOpenCallLogScreen: Boolean,
    actions: RecentsActions,
    modifier: Modifier = Modifier,
) {
    val phoneApp = dialerLabel ?: stringResource(R.string.default_phone_app)
    when (availability) {
        CallHistoryAvailability.NotIncludedInBuild -> ExplanationPage(
            title = stringResource(R.string.recents_unavailable_title, phoneApp),
            body = stringResource(R.string.recents_unavailable_body),
            icon = AppIcons.Lock,
            primaryLabel = stringResource(
                if (canOpenCallLogScreen) R.string.recents_open_system else R.string.recents_open_dialer,
                phoneApp,
            ),
            onPrimary = actions.openSystemCallLog,
            modifier = modifier,
        )
        is CallHistoryAvailability.PermissionRequired -> ExplanationPage(
            title = stringResource(R.string.recents_permission_title),
            body = stringResource(R.string.recents_permission_body),
            icon = AppIcons.History,
            primaryLabel = stringResource(R.string.recents_permission_allow),
            onPrimary = actions.requestPermission,
            secondaryLabel = stringResource(R.string.recents_open_system, phoneApp),
            onSecondary = actions.openSystemCallLog,
            modifier = modifier,
        )
        is CallHistoryAvailability.Denied -> ExplanationPage(
            title = stringResource(R.string.recents_denied_title),
            body = stringResource(R.string.recents_denied_body),
            icon = AppIcons.Lock,
            primaryLabel = stringResource(R.string.recents_open_system, phoneApp),
            onPrimary = actions.openSystemCallLog,
            secondaryLabel = stringResource(R.string.action_open_app_settings),
            onSecondary = actions.openAppSettings,
            modifier = modifier,
        )
        CallHistoryAvailability.Available -> CallLogList(state, filter, actions, modifier)
    }
}

@Composable
private fun ExplanationPage(
    title: String,
    body: String,
    icon: ImageVector,
    primaryLabel: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryLabel: String? = null,
    onSecondary: () -> Unit = {},
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .testTag("recentsExplanation"),
    ) {
        InfoCard(title = title, body = body, icon = icon) {
            Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPrimary, modifier = Modifier.fillMaxWidth().testTag("recentsPrimary")) { Text(primaryLabel) }
                if (secondaryLabel != null) {
                    OutlinedButton(onClick = onSecondary, modifier = Modifier.fillMaxWidth()) { Text(secondaryLabel) }
                }
            }
        }
        Text(
            text = stringResource(R.string.recents_home_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun CallLogList(state: RecentsUiState, filter: CallFilter, actions: RecentsActions, modifier: Modifier) {
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (f in CallFilter.entries) {
                FilterChip(
                    selected = f == filter,
                    onClick = { actions.setFilter(f) },
                    label = {
                        Text(
                            stringResource(
                                when (f) {
                                    CallFilter.ALL -> R.string.filter_all
                                    CallFilter.MISSED -> R.string.filter_missed
                                    CallFilter.INCOMING -> R.string.filter_incoming
                                    CallFilter.OUTGOING -> R.string.filter_outgoing
                                },
                            ),
                        )
                    },
                )
            }
        }
        if (state.sections.isEmpty()) {
            EmptyMessage(stringResource(R.string.recents_empty), icon = AppIcons.History)
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize().testTag("callLogList"), contentPadding = PaddingValues(bottom = 12.dp)) {
            for (section in state.sections) {
                item(key = "day_${section.date}") { SectionHeader(TimeDisplay.dayHeader(section.date)) }
                items(section.groups, key = { "call_${it.latest.id}" }) { group ->
                    CallGroupRow(group, state.contactsByNumber[group.latest.nationalDigits], actions)
                }
            }
        }
    }
}

@Composable
private fun CallGroupRow(group: CallGroup, contact: Contact?, actions: RecentsActions) {
    val call = group.latest
    val colors = LocalCallColors.current
    val formatted = PhoneDisplay.format(call.number, call.nationalDigits)
    val name = contact?.displayName ?: call.cachedName
    val (icon, tint, typeLabel) = when (call.type) {
        CallType.OUTGOING -> Triple(AppIcons.CallMade, colors.outgoing, R.string.call_type_outgoing)
        CallType.MISSED -> Triple(AppIcons.CallMissed, colors.missed, R.string.call_type_missed)
        CallType.REJECTED -> Triple(AppIcons.CallMissed, colors.missed, R.string.call_type_rejected)
        CallType.BLOCKED -> Triple(AppIcons.CallMissed, colors.missed, R.string.call_type_blocked)
        else -> Triple(AppIcons.CallReceived, colors.incoming, R.string.call_type_incoming)
    }
    val missed = call.type == CallType.MISSED || call.type == CallType.REJECTED
    ListRow(
        title = buildAnnotatedString {
            append(name ?: formatted.ifEmpty { stringResource(R.string.unknown_number) })
            if (group.count > 1) append("  (${group.count})")
        },
        subtitle = listOfNotNull(
            stringResource(typeLabel),
            if (name != null) formatted else null,
            TimeDisplay.time(call.timestampMillis),
            if (!missed && call.durationSeconds > 0) TimeDisplay.duration(call.durationSeconds) else null,
        ).joinToString(" · ").asAnnotated(),
        onClick = { if (contact != null) actions.openContact(contact) else actions.openKeypadWith(call.number) },
        leading = {
            if (contact != null) {
                ContactAvatar(contact.displayName, contact.photoThumbnailUri, 44.dp)
            } else {
                CircleIcon(icon, tint = tint)
            }
        },
        trailing = {
            if (call.number.isNotEmpty()) {
                CallIconButton(onClick = { actions.callNumber(call.number) }, contentDescription = stringResource(R.string.action_call_target, name ?: formatted))
            }
        },
    )
}
