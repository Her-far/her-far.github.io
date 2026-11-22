package io.github.herfar.barodial.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.core.net.toUri
import io.github.herfar.barodial.core.hangul.Hangul
import io.github.herfar.barodial.ui.icons.AppIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 연락처 사진 메모리 캐시. 사진은 Contacts Provider 에서 직접 읽으며 외부로 보내지 않는다. */
object ContactPhotoCache {
    private val cache = object : LruCache<String, ImageBitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    fun get(key: String): ImageBitmap? = cache.get(key)

    fun load(context: Context, uri: String, targetPx: Int): ImageBitmap? {
        val key = "$uri@$targetPx"
        cache.get(key)?.let { return it }
        val bitmap = try {
            decode(context, uri.toUri(), targetPx)
        } catch (e: Exception) {
            null
        } ?: return null
        return bitmap.asImageBitmap().also { cache.put(key, it) }
    }

    private fun decode(context: Context, uri: Uri, targetPx: Int): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetPx && bounds.outHeight / (sample * 2) >= targetPx) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }
}

private val LightAvatarColors = listOf(
    Color(0xFFD7E8FF) to Color(0xFF0B4A8B),
    Color(0xFFDDF3E4) to Color(0xFF146C2E),
    Color(0xFFFFE3D1) to Color(0xFF8A3B00),
    Color(0xFFF3DDF5) to Color(0xFF6E2A78),
    Color(0xFFFFDDE3) to Color(0xFF9C1F3D),
    Color(0xFFD4F1F2) to Color(0xFF0B6468),
    Color(0xFFFFF1C2) to Color(0xFF7A5900),
    Color(0xFFE4E2F8) to Color(0xFF3D3A8C),
)

private val DarkAvatarColors = listOf(
    Color(0xFF1F3A5C) to Color(0xFFBFD9FF),
    Color(0xFF1E4429) to Color(0xFFB7EBC6),
    Color(0xFF54300F) to Color(0xFFFFD2B3),
    Color(0xFF47294C) to Color(0xFFF1C8F5),
    Color(0xFF5A2130) to Color(0xFFFFC2CE),
    Color(0xFF16494B) to Color(0xFFB2EEF0),
    Color(0xFF4E3E0B) to Color(0xFFFFE494),
    Color(0xFF2E2C5E) to Color(0xFFD3D0FF),
)

/** 이니셜 원에 표시할 글자. 한글은 첫 음절, 영문은 첫 글자 대문자. 숫자·기호면 null (사람 아이콘) */
fun avatarInitial(name: String): String? {
    val c = name.firstOrNull { !it.isWhitespace() } ?: return null
    return when {
        Hangul.isSyllable(c) -> c.toString()
        c.isLetter() -> c.uppercaseChar().toString()
        else -> null
    }
}

@Composable
fun ContactAvatar(
    name: String,
    photoUri: String?,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val targetPx = with(density) { size.roundToPx() }
    val cacheKey = photoUri?.let { "$it@$targetPx" }
    val photo by produceState(initialValue = cacheKey?.let { ContactPhotoCache.get(it) }, cacheKey) {
        if (value == null && photoUri != null) {
            value = withContext(Dispatchers.IO) { ContactPhotoCache.load(context.applicationContext, photoUri, targetPx) }
        }
    }
    val image = photo
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(CircleShape),
        )
        return
    }
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val palette = if (dark) DarkAvatarColors else LightAvatarColors
    val (bg, fg) = palette[(name.hashCode() and Int.MAX_VALUE) % palette.size]
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        val initial = avatarInitial(name)
        if (initial != null) {
            Text(
                text = initial,
                color = fg,
                fontSize = with(density) { (size * 0.42f).toSp() },
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        } else {
            Icon(AppIcons.Person, contentDescription = null, tint = fg, modifier = Modifier.size(size * 0.58f))
        }
    }
}
