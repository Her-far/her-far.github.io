package io.github.herfar.barodial.ui.recents

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
import io.github.herfar.barodial.data.recordings.CallRecording
import io.github.herfar.barodial.data.recordings.RecordingsAvailability
import io.github.herfar.barodial.ui.RecentsUiState
import io.github.herfar.barodial.ui.RecordingItem
import io.github.herfar.barodial.ui.components.CallIconButton
import io.github.herfar.barodial.ui.components.CircleIcon
import io.github.herfar.barodial.ui.components.ContactAvatar
import io.github.herfar.barodial.ui.components.EmptyMessage
import io.github.herfar.barodial.ui.components.InfoCard
import io.github.herfar.barodial.ui.components.ListRow
import io.github.herfar.barodial.ui.components.PhoneDisplay
import io.github.herfar.barodial.ui.components.RecordingRow
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
    val sendSms: (String) -> Unit,
    val openContact: (Contact) -> Unit,
    val openNumber: (String) -> Unit,
    val playRecording: (CallRecording, String) -> Unit,
    val requestRecordingsPermission: () -> Unit,
)

/** 녹음 파일의 표시 이름: 연락처 → 파일 이름의 이름/번호 → 파일 이름 */
fun recordingTitle(item: RecordingItem): String =
    item.contact?.displayName
        ?: item.recording.label?.let { if (item.recording.labelDigits != null) PhoneDisplay.format(it) else it }
        ?: item.recording.displayName

/**
 * 최근기록 탭.
 * 통화기록 권한이 없으면 실제 기록처럼 보이는 어떤 목록도 표시하지 않고,
 * 이유를 설명한 뒤 기본 전화 앱(삼성 전화)의 기록 화면으로 보내 준다.
 * 통화 녹음 파일은 권한이 있으면 따로 보여 준다 (실제 파일이므로 가짜 기록이 아니다).
 */
@Composable
fun RecentsScreen(
    availability: CallHistoryAvailability,
    recordingsAvailability: RecordingsAvailability,
    state: RecentsUiState,
    filter: CallFilter,
    dialerLabel: String?,
    canOpenCallLogScreen: Boolean,
    actions: RecentsActions,
    modifier: Modifier = Modifier,
) {
    val phoneApp = dialerLabel ?: stringResource(R.string.default_phone_app)
    val openSystemLabel = stringResource(
        if (canOpenCallLogScreen) R.string.recents_open_system else R.string.recents_open_dialer,
        phoneApp,
    )
    when (availability) {
        CallHistoryAvailability.Available -> CallLogList(state, filter, recordingsAvailability, actions, modifier)
        else -> LazyColumn(modifier.fillMaxSize().testTag("recentsExplanation"), contentPadding = PaddingValues(bottom = 16.dp)) {
            item("explanation") {
                when (availability) {
                    // 아직 요청 전: 허용하면 삼성 전화의 최근기록을 이 앱에서 그대로 본다
                    is CallHistoryAvailability.PermissionRequired -> Explanation(
                        title = stringResource(R.string.recents_permission_title),
                        body = stringResource(R.string.recents_permission_body),
                        icon = AppIcons.History,
                        primaryLabel = stringResource(R.string.recents_permission_allow),
                        onPrimary = actions.requestPermission,
                        secondaryLabel = openSystemLabel,
                        onSecondary = actions.openSystemCallLog,
                    )
                    // 거부·차단됨 (파일로 설치한 경우 Android 가 막을 수 있음): 이유 안내 + 삼성 전화로
                    else -> Explanation(
                        title = stringResource(R.string.recents_denied_title),
                        body = stringResource(R.string.recents_denied_body, phoneApp),
                        icon = AppIcons.Lock,
                        primaryLabel = openSystemLabel,
                        onPrimary = actions.openSystemCallLog,
                        secondaryLabel = stringResource(R.string.action_open_app_settings),
                        onSecondary = actions.openAppSettings,
                    )
                }
            }
            recordingsSection(state.recordings, recordingsAvailability, actions)
        }
    }
}

@Composable
private fun Explanation(
    title: String,
    body: String,
    icon: ImageVector,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: () -> Unit = {},
) {
    InfoCard(title = title, body = body, icon = icon) {
        Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onPrimary, modifier = Modifier.fillMaxWidth().testTag("recentsPrimary")) { Text(primaryLabel) }
            if (secondaryLabel != null) {
                OutlinedButton(onClick = onSecondary, modifier = Modifier.fillMaxWidth().testTag("recentsSecondary")) { Text(secondaryLabel) }
            }
        }
    }
}

