package io.github.herfar.barodial.ui.contacts

import android.content.ClipData
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.herfar.barodial.R
import io.github.herfar.barodial.data.calllog.CallLogEntry
import io.github.herfar.barodial.data.calllog.CallLogGrouping
import io.github.herfar.barodial.data.calllog.CallType
import io.github.herfar.barodial.data.contacts.Contact
import io.github.herfar.barodial.data.contacts.PhoneNumber
import io.github.herfar.barodial.data.recordings.CallRecording
import io.github.herfar.barodial.ui.components.AppTopBar
import io.github.herfar.barodial.ui.components.CallIconButton
import io.github.herfar.barodial.ui.components.ContactAvatar
import io.github.herfar.barodial.ui.components.EmptyMessage
import io.github.herfar.barodial.ui.components.PhoneDisplay
import io.github.herfar.barodial.ui.components.RecordingRow
import io.github.herfar.barodial.ui.components.SectionHeader
import io.github.herfar.barodial.ui.components.TimeDisplay
import io.github.herfar.barodial.ui.components.phoneTypeLabel
import io.github.herfar.barodial.ui.icons.AppIcons
import io.github.herfar.barodial.ui.theme.LocalCallColors
import kotlinx.coroutines.launch

class DetailActions(
    val navigate: () -> Unit,
    val call: (Contact, PhoneNumber?) -> Unit,
    val sms: (String) -> Unit,
    val toggleFavorite: (Contact) -> Unit,
    val edit: (Contact) -> Unit,
    val viewInContacts: (Contact) -> Unit,
    val setPreferred: (Contact, PhoneNumber?) -> Unit,
    val numberCopied: () -> Unit,
    val playRecording: (CallRecording, String) -> Unit,
)

@Composable
fun ContactDetailScreen(
    contact: Contact?,
    isFavorite: Boolean,
    preferredDigits: String?,
    calls: List<CallLogEntry>?,
    navigationIcon: ImageVector,
    actions: DetailActions,
    modifier: Modifier = Modifier,
    recordings: List<CallRecording> = emptyList(),
    recordingByCallId: Map<Long, CallRecording> = emptyMap(),
    applyStatusBarInsets: Boolean = true,
) {
    Surface(modifier = modifier.fillMaxSize().testTag("contactDetail"), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            AppTopBar(
                title = "",
                navigationIcon = navigationIcon,
                navigationDescription = stringResource(R.string.action_back),
                onNavigate = actions.navigate,
                applyStatusBarInsets = applyStatusBarInsets,
            ) {
                if (contact != null) {
                    IconButton(onClick = { actions.toggleFavorite(contact) }, modifier = Modifier.testTag("detailFavorite")) {
                        Icon(
                            if (isFavorite) AppIcons.Star else AppIcons.StarBorder,
                            contentDescription = stringResource(if (isFavorite) R.string.action_unfavorite else R.string.action_favorite),
                            tint = if (isFavorite) LocalCallColors.current.favorite else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { actions.edit(contact) }) {
                        Icon(AppIcons.Edit, contentDescription = stringResource(R.string.action_edit_in_contacts))
                    }
                }
            }
            if (contact == null) {
                EmptyMessage(stringResource(R.string.detail_not_found), icon = AppIcons.Person)
                return@Column
            }
            DetailBody(contact, isFavorite, preferredDigits, calls, recordings, recordingByCallId, actions)
        }
    }
}

