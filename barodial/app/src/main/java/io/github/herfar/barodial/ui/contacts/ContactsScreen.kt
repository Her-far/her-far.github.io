package io.github.herfar.barodial.ui.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.herfar.barodial.R
import io.github.herfar.barodial.data.contacts.Contact
import io.github.herfar.barodial.data.contacts.ContactsState
import io.github.herfar.barodial.ui.components.CallIconButton
import io.github.herfar.barodial.ui.components.ContactAvatar
import io.github.herfar.barodial.ui.components.ContactsPermissionCard
import io.github.herfar.barodial.ui.components.EmptyMessage
import io.github.herfar.barodial.ui.components.InfoCard
import io.github.herfar.barodial.ui.components.ListRow
import io.github.herfar.barodial.ui.components.PhoneDisplay
import io.github.herfar.barodial.ui.components.asAnnotated
import io.github.herfar.barodial.ui.icons.AppIcons
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

@Composable
fun ContactsScreen(
    state: ContactsState,
    listState: LazyListState,
    selectedContactId: Long?,
    onOpen: (Contact) -> Unit,
    onCall: (Contact) -> Unit,
    onRequestPermission: () -> Unit,
    onCreateContact: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        ContactsState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        ContactsState.PermissionRequired -> Column(modifier.fillMaxSize()) { ContactsPermissionCard(onAllow = onRequestPermission) }
        is ContactsState.Error -> Column(modifier.fillMaxSize()) {
            InfoCard(stringResource(R.string.contacts_error_title), state.message, AppIcons.Info)
        }
        is ContactsState.Ready -> {
            if (state.contacts.isEmpty()) {
                Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    EmptyMessage(stringResource(R.string.contacts_empty), icon = AppIcons.People)
                    Button(onClick = onCreateContact) { Text(stringResource(R.string.action_new_contact)) }
                }
                return
            }
            val headerIndex = remember(state) {
                var index = 0
                state.sections.associate { section ->
                    val start = index
                    index += 1 + section.contacts.size
                    section.label to start
                }
            }
            val scope = rememberCoroutineScope()
            Box(modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().testTag("contactsList"),
                    contentPadding = PaddingValues(end = 28.dp, bottom = 12.dp),
                ) {
                    state.sections.forEach { section ->
                        stickyHeader(key = "header_${section.label}") { LetterHeader(section.label) }
                        items(section.contacts, key = { it.id }) { contact ->
                            val selected = contact.id == selectedContactId
                            ListRow(
                                title = contact.displayName.asAnnotated(),
                                subtitle = listOfNotNull(contact.primaryPhone?.let { PhoneDisplay.format(it) }, contact.organization)
                                    .joinToString(" · ").asAnnotated(),
                                onClick = { onOpen(contact) },
                                leading = { ContactAvatar(contact.displayName, contact.photoThumbnailUri, 44.dp) },
                                trailing = {
                                    CallIconButton(
                                        onClick = { onCall(contact) },
                                        contentDescription = stringResource(R.string.action_call_target, contact.displayName),
                                    )
                                },
                                modifier = Modifier
                                    .then(
                                        if (selected) {
                                            Modifier.background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f))
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .testTag("contactRow_${contact.id}"),
                            )
                        }
                    }
                }
                FastScroller(
                    labels = state.sections.map { it.label },
                    onLabelSelected = { label ->
                        headerIndex[label]?.let { scope.launch { listState.scrollToItem(it) } }
                    },
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun LetterHeader(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = 24.dp, top = 10.dp, bottom = 4.dp),
    )
}

/**
 * 오른쪽 가장자리 빠른 스크롤 색인 (ㄱ ㄴ ㄷ … A B C … #).
 * 누르거나 끌면 해당 초성 구간으로 바로 이동하고, 현재 글자를 크게 보여 준다.
 */
@Composable
fun FastScroller(
    labels: List<String>,
    onLabelSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (labels.size < 2) return
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    var heightPx by remember { mutableIntStateOf(0) }
    var active by remember { mutableStateOf<String?>(null) }
    var touchY by remember { mutableStateOf(0f) }
    val description = stringResource(R.string.contacts_fast_scroll)

    fun select(y: Float) {
        if (heightPx <= 0) return
        val index = ((y / heightPx) * labels.size).toInt().coerceIn(0, labels.lastIndex)
        val label = labels[index]
        touchY = y.coerceIn(0f, heightPx.toFloat())
        if (label != active) {
            active = label
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onLabelSelected(label)
        }
    }

    Box(modifier) {
        val slotDp = with(density) { if (labels.isNotEmpty() && heightPx > 0) (heightPx.toFloat() / labels.size).toDp() else 16.dp }
        val step = if (slotDp >= 14.dp) 1 else ceil(14f / slotDp.value).toInt().coerceAtLeast(2)
        Column(
            modifier = Modifier
                .width(28.dp)
                .fillMaxHeight()
                .onSizeChanged { heightPx = it.height }
                .semantics { contentDescription = description }
                .pointerInput(labels) {
                    // 세로로 끌거나 탭하면 초성 이동, 가로로 밀면 손을 떼어 탭 넘김(스와이프)에 양보한다.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val slop = viewConfiguration.touchSlop
                        var vertical = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                if (!vertical && !change.isConsumed) select(down.position.y) // 탭
                                break
                            }
                            if (!vertical) {
                                val dx = abs(change.position.x - down.position.x)
                                val dy = abs(change.position.y - down.position.y)
                                if (dx > slop && dx > dy) break // 가로 스와이프: 양보
                                if (change.isConsumed) break
                                if (dy > slop) vertical = true
                            }
                            if (vertical) {
                                select(change.position.y)
                                change.consume()
                            }
                        }
                        active = null
                    }
                }
                .testTag("fastScroller"),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            labels.forEachIndexed { i, label ->
                Text(
                    text = if (i % step == 0) label else "·",
                    fontSize = 11.sp,
                    lineHeight = 12.sp,
                    fontWeight = if (label == active) FontWeight.Bold else FontWeight.Medium,
                    color = if (label == active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        val current = active
        if (current != null) {
            val bubbleSize = 64.dp
            val bubblePx = with(density) { bubbleSize.toPx() }
            Surface(
                shape = RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50, topEndPercent = 50, bottomEndPercent = 8),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .offset { IntOffset(x = -(bubblePx + 12.dp.toPx()).roundToInt(), y = (touchY - bubblePx / 2).roundToInt()) }
                    .size(bubbleSize),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(current, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

