package io.github.herfar.barodial.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import io.github.herfar.barodial.R
import io.github.herfar.barodial.data.calllog.CallType
import io.github.herfar.barodial.data.contacts.Contact
import io.github.herfar.barodial.data.contacts.PhoneNumber
import io.github.herfar.barodial.data.history.HistoryEntry
import io.github.herfar.barodial.data.search.MatchField
import io.github.herfar.barodial.data.search.SearchResult
import io.github.herfar.barodial.ui.SearchRow
import io.github.herfar.barodial.ui.SearchUiState
import io.github.herfar.barodial.ui.components.CallIconButton
import io.github.herfar.barodial.ui.components.CircleIcon
import io.github.herfar.barodial.ui.components.ContactAvatar
import io.github.herfar.barodial.ui.components.ContactsPermissionCard
import io.github.herfar.barodial.ui.components.EmptyMessage
import io.github.herfar.barodial.ui.components.ListRow
import io.github.herfar.barodial.ui.components.PhoneDisplay
import io.github.herfar.barodial.ui.components.TimeDisplay
import io.github.herfar.barodial.ui.components.asAnnotated
import io.github.herfar.barodial.ui.components.highlight
import io.github.herfar.barodial.ui.components.highlightStyle
import io.github.herfar.barodial.ui.components.phoneTypeLabel
import io.github.herfar.barodial.ui.icons.AppIcons
import kotlin.math.abs

/** 검색 결과에서 사용하는 동작 묶음 */
class SearchActions(
    val openContact: (Contact) -> Unit,
    val callContact: (Contact, PhoneNumber?) -> Unit,
    val callNumber: (String) -> Unit,
    val sendSms: (String) -> Unit,
    val addToContacts: (String) -> Unit,
    val removeHistory: (HistoryEntry) -> Unit,
    val clearHistory: () -> Unit,
    val requestContactsPermission: () -> Unit,
)

/**
 * 검색 결과 목록.
 * [nearSearchBar] 가 true 면 가장 관련 높은 결과가 맨 아래(검색창 바로 위)에 온다.
 * 한 손으로 쥐었을 때 엄지가 닿는 위치에 첫 번째 결과가 놓이도록 한 설계다.
 */
@Composable
fun SearchResults(
    state: SearchUiState,
    nearSearchBar: Boolean,
    actions: SearchActions,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val hideKeyboardOnScroll = remember(keyboard) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && abs(available.y) > 6f) keyboard?.hide()
                return Offset.Zero
            }
        }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(state.query) {
        if (listState.firstVisibleItemIndex > 0) listState.scrollToItem(0)
    }

    if (state.rows.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = if (nearSearchBar) Alignment.BottomCenter else Alignment.TopCenter) {
            EmptyMessage(
                text = if (state.query.isEmpty()) {
                    stringResource(R.string.search_empty_history)
                } else {
                    stringResource(R.string.search_no_result, state.query)
                },
                icon = AppIcons.Search,
                modifier = Modifier.testTag("searchEmpty"),
            )
        }
        return
    }

    LazyColumn(
        state = listState,
        reverseLayout = nearSearchBar,
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(hideKeyboardOnScroll)
            .testTag("searchResults"),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(state.rows, key = { it.key }) { row ->
            when (row) {
                is SearchRow.DialNumber -> DialNumberRow(row, actions)
                is SearchRow.ContactHit -> ContactHitRow(row.result, actions)
                is SearchRow.RecentContact -> RecentContactRow(row, actions)
                is SearchRow.RecentNumber -> RecentNumberRow(row, showRemove = state.query.isEmpty(), actions = actions)
                is SearchRow.CallLogNumber -> CallLogNumberRow(row, actions)
                SearchRow.ClearHistory -> Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.Center) {
                    TextButton(onClick = actions.clearHistory, modifier = Modifier.testTag("clearHistory")) {
                        Text(stringResource(R.string.search_clear_history))
                    }
                }
                SearchRow.ContactsPermissionHint -> ContactsPermissionCard(onAllow = actions.requestContactsPermission)
            }
        }
    }
}

@Composable
private fun DialNumberRow(row: SearchRow.DialNumber, actions: SearchActions) {
    val formatted = PhoneDisplay.format(row.number)
    ListRow(
        title = formatted.asAnnotated(),
        subtitle = (row.contact?.displayName ?: stringResource(R.string.search_dial_this_number)).asAnnotated(),
        onClick = { actions.callNumber(row.number) },
        leading = {
            val contact = row.contact
            if (contact != null) {
                ContactAvatar(contact.displayName, contact.photoThumbnailUri, 44.dp)
            } else {
                CircleIcon(AppIcons.Dialpad)
            }
        },
        trailing = {
            IconButton(onClick = { actions.sendSms(row.number) }) {
                Icon(AppIcons.Message, contentDescription = stringResource(R.string.action_sms))
            }
            if (row.contact == null) {
                IconButton(onClick = { actions.addToContacts(row.number) }) {
                    Icon(AppIcons.PersonAdd, contentDescription = stringResource(R.string.action_add_contact))
                }
            }
            CallIconButton(
                onClick = { actions.callNumber(row.number) },
                contentDescription = stringResource(R.string.action_call_target, formatted),
            )
        },
        modifier = Modifier.testTag("dialNumberRow"),
    )
}

