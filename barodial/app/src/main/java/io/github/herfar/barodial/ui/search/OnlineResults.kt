package io.github.herfar.barodial.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.herfar.barodial.R
import io.github.herfar.barodial.data.places.Place
import io.github.herfar.barodial.data.places.PlaceItem
import io.github.herfar.barodial.data.places.PlaceSearchError
import io.github.herfar.barodial.ui.components.CallIconButton
import io.github.herfar.barodial.ui.components.CircleIcon
import io.github.herfar.barodial.ui.components.highlight
import io.github.herfar.barodial.ui.components.highlightStyle
import io.github.herfar.barodial.ui.icons.AppIcons

/** 온라인 검색 영역의 동작 */
class OnlineActions(
    val callPlace: (Place) -> Unit,
    val openPlace: (Place) -> Unit,
    val retry: () -> Unit,
    val loadMore: () -> Unit,
    val setNearby: (Boolean) -> Unit,
    val requestLocation: () -> Unit,
    val openSettings: () -> Unit,
    val dismissHint: () -> Unit,
    val enterContactManually: () -> Unit,
)

/** 목록의 한 줄 (key + 그리는 함수) */
typealias ResultEntry = Pair<String, @Composable () -> Unit>

/**
 * 온라인 영역을 위에서 아래로 읽는 순서로 만든다:
 * 머리글(출처·가까운 순) → 위치 안내 → 결과/상태 → 더 보기 → 출처·정책 안내.
 */
fun onlineEntries(state: OnlineSearchUi, nearbySelected: Boolean, actions: OnlineActions): List<ResultEntry> {
    val out = ArrayList<ResultEntry>()
    fun header(withChip: Boolean) {
        out += "online:header" to { OnlineHeader(if (withChip) nearbySelected else null, actions) }
    }
    fun locationNote(nearby: NearbyState) {
        when (nearby) {
            NearbyState.PERMISSION_NEEDED -> out += "online:location" to {
                StatusRow(stringResource(R.string.online_location_needed), "onlineLocationNote", stringResource(R.string.online_allow_location) to actions.requestLocation)
            }
            NearbyState.LOCATION_UNAVAILABLE -> out += "online:location" to {
                StatusRow(stringResource(R.string.online_location_unavailable), "onlineLocationNote")
            }
            else -> Unit
        }
    }
    when (state) {
        OnlineSearchUi.Hidden -> Unit
        is OnlineSearchUi.Off -> out += "online:off" to {
            StatusRow(
                text = stringResource(R.string.online_off_hint),
                tag = "onlineOffHint",
                action = stringResource(R.string.online_turn_on) to actions.openSettings,
                onClose = actions.dismissHint,
            )
        }
        is OnlineSearchUi.NotConfigured -> {
            header(withChip = false)
            out += "online:notConfigured" to {
                StatusRow(stringResource(R.string.online_not_configured), "onlineNotConfigured", stringResource(R.string.online_setup) to actions.openSettings)
            }
        }
        is OnlineSearchUi.Loading -> {
            header(withChip = true)
            out += "online:loading" to { LoadingRow(stringResource(R.string.online_loading, state.keyword), "onlineLoading") }
        }
        is OnlineSearchUi.Failed -> {
            header(withChip = state.error !is PlaceSearchError.Offline)
            locationNote(state.nearby)
            out += "online:error" to { ErrorRow(state.error, actions) }
        }
        is OnlineSearchUi.Results -> {
            header(withChip = true)
            locationNote(state.nearby)
            if (state.items.isEmpty()) {
                out += "online:empty" to { StatusRow(stringResource(R.string.online_empty, state.keyword), "onlineEmpty") }
            }
            for (item in state.items) {
                out += "place:${item.place.id.ifEmpty { item.place.name + item.place.displayAddress }}" to {
                    PlaceRow(item, state.keyword, actions)
                }
            }
            when {
                state.loadingMore -> out += "online:more" to { LoadingRow(null, "onlineLoadingMore") }
                state.moreError != null -> out += "online:more" to { ErrorRow(state.moreError, actions, retry = actions.loadMore) }
                state.canLoadMore -> out += "online:more" to {
                    Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.Center) {
                        TextButton(onClick = actions.loadMore, modifier = Modifier.testTag("onlineLoadMore")) {
                            Text(stringResource(R.string.online_load_more))
                        }
                    }
                }
            }
            if (state.items.isNotEmpty()) out += "online:footer" to { OnlineFooter(actions) }
        }
    }
    return out
}

@Composable
private fun OnlineHeader(nearbySelected: Boolean?, actions: OnlineActions) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 20.dp, end = 12.dp, top = 8.dp).testTag("onlineHeader"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.online_section_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        if (nearbySelected != null) {
            FilterChip(
                selected = nearbySelected,
                onClick = { actions.setNearby(!nearbySelected) },
                label = { Text(stringResource(R.string.online_nearby)) },
                modifier = Modifier.testTag("onlineNearbyChip"),
            )
        }
    }
}