@Composable
private fun DetailBody(
    contact: Contact,
    isFavorite: Boolean,
    preferredDigits: String?,
    calls: List<CallLogEntry>?,
    recordings: List<CallRecording>,
    recordingByCallId: Map<Long, CallRecording>,
    actions: DetailActions,
) {
    val shownCalls = calls?.take(MAX_CALLS_IN_DETAIL)
    // 통화내역 줄에 ▶ 로 이미 보이는 녹음은 아래 '통화 녹음' 목록에서 뺀다
    val attachedIds = shownCalls?.mapNotNullTo(HashSet()) { recordingByCallId[it.id]?.id }.orEmpty()
    val otherRecordings = recordings.filter { it.id !in attachedIds }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item("header") {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ContactAvatar(contact.displayName, contact.photoUri ?: contact.photoThumbnailUri, 104.dp)
                Spacer(Modifier.height(14.dp))
                Text(
                    text = contact.displayName,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("detailName"),
                )
                val sub = listOfNotNull(contact.organization, contact.jobTitle).joinToString(" · ")
                if (sub.isNotEmpty()) {
                    Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
        }
        item("actions") {
            Row(
                modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val callColors = LocalCallColors.current
                QuickAction(
                    icon = AppIcons.Call,
                    label = stringResource(R.string.action_call),
                    onClick = { actions.call(contact, null) },
                    containerColor = callColors.call,
                    contentColor = callColors.onCall,
                    modifier = Modifier.weight(1f).testTag("detailCall"),
                )
                QuickAction(
                    icon = AppIcons.Message,
                    label = stringResource(R.string.action_sms),
                    onClick = { (contact.primaryPhone ?: contact.phones.first()).let { actions.sms(it.number) } },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f),
                )
                QuickAction(
                    icon = if (isFavorite) AppIcons.Star else AppIcons.StarBorder,
                    label = stringResource(if (isFavorite) R.string.detail_favorite_on else R.string.detail_favorite_off),
                    onClick = { actions.toggleFavorite(contact) },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = if (isFavorite) LocalCallColors.current.favorite else MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item("phones") {
            Card(
                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                contact.phones.forEachIndexed { i, phone ->
                    if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    PhoneRow(contact, phone, preferredDigits == phone.nationalDigits, contact.phones.size > 1, actions)
                }
            }
        }
        if (calls != null) {
            item("callsHeader") {
                val stats = CallLogGrouping.stats(calls)
                SectionHeader(
                    title = stringResource(
                        R.string.detail_call_history_title,
                        stats.totalCalls,
                        TimeDisplay.duration(stats.totalDurationSeconds),
                    ),
                    modifier = Modifier.widthIn(max = 640.dp),
                )
            }
            if (calls.isEmpty()) {
                item("callsEmpty") { EmptyMessage(stringResource(R.string.detail_call_history_empty)) }
            }
            items(shownCalls.orEmpty(), key = { "call_${it.id}" }) { call ->
                CallEntryRow(call, recordingByCallId[call.id], onPlay = { actions.playRecording(it, contact.displayName) })
            }
        }
        if (otherRecordings.isNotEmpty()) {
            item("recordingsHeader") {
                SectionHeader(
                    stringResource(
                        if (calls == null) R.string.detail_recordings_title else R.string.detail_other_recordings_title,
                        otherRecordings.size,
                    ),
                    modifier = Modifier.widthIn(max = 640.dp),
                )
            }
            items(otherRecordings.take(50), key = { "rec_${it.id}" }) { rec ->
                RecordingRow(
                    title = TimeDisplay.dateTime(rec.startMillis),
                    subtitle = stringResource(R.string.recording_length, TimeDisplay.duration(rec.durationMillis / 1000)),
                    recording = rec,
                    onPlay = { actions.playRecording(rec, contact.displayName) },
                    modifier = Modifier.widthIn(max = 640.dp),
                )
            }
        }
        item("system") {
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = { actions.edit(contact) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(AppIcons.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_edit_in_contacts))
                }
                OutlinedButton(onClick = { actions.viewInContacts(contact) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(AppIcons.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_view_in_contacts))
                }
                Text(
                    text = stringResource(R.string.detail_favorite_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item("bottomInset") { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
    }
}

/** 상세 화면의 큰 동작 버튼 (아이콘 위, 글자 아래: 좁은 화면에서도 글자가 잘리지 않음) */
@Composable
internal fun QuickAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier.heightIn(min = 72.dp),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(26.dp))
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (containerColor == LocalCallColors.current.call) contentColor else MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PhoneRow(contact: Contact, phone: PhoneNumber, isPreferred: Boolean, multiple: Boolean, actions: DetailActions) {
    var menuOpen by remember { mutableStateOf(false) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val formatted = PhoneDisplay.format(phone)
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = { actions.call(contact, phone) }, onLongClick = { menuOpen = true })
                .heightIn(min = 72.dp)
                .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
                .testTag("detailPhone_${phone.nationalDigits}"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(formatted, style = MaterialTheme.typography.titleMedium)
                val label = buildList {
                    add(phoneTypeLabel(phone))
                    if (phone.isDefault) add(stringResource(R.string.detail_default_number))
                    if (isPreferred) add(stringResource(R.string.detail_preferred_number))
                }.joinToString(" · ")
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { actions.sms(phone.number) }) {
                Icon(AppIcons.Message, contentDescription = stringResource(R.string.action_sms_target, formatted))
            }
            CallIconButton(onClick = { actions.call(contact, phone) }, contentDescription = stringResource(R.string.action_call_target, formatted))
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_copy_number)) },
                leadingIcon = { Icon(AppIcons.ContentCopy, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("phone", phone.number))) }
                    actions.numberCopied()
                },
            )
            if (multiple) {
                DropdownMenuItem(
                    text = {
                        Text(stringResource(if (isPreferred) R.string.detail_unset_preferred else R.string.detail_set_preferred))
                    },
                    leadingIcon = { Icon(AppIcons.Check, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        actions.setPreferred(contact, if (isPreferred) null else phone)
                    },
                )
            }
        }
    }
}

/** 통화내역 한 줄. 녹음이 연결되어 있으면 ▶ 버튼으로 바로 재생한다. */
@Composable
internal fun CallEntryRow(call: CallLogEntry, recording: CallRecording?, onPlay: (CallRecording) -> Unit) {
    val colors = LocalCallColors.current
    val (icon, tint, label) = when (call.type) {
        CallType.OUTGOING -> Triple(AppIcons.CallMade, colors.outgoing, R.string.call_type_outgoing)
        CallType.MISSED -> Triple(AppIcons.CallMissed, colors.missed, R.string.call_type_missed)
        CallType.REJECTED -> Triple(AppIcons.CallMissed, colors.missed, R.string.call_type_rejected)
        CallType.BLOCKED -> Triple(AppIcons.CallMissed, colors.missed, R.string.call_type_blocked)
        else -> Triple(AppIcons.CallReceived, colors.incoming, R.string.call_type_incoming)
    }
    Row(
        Modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(start = 24.dp, end = 8.dp, top = 2.dp, bottom = 2.dp)
            .testTag("detailCall_${call.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        val missed = call.type == CallType.MISSED || call.type == CallType.REJECTED || call.type == CallType.BLOCKED
        Text(
            text = TimeDisplay.dateTime(call.timestampMillis) + if (!missed) " · ${TimeDisplay.duration(call.durationSeconds)}" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (recording != null) {
            IconButton(onClick = { onPlay(recording) }, modifier = Modifier.testTag("detailPlay_${call.id}")) {
                Icon(
                    AppIcons.PlayArrow,
                    contentDescription = stringResource(R.string.recording_play),
                    tint = MaterialTheme.colorScheme.tertiary,
                )
            }
        } else {
            Spacer(Modifier.width(48.dp))
        }
    }
}

private const val MAX_CALLS_IN_DETAIL = 50