/** 통화 녹음 파일 목록 (통화기록이 없을 때) */
private fun LazyListScope.recordingsSection(
    recordings: List<RecordingItem>,
    availability: RecordingsAvailability,
    actions: RecentsActions,
) {
    item("recHeader") { SectionHeader(stringResource(R.string.recordings_title)) }
    when (availability) {
        is RecordingsAvailability.PermissionRequired, is RecordingsAvailability.Denied -> item("recPermission") {
            RecordingsPermissionBanner(availability is RecordingsAvailability.Denied, actions)
        }
        RecordingsAvailability.Available -> {
            if (recordings.isEmpty()) {
                item("recEmpty") { EmptyMessage(stringResource(R.string.recordings_empty), icon = AppIcons.Mic) }
            }
            items(recordings, key = { "rec_${it.recording.id}" }) { item ->
                val title = recordingTitle(item)
                RecordingRow(title = title, recording = item.recording, onPlay = { actions.playRecording(item.recording, title) })
            }
        }
    }
}

@Composable
private fun RecordingsPermissionBanner(denied: Boolean, actions: RecentsActions) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).testTag("recordingsPermission"),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(AppIcons.Mic, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(if (denied) R.string.recordings_permission_denied else R.string.recordings_permission_body),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = if (denied) actions.openAppSettings else actions.requestRecordingsPermission,
                modifier = Modifier.testTag("allowRecordingsButton"),
            ) {
                Text(stringResource(if (denied) R.string.action_settings_short else R.string.permission_allow_short))
            }
        }
    }
}

@Composable
private fun CallLogList(
    state: RecentsUiState,
    filter: CallFilter,
    recordingsAvailability: RecordingsAvailability,
    actions: RecentsActions,
    modifier: Modifier,
) {
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (f in CallFilter.entries) {
                if (f == CallFilter.RECORDED && recordingsAvailability != RecordingsAvailability.Available) continue
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
                                    CallFilter.RECORDED -> R.string.filter_recorded
                                },
                            ),
                        )
                    },
                    leadingIcon = if (f == CallFilter.RECORDED) {
                        { Icon(AppIcons.Mic, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else {
                        null
                    },
                    modifier = Modifier.testTag("filter_${f.name}"),
                )
            }
        }
        LazyColumn(Modifier.fillMaxSize().testTag("callLogList"), contentPadding = PaddingValues(bottom = 12.dp)) {
            if (recordingsAvailability != RecordingsAvailability.Available) {
                item("recPermission") { RecordingsPermissionBanner(recordingsAvailability is RecordingsAvailability.Denied, actions) }
            }
            if (state.sections.isEmpty()) {
                item("empty") { EmptyMessage(stringResource(R.string.recents_empty), icon = AppIcons.History) }
            }
            for (section in state.sections) {
                item(key = "day_${section.date}") { SectionHeader(TimeDisplay.dayHeader(section.date)) }
                items(section.groups, key = { "call_${it.latest.id}" }) { group ->
                    val recording = group.entries.firstNotNullOfOrNull { state.recordingByCallId[it.id] }
                    CallGroupRow(group, state.contactsByNumber[group.latest.nationalDigits], recording, actions)
                }
            }
        }
    }
}

@Composable
private fun CallGroupRow(group: CallGroup, contact: Contact?, recording: CallRecording?, actions: RecentsActions) {
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
    val title = name ?: formatted.ifEmpty { stringResource(R.string.unknown_number) }
    ListRow(
        title = buildAnnotatedString {
            append(title)
            if (group.count > 1) append("  (${group.count})")
        },
        // 연락처 이름이 있으면 번호는 생략해 시각·통화 시간이 잘리지 않게 한다 (번호는 상세에서 확인)
        subtitle = listOfNotNull(
            stringResource(typeLabel),
            if (name != null && contact == null) formatted else null,
            TimeDisplay.time(call.timestampMillis),
            if (!missed && call.durationSeconds > 0) TimeDisplay.duration(call.durationSeconds) else null,
        ).joinToString(" · ").asAnnotated(),
        onClick = { if (contact != null) actions.openContact(contact) else actions.openNumber(call.number) },
        leading = {
            if (contact != null) {
                ContactAvatar(contact.displayName, contact.photoThumbnailUri, 44.dp)
            } else {
                CircleIcon(icon, tint = tint)
            }
        },
        trailing = {
            // 좁은 화면에서도 이름이 덜 잘리도록 ▶·문자 버튼은 40dp, 통화 버튼은 48dp
            if (recording != null) {
                IconButton(
                    onClick = { actions.playRecording(recording, title) },
                    modifier = Modifier.size(40.dp).testTag("playCall_${call.id}"),
                ) {
                    Icon(AppIcons.PlayArrow, contentDescription = stringResource(R.string.recording_play_target, title), tint = MaterialTheme.colorScheme.tertiary)
                }
            }
            if (call.number.isNotEmpty()) {
                IconButton(
                    onClick = { actions.sendSms(call.number) },
                    modifier = Modifier.size(40.dp).testTag("smsCall_${call.id}"),
                ) {
                    Icon(
                        AppIcons.Message,
                        contentDescription = stringResource(R.string.action_sms_target, title),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                CallIconButton(onClick = { actions.callNumber(call.number) }, contentDescription = stringResource(R.string.action_call_target, title))
            }
        },
        modifier = Modifier.testTag("callRow_${call.id}"),
    )
}