/** 내 연락처 영역 머리글 / '연락처에 없음' 줄 */
@Composable
fun LocalSectionHeader(text: String, tag: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 4.dp).testTag(tag),
    )
}

@Composable
private fun PlaceRow(item: PlaceItem, keyword: String, actions: OnlineActions) {
    val p = item.place
    val hl = highlightStyle()
    val start = p.name.indexOf(keyword, ignoreCase = true)
    val title = highlight(p.name, if (start >= 0) start until start + keyword.length else null, hl)
    val phoneText = if (p.hasPhone) p.phone else stringResource(R.string.online_no_phone)
    val line1 = listOf(phoneText, p.shortCategory).filter { it.isNotBlank() }.joinToString(" · ")
    val distance = p.distanceMeters?.let { m ->
        if (m < 1000) stringResource(R.string.online_distance_m, m) else stringResource(R.string.online_distance_km, m / 1000.0)
    }
    val line2 = listOfNotNull(p.displayAddress.ifBlank { null }, distance).joinToString(" · ")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { actions.openPlace(p) }
            .heightIn(min = 76.dp)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
            .testTag("place_${p.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIcon(AppIcons.Business)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 지점·지역회 이름("… 서울특별시회")이 잘리지 않도록 두 줄까지 보여 준다.
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                item.regionLabel?.let { region ->
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = region,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .testTag("placeRegion_${p.id}"),
                    )
                }
            }
            Text(
                text = line1,
                style = MaterialTheme.typography.bodyMedium,
                color = if (p.hasPhone) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("placePhone_${p.id}"),
            )
            if (line2.isNotEmpty()) {
                Text(
                    text = line2,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = { actions.openPlace(p) }, modifier = Modifier.testTag("placeMap_${p.id}")) {
            Icon(AppIcons.OpenInNew, contentDescription = stringResource(R.string.online_open_map, p.name), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        CallIconButton(
            onClick = { actions.callPlace(p) },
            enabled = p.hasPhone,
            contentDescription = if (p.hasPhone) {
                stringResource(R.string.online_call_target, p.name)
            } else {
                stringResource(R.string.online_no_phone_cd, p.name)
            },
            modifier = Modifier.testTag("placeCall_${p.id}"),
        )
    }
}

@Composable
private fun LoadingRow(text: String?, tag: String) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 20.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (text == null) Arrangement.Center else Arrangement.Start,
    ) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        if (text != null) {
            Spacer(Modifier.width(14.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatusRow(
    text: String,
    tag: String,
    action: Pair<String, () -> Unit>? = null,
    onClose: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        if (action != null) {
            TextButton(onClick = action.second, modifier = Modifier.testTag("${tag}Action")) { Text(action.first) }
        }
        if (onClose != null) {
            IconButton(onClick = onClose, modifier = Modifier.testTag("${tag}Close")) {
                Icon(AppIcons.Close, contentDescription = stringResource(R.string.action_close), tint = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

/** 온라인 검색 오류 안내 문구 (검색 결과와 설정의 [연결 확인]에서 함께 쓴다) */
@Composable
fun placeErrorText(error: PlaceSearchError): String = when (error) {
    PlaceSearchError.Offline -> stringResource(R.string.online_error_offline)
    PlaceSearchError.Network -> stringResource(R.string.online_error_network)
    is PlaceSearchError.InvalidCredentials ->
        stringResource(if (error.viaProxy) R.string.online_error_proxy_auth else R.string.online_error_key)
    is PlaceSearchError.Forbidden -> stringResource(R.string.online_error_forbidden)
    PlaceSearchError.QuotaExceeded -> stringResource(R.string.online_error_quota)
    is PlaceSearchError.Server -> stringResource(R.string.online_error_server, error.status)
}

@Composable
private fun ErrorRow(error: PlaceSearchError, actions: OnlineActions, retry: () -> Unit = actions.retry) {
    val action = when (error) {
        PlaceSearchError.Offline, PlaceSearchError.Network, is PlaceSearchError.Server -> stringResource(R.string.online_retry) to retry
        is PlaceSearchError.InvalidCredentials, is PlaceSearchError.Forbidden -> stringResource(R.string.online_settings) to actions.openSettings
        PlaceSearchError.QuotaExceeded -> null
    }
    StatusRow(placeErrorText(error), "onlineError", action)
}

@Composable
private fun OnlineFooter(actions: OnlineActions) {
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 8.dp).testTag("onlineFooter")) {
        Text(stringResource(R.string.online_footer), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.online_footer_contact),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = actions.enterContactManually, modifier = Modifier.testTag("onlineEnterContact")) {
                Text(stringResource(R.string.online_enter_contact))
            }
        }
    }
}
