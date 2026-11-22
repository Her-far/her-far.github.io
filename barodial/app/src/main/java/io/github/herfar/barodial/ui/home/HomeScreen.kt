package io.github.herfar.barodial.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.herfar.barodial.R
import io.github.herfar.barodial.data.contacts.Contact
import io.github.herfar.barodial.data.contacts.ContactsState
import io.github.herfar.barodial.data.history.HistoryEntry
import io.github.herfar.barodial.data.history.HistoryKind
import io.github.herfar.barodial.ui.FavoriteItem
import io.github.herfar.barodial.ui.HomeUiState
import io.github.herfar.barodial.ui.components.CallIconButton
import io.github.herfar.barodial.ui.components.CircleIcon
import io.github.herfar.barodial.ui.components.ContactAvatar
import io.github.herfar.barodial.ui.components.ContactsPermissionCard
import io.github.herfar.barodial.ui.components.InfoCard
import io.github.herfar.barodial.ui.components.ListRow
import io.github.herfar.barodial.ui.components.PhoneDisplay
import io.github.herfar.barodial.ui.components.SectionHeader
import io.github.herfar.barodial.ui.components.TimeDisplay
import io.github.herfar.barodial.ui.components.asAnnotated
import io.github.herfar.barodial.ui.icons.AppIcons
import io.github.herfar.barodial.ui.theme.LocalCallColors

class HomeActions(
    val requestContactsPermission: () -> Unit,
    val callContact: (Contact) -> Unit,
    val openContact: (Contact) -> Unit,
    val callNumber: (String) -> Unit,
    val showFavorites: () -> Unit,
    val removeRecent: (HistoryEntry) -> Unit,
    val clearRecent: () -> Unit,
)

@Composable
fun HomeScreen(
    state: HomeUiState,
    contactsState: ContactsState,
    actions: HomeActions,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("homeScreen"),
        contentPadding = PaddingValues(bottom = 12.dp),
    ) {
        when (contactsState) {
            ContactsState.PermissionRequired -> item("permission") { ContactsPermissionCard(onAllow = actions.requestContactsPermission) }
            ContactsState.Loading -> item("loading") {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            is ContactsState.Error -> item("error") {
                InfoCard(
                    title = stringResource(R.string.contacts_error_title),
                    body = contactsState.message,
                    icon = AppIcons.Info,
                )
            }
            is ContactsState.Ready -> Unit
        }

        if (state.favorites.isNotEmpty()) {
            item("favHeader") {
                SectionHeader(
                    title = stringResource(R.string.home_favorites),
                    actionLabel = stringResource(R.string.home_see_all),
                    onAction = actions.showFavorites,
                )
            }
            item("favRow") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(state.favorites, key = { it.contact.id }) { item ->
                        FavoriteBubble(item, onCall = { actions.callContact(item.contact) }, onOpen = { actions.openContact(item.contact) })
                    }
                }
            }
        }

        if (state.frequent.isNotEmpty()) {
            item("freqHeader") { SectionHeader(stringResource(R.string.home_frequent)) }
            items(state.frequent, key = { "freq_${it.id}" }) { c ->
                ListRow(
                    title = c.displayName.asAnnotated(),
                    subtitle = c.primaryPhone?.let { PhoneDisplay.format(it) }?.asAnnotated(),
                    onClick = { actions.openContact(c) },
                    leading = { ContactAvatar(c.displayName, c.photoThumbnailUri, 44.dp) },
                    trailing = {
                        CallIconButton(onClick = { actions.callContact(c) }, contentDescription = stringResource(R.string.action_call_target, c.displayName))
                    },
                )
            }
        }

        if (state.recent.isNotEmpty()) {
            item("recentHeader") {
                SectionHeader(
                    title = stringResource(R.string.home_recent),
                    actionLabel = stringResource(R.string.action_clear_all),
                    onAction = actions.clearRecent,
                )
            }
            items(state.recent, key = { "recent_${it.entry.kind}_${it.entry.key}" }) { item ->
                val contact = item.contact
                val number = if (item.entry.kind == HistoryKind.NUMBER) item.entry.key else null
                val title = contact?.displayName ?: PhoneDisplay.format(number.orEmpty())
                val subtitleParts = listOfNotNull(
                    if (contact != null) (number?.let { PhoneDisplay.format(it) } ?: contact.primaryPhone?.let { PhoneDisplay.format(it) }) else null,
                    TimeDisplay.relative(item.entry.lastUsedMillis),
                )
                ListRow(
                    title = title.asAnnotated(),
                    subtitle = subtitleParts.joinToString(" · ").asAnnotated(),
                    onClick = { if (contact != null) actions.openContact(contact) else actions.callNumber(number.orEmpty()) },
                    leading = {
                        if (contact != null) ContactAvatar(contact.displayName, contact.photoThumbnailUri, 44.dp) else CircleIcon(AppIcons.History)
                    },
                    trailing = {
                        IconButton(onClick = { actions.removeRecent(item.entry) }) {
                            Icon(AppIcons.Close, contentDescription = stringResource(R.string.search_remove_history), tint = MaterialTheme.colorScheme.outline)
                        }
                        CallIconButton(
                            onClick = { if (number != null) actions.callNumber(number) else contact?.let(actions.callContact) },
                            contentDescription = stringResource(R.string.action_call_target, title),
                        )
                    },
                )
            }
        }

        if (contactsState is ContactsState.Ready && state.favorites.isEmpty() && state.recent.isEmpty() && state.frequent.isEmpty()) {
            item("hint") { HomeHint() }
        }
    }
}

@Composable
private fun FavoriteBubble(item: FavoriteItem, onCall: () -> Unit, onOpen: () -> Unit) {
    val contact = item.contact
    val callLabel = stringResource(R.string.home_favorite_tap_to_call, contact.displayName)
    val openLabel = stringResource(R.string.action_open_detail)
    Column(
        modifier = Modifier
            .width(80.dp)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onCall, onLongClick = onOpen)
            .semantics {
                contentDescription = callLabel
                onLongClick(label = openLabel) { onOpen(); true }
            }
            .padding(vertical = 8.dp)
            .testTag("favoriteBubble_${contact.id}"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            ContactAvatar(contact.displayName, contact.photoThumbnailUri, 60.dp)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(LocalCallColors.current.call),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Call, contentDescription = null, tint = LocalCallColors.current.onCall, modifier = Modifier.size(13.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = contact.displayName,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun HomeHint() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(AppIcons.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
        Text(
            text = stringResource(R.string.home_hint_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.home_hint_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Icon(AppIcons.ArrowDownward, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
    }
}
