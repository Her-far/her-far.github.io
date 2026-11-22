package io.github.herfar.barodial.ui.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.herfar.barodial.R
import io.github.herfar.barodial.data.contacts.Contact
import io.github.herfar.barodial.data.contacts.ContactsState
import io.github.herfar.barodial.ui.FavoriteItem
import io.github.herfar.barodial.ui.FavoritesUiState
import io.github.herfar.barodial.ui.components.ContactAvatar
import io.github.herfar.barodial.ui.components.ContactsPermissionCard
import io.github.herfar.barodial.ui.components.EmptyMessage
import io.github.herfar.barodial.ui.components.ListRow
import io.github.herfar.barodial.ui.components.PhoneDisplay
import io.github.herfar.barodial.ui.components.SectionHeader
import io.github.herfar.barodial.ui.components.asAnnotated
import io.github.herfar.barodial.ui.components.phoneTypeLabel
import io.github.herfar.barodial.ui.icons.AppIcons
import io.github.herfar.barodial.ui.theme.LocalCallColors

class FavoritesActions(
    val call: (Contact) -> Unit,
    val open: (Contact) -> Unit,
    val toggleFavorite: (Contact) -> Unit,
    val move: (Contact, Int) -> Unit,
    val requestContactsPermission: () -> Unit,
)

@Composable
fun FavoritesScreen(
    state: FavoritesUiState,
    contactsState: ContactsState,
    editMode: Boolean,
    preferredDigits: (Contact) -> String?,
    actions: FavoritesActions,
    modifier: Modifier = Modifier,
) {
    when (contactsState) {
        ContactsState.PermissionRequired -> {
            Column(modifier.fillMaxSize()) { ContactsPermissionCard(onAllow = actions.requestContactsPermission) }
            return
        }
        ContactsState.Loading -> {
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return
        }
        else -> Unit
    }
    if (state.app.isEmpty() && state.system.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyMessage(stringResource(R.string.favorites_empty), icon = AppIcons.StarBorder)
        }
        return
    }
    if (editMode) {
        EditList(state, actions, modifier)
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 104.dp),
        modifier = modifier.fillMaxSize().testTag("favoritesGrid"),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "hint") {
            Text(
                text = stringResource(R.string.favorites_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        items(state.app, key = { "app_${it.contact.id}" }) { item ->
            FavoriteTile(item, preferredDigits(item.contact), actions)
        }
        if (state.system.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "systemHeader") {
                Column {
                    SectionHeader(stringResource(R.string.favorites_system_title))
                    Text(
                        text = stringResource(R.string.favorites_system_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }
            items(state.system, key = { "sys_${it.contact.id}" }) { item ->
                FavoriteTile(item, preferredDigits(item.contact), actions)
            }
        }
    }
}

@Composable
private fun FavoriteTile(item: FavoriteItem, preferredDigits: String?, actions: FavoritesActions) {
    val contact = item.contact
    val phone = preferredDigits?.let { contact.phoneByDigits(it) } ?: contact.primaryPhone
    Column(
        modifier = Modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(
                onClickLabel = stringResource(R.string.action_call),
                onLongClickLabel = stringResource(R.string.action_open_detail),
                onClick = { actions.call(contact) },
                onLongClick = { actions.open(contact) },
            )
            .padding(vertical = 12.dp, horizontal = 4.dp)
            .testTag("favoriteTile_${contact.id}"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            ContactAvatar(contact.displayName, contact.photoThumbnailUri, 68.dp)
            val callColors = LocalCallColors.current
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(callColors.call),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Call, contentDescription = null, tint = callColors.onCall, modifier = Modifier.size(14.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = contact.displayName,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (phone != null) {
            Text(
                text = phoneTypeLabel(phone),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EditList(state: FavoritesUiState, actions: FavoritesActions, modifier: Modifier) {
    LazyColumn(modifier.fillMaxSize().testTag("favoritesEditList"), contentPadding = PaddingValues(bottom = 12.dp)) {
        item("editHint") {
            Text(
                text = stringResource(R.string.favorites_edit_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        itemsIndexed(state.app, key = { _, it -> "edit_${it.contact.id}" }) { index, item ->
            val c = item.contact
            ListRow(
                title = c.displayName.asAnnotated(),
                subtitle = c.primaryPhone?.let { PhoneDisplay.format(it) }?.asAnnotated(),
                onClick = {},
                leading = { ContactAvatar(c.displayName, c.photoThumbnailUri, 40.dp) },
                trailing = {
                    IconButton(onClick = { actions.move(c, -1) }, enabled = index > 0) {
                        Icon(AppIcons.ArrowUpward, contentDescription = stringResource(R.string.favorites_move_up, c.displayName))
                    }
                    IconButton(onClick = { actions.move(c, 1) }, enabled = index < state.app.lastIndex) {
                        Icon(AppIcons.ArrowDownward, contentDescription = stringResource(R.string.favorites_move_down, c.displayName))
                    }
                    IconButton(onClick = { actions.toggleFavorite(c) }) {
                        Icon(AppIcons.Delete, contentDescription = stringResource(R.string.action_unfavorite), tint = MaterialTheme.colorScheme.error)
                    }
                },
            )
        }
        if (state.system.isNotEmpty()) {
            item("editSystemHeader") { SectionHeader(stringResource(R.string.favorites_system_title)) }
            itemsIndexed(state.system, key = { _, it -> "editsys_${it.contact.id}" }) { _, item ->
                val c = item.contact
                ListRow(
                    title = c.displayName.asAnnotated(),
                    subtitle = stringResource(R.string.favorites_system_item).asAnnotated(),
                    onClick = {},
                    leading = { ContactAvatar(c.displayName, c.photoThumbnailUri, 40.dp) },
                    trailing = {
                        IconButton(onClick = { actions.toggleFavorite(c) }) {
                            Icon(AppIcons.Star, contentDescription = stringResource(R.string.favorites_add_to_app, c.displayName), tint = LocalCallColors.current.favorite)
                        }
                    },
                )
            }
        }
    }
}
