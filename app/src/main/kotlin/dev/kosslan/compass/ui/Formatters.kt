package dev.kosslan.compass.ui

import android.content.ClipData
import android.content.Context
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.ui.platform.ClipEntry

fun Context.formatBytes(count: ULong): String = Formatter.formatShortFileSize(this, count.toLong())

fun handshakeAge(seconds: ULong): CharSequence =
    DateUtils.getRelativeTimeSpanString(
        System.currentTimeMillis() - seconds.toLong() * DateUtils.SECOND_IN_MILLIS,
        System.currentTimeMillis(),
        DateUtils.SECOND_IN_MILLIS,
    )

fun hostOf(endpoint: String): String = endpoint.substringBeforeLast(':')

fun plainText(
    label: String,
    text: String,
): ClipEntry = ClipEntry(ClipData.newPlainText(label, text))