@Composable
private fun ContactHitRow(result: SearchResult, actions: SearchActions) {
    val contact = result.contact
    val hl = highlightStyle()
    val subtitle: AnnotatedString = when (result.field) {
        MatchField.PHONE -> {
            val phone = result.matchedPhone
            if (phone == null) {
                AnnotatedString("")
            } else {
                val label = phoneTypeLabel(phone)
                buildAnnotatedString {
                    append(label)
                    append("  ")
                    append(PhoneDisplay.highlighted(PhoneDisplay.format(phone), result.phoneDigitRange, hl))
                }
            }
        }
        MatchField.ORGANIZATION, MatchField.NICKNAME ->
            highlight(result.matchedText.orEmpty(), result.matchedTextHighlight, hl)
        MatchField.NAME -> {
            val primary = contact.primaryPhone
            val parts = listOfNotNull(primary?.let { PhoneDisplay.format(it) }, contact.organization)
            parts.joinToString(" · ").asAnnotated()
        }
    }
    val title = highlight(contact.displayName, result.nameHighlight, hl)
    ListRow(
        title = title,
        subtitle = subtitle,
        onClick = { actions.openContact(contact) },
        leading = { ContactAvatar(contact.displayName, contact.photoThumbnailUri, 44.dp) },
        trailing = {
            CallIconButton(
                onClick = { actions.callContact(contact, result.matchedPhone) },
                contentDescription = stringResource(R.string.action_call_target, contact.displayName),
                modifier = Modifier.testTag("callResult_${contact.id}"),
            )
        },
        modifier = Modifier.testTag("contactResult_${contact.id}"),
    )
}

@Composable
private fun RecentContactRow(row: SearchRow.RecentContact, actions: SearchActions) {
    val contact = row.contact
    ListRow(
        title = contact.displayName.asAnnotated(),
        subtitle = listOfNotNull(
            contact.primaryPhone?.let { PhoneDisplay.format(it) },
            TimeDisplay.relative(row.entry.lastUsedMillis),
        ).joinToString(" · ").asAnnotated(),
        onClick = { actions.openContact(contact) },
        leading = { ContactAvatar(contact.displayName, contact.photoThumbnailUri, 44.dp) },
        trailing = {
            IconButton(onClick = { actions.removeHistory(row.entry) }) {
                Icon(AppIcons.Close, contentDescription = stringResource(R.string.search_remove_history), tint = MaterialTheme.colorScheme.outline)
            }
            CallIconButton(
                onClick = { actions.callContact(contact, null) },
                contentDescription = stringResource(R.string.action_call_target, contact.displayName),
            )
        },
    )
}

@Composable
private fun RecentNumberRow(row: SearchRow.RecentNumber, showRemove: Boolean, actions: SearchActions) {
    val formatted = PhoneDisplay.format(row.entry.key)
    val hl = highlightStyle()
    val contact = row.contact
    ListRow(
        title = if (contact != null) contact.displayName.asAnnotated() else PhoneDisplay.highlighted(formatted, row.digitRange, hl),
        subtitle = buildAnnotatedString {
            if (contact != null) {
                append(formatted)
                append(" · ")
            }
            append(stringResource(R.string.search_recent_number))
            append(" · ")
            append(TimeDisplay.relative(row.entry.lastUsedMillis))
        },
        onClick = { actions.callNumber(row.entry.key) },
        leading = { CircleIcon(AppIcons.History) },
        trailing = {
            if (showRemove) {
                IconButton(onClick = { actions.removeHistory(row.entry) }) {
                    Icon(AppIcons.Close, contentDescription = stringResource(R.string.search_remove_history), tint = MaterialTheme.colorScheme.outline)
                }
            }
            CallIconButton(
                onClick = { actions.callNumber(row.entry.key) },
                contentDescription = stringResource(R.string.action_call_target, formatted),
            )
        },
    )
}

@Composable
private fun CallLogNumberRow(row: SearchRow.CallLogNumber, actions: SearchActions) {
    val formatted = PhoneDisplay.format(row.entry.number, row.entry.nationalDigits)
    val icon = when (row.entry.type) {
        CallType.OUTGOING -> AppIcons.CallMade
        CallType.MISSED, CallType.REJECTED -> AppIcons.CallMissed
        else -> AppIcons.CallReceived
    }
    ListRow(
        title = PhoneDisplay.highlighted(formatted, row.digitRange, highlightStyle()),
        subtitle = (row.entry.cachedName?.let { "$it · " }.orEmpty() + stringResource(R.string.search_from_call_log) + " · " +
            TimeDisplay.relative(row.entry.timestampMillis)).asAnnotated(),
        onClick = { actions.callNumber(row.entry.number) },
        leading = { CircleIcon(icon) },
        trailing = {
            CallIconButton(
                onClick = { actions.callNumber(row.entry.number) },
                contentDescription = stringResource(R.string.action_call_target, formatted),
            )
        },
    )
}
