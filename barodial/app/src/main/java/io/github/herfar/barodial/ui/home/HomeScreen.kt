package io.github.herfar.barodial.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import io.github.herfar.barodial.ui.DialerUiState
import io.github.herfar.barodial.ui.FavoriteItem
import io.github.herfar.barodial.ui.HomeUiState
import io.github.herfar.barodial.ui.components.CallIconButton
import io.github.herfar.barodial.ui.components.ContactAvatar
import io.github.herfar.barodial.ui.components.ContactsPermissionCard
import io.github.herfar.barodial.ui.components.InfoCard
import io.github.herfar.barodial.ui.components.ListRow
import io.github.herfar.barodial.ui.components.PhoneDisplay
import io.github.herfar.barodial.ui.components.SectionHeader
import io.github.herfar.barodial.ui.components.asAnnotated
import io.github.herfar.barodial.ui.dialer.DialerActions
import io.github.herfar.barodial.ui.dialer.DialerPane
import io.github.herfar.barodial.ui.dialer.dialKeyHeightFor
import io.github.herfar.barodial.ui.icons.AppIcons
import io.github.herfar.barodial.ui.theme.LocalCallColors

class HomeActions(
    val requestContactsPermission: () -> Unit,
    val callContact: (Contact) -> Unit,
    val openContact: (Contact) -> Unit,
    val showFavorites: () -> Unit,
)

private val FavoritesStripHeight = 124.dp

/**
 * 접힌 화면의 홈: 위쪽 즐겨찾기 줄 + 아래쪽 다이얼(키패드).
 * 번호를 누르기 시작하면 즐겨찾기 자리에 일치하는 연락처가 나타난다.
 * 키 높이는 즐겨찾기 표시 여부와 무관하게 고정해 키패드가 흔들리지 않게 한다.
 */
@Composable
fun HomeDialer(
    home: HomeUiState,
    contactsState: ContactsState,
    dialer: DialerUiState,
    haptics: Boolean,
    homeActions: HomeActions,
    dialerActions: DialerActions,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize().testTag("homeScreen")) {
        val keyHeight = dialKeyHeightFor(maxHeight, FavoritesStripHeight + 72.dp)
        DialerPane(
            state = dialer,
            haptics = haptics,
            actions = dialerActions,
            keyHeight = keyHeight,
            applyBottomInsets = false,
            emptyContent = {
                Column(Modifier.fillMaxSize()) {
                    if (contactsState == ContactsState.PermissionRequired) {
                        ContactsPermissionBanner(onAllow = homeActions.requestContactsPermission)
                    }
                    if (home.favorites.isNotEmpty()) {
                        FavoritesStrip(home.favorites, homeActions)
                    } else if (contactsState is ContactsState.Ready) {
                        Text(
                            text = stringResource(R.string.home_dial_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun FavoritesStrip(favorites: List<FavoriteItem>, actions: HomeActions) {
    Column(Modifier.fillMaxWidth().testTag("homeFavorites")) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.home_favorites),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = actions.showFavorites) { Text(stringResource(R.string.home_see_all)) }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(favorites, key = { it.contact.id }) { item ->
                FavoriteBubble(item, onCall = { actions.callContact(item.contact) }, onOpen = { actions.openContact(item.contact) })
            }
        }
    }
}

/** 키패드 위 좁은 공간용 연락처 권한 안내 (한 줄) */
@Composable
private fun ContactsPermissionBanner(onAllow: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("contactsPermissionBanner"),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(AppIcons.Lock, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(R.string.permission_contacts_banner),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onAllow, modifier = Modifier.testTag("allowContactsBannerButton")) {
                Text(stringResource(R.string.permission_allow_short))
            }
        }
    }
}

/**
 * 펼친 화면의 홈 (오른쪽 창에 키패드가 있으므로 여기에는 즐겨찾기와 자주 거는 연락처).
 */
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
                InfoCard(title = stringResource(R.string.contacts_error_title), body = contactsState.message, icon = AppIcons.Info)
            }
            is ContactsState.Ready -> Unit
        }
        if (state.favorites.isNotEmpty()) {
            item("favStrip") { FavoritesStrip(state.favorites, actions) }
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
        if (contactsState is ContactsState.Ready && state.favorites.isEmpty() && state.frequent.isEmpty()) {
            item("hint") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(AppIcons.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
                    Text(stringResource(R.string.home_hint_title), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    Text(
                        stringResource(R.string.home_hint_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
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
            .width(76.dp)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onCall, onLongClick = onOpen)
            .semantics {
                contentDescription = callLabel
                onLongClick(label = openLabel) {
                    onOpen()
                    true
                }
            }
            .padding(vertical = 6.dp)
            .testTag("favoriteBubble_${contact.id}"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            ContactAvatar(contact.displayName, contact.photoThumbnailUri, 56.dp)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(LocalCallColors.current.call),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Call, contentDescription = null, tint = LocalCallColors.current.onCall, modifier = Modifier.size(12.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = contact.displayName,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
