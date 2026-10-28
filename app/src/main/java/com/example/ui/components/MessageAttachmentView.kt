package com.example.ui.components

import android.media.MediaPlayer
import android.net.Uri
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.util.AppStrings
import com.example.util.MediaStorageManager
import java.io.File

@Composable
fun MessageAttachmentView(
    attachmentUri: String?,
    attachmentMimeType: String? = null,
    attachmentName: String? = null,
    isUserBubble: Boolean = false,
    isUser: Boolean = isUserBubble,
    modifier: Modifier = Modifier
) {
    if (attachmentUri.isNullOrBlank()) return

    val context = LocalContext.current
    val lowerUri = attachmentUri.lowercase()
    val isImage = attachmentMimeType?.startsWith("image/") == true ||
        lowerUri.endsWith(".jpg") || lowerUri.endsWith(".jpeg") ||
        lowerUri.endsWith(".png") || lowerUri.endsWith(".webp") || lowerUri.endsWith(".gif")
    val isAudio = attachmentMimeType?.startsWith("audio/") == true ||
        lowerUri.endsWith(".m4a") || lowerUri.endsWith(".aac") ||
        lowerUri.endsWith(".mp3") || lowerUri.endsWith(".wav") || lowerUri.endsWith(".ogg")
    val isVideo = attachmentMimeType?.startsWith("video/") == true ||
        lowerUri.endsWith(".mp4") || lowerUri.endsWith(".webm") || lowerUri.endsWith(".mkv")

    var isPlayingAudio by remember { mutableStateOf(false) }
    var isPlayingVideoInline by remember { mutableStateOf(false) }
    var showFullScreenImage by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(attachmentUri) {
        onDispose {
            try {
                mediaPlayer?.stop()
                mediaPlayer?.release()
            } catch (_: Exception) {}
            mediaPlayer = null
        }
    }

    val modelData: Any = remember(attachmentUri) {
        if (attachmentUri.startsWith("/")) {
            File(attachmentUri)
        } else {
            Uri.parse(attachmentUri)
        }
    }

    val triggerDownload = { defaultMime: String, defaultName: String ->
        val savedName = MediaStorageManager.saveFileToPublicDownloads(
            context = context,
            pathOrUriStr = attachmentUri,
            mimeType = attachmentMimeType ?: defaultMime,
            suggestedFileName = attachmentName ?: defaultName
        )
        if (savedName != null) {
            Toast.makeText(
                context,
                AppStrings.tr(
                    "הקובץ הורד לתיקיית ההורדות (Downloads/NotifAI): $savedName 📥",
                    "File downloaded to Downloads/NotifAI: $savedName 📥"
                ),
                Toast.LENGTH_LONG
            ).show()
        } else {
            Toast.makeText(
                context,
                AppStrings.tr("שגיאה בהורדת הקובץ", "Error downloading file"),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    if (showFullScreenImage && isImage) {
        AlertDialog(
            onDismissRequest = { showFullScreenImage = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .padding(8.dp),
            shape = RoundedCornerShape(24.dp),
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = attachmentName ?: AppStrings.tr("תצוגת תמונה מלאה", "Full Image View"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { showFullScreenImage = false }) {
                        Icon(Icons.Default.Close, contentDescription = AppStrings.tr("סגור", "Close"))
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AsyncImage(
                        model = modelData,
                        contentDescription = attachmentName ?: AppStrings.tr("תמונה", "Image"),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp, max = 480.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                }
            },
            confirmButton = {
                FilledTonalButton(
                    onClick = { triggerDownload("image/png", "notifai_image.png") }
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(AppStrings.tr("הורד למכשיר", "Download to Device"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showFullScreenImage = false }) {
                    Text(AppStrings.tr("סגור", "Close"))
                }
            }
        )
    }

    Column(modifier = modifier.padding(bottom = 8.dp)) {
        when {
            isImage -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 240.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            width = 1.dp,
                            color = if (isUserBubble) Color.White.copy(alpha = 0.3f)
                            else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { showFullScreenImage = true }
                ) {
                    AsyncImage(
                        model = modelData,
                        contentDescription = attachmentName ?: AppStrings.tr("תמונה מצורפת", "Attached image"),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.68f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable {
                                triggerDownload("image/png", "notifai_image.png")
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = AppStrings.tr("הורד תמונה", "Download image"),
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = AppStrings.tr("הורד", "Download"),
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Surface(
                            color = Color.Black.copy(alpha = 0.68f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable { showFullScreenImage = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OpenInNew,
                                    contentDescription = AppStrings.tr("הגדל תמונה", "Enlarge image"),
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = AppStrings.tr("הגדל תמונה", "Enlarge"),
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            isVideo -> {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isUserBubble) Color.Black.copy(alpha = 0.22f)
                    else MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isPlayingVideoInline) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.Black)
                            ) {
                                AndroidView(
                                    factory = { ctx ->
                                        VideoView(ctx).apply {
                                            val mediaController = MediaController(ctx)
                                            mediaController.setAnchorView(this)
                                            setMediaController(mediaController)
                                            if (attachmentUri.startsWith("/")) {
                                                setVideoPath(attachmentUri)
                                            } else {
                                                setVideoURI(Uri.parse(attachmentUri))
                                            }
                                            setOnPreparedListener { mp ->
                                                mp.isLooping = false
                                                start()
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                IconButton(
                                    onClick = { isPlayingVideoInline = !isPlayingVideoInline },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                                ) {
                                    Icon(
                                        imageVector = if (isPlayingVideoInline) Icons.Default.Stop else Icons.Default.PlayArrow,
                                        contentDescription = AppStrings.tr("נגן וידאו", "Play video"),
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = attachmentName ?: AppStrings.tr("🎬 סרטון וידאו", "🎬 Video Clip"),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (isPlayingVideoInline) {
                                            AppStrings.tr("מנגן סרטון כעת...", "Playing video now...")
                                        } else {
                                            AppStrings.tr("לחץ לניגון וידאו או להורדה", "Tap to play video or download")
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            IconButton(
                                onClick = { triggerDownload("video/mp4", "notifai_video.mp4") }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = AppStrings.tr("הורד וידאו", "Download video"),
                                    tint = if (isUserBubble) Color.White else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            isAudio -> {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isUserBubble) Color.Black.copy(alpha = 0.22f)
                    else MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            IconButton(
                                onClick = {
                                    if (isPlayingAudio) {
                                        try {
                                            mediaPlayer?.stop()
                                            mediaPlayer?.release()
                                        } catch (_: Exception) {}
                                        mediaPlayer = null
                                        isPlayingAudio = false
                                    } else {
                                        try {
                                            val player = MediaPlayer()
                                            if (attachmentUri.startsWith("/")) {
                                                player.setDataSource(attachmentUri)
                                            } else {
                                                player.setDataSource(context, Uri.parse(attachmentUri))
                                            }
                                            player.setOnCompletionListener {
                                                isPlayingAudio = false
                                                it.release()
                                                mediaPlayer = null
                                            }
                                            player.prepare()
                                            player.start()
                                            mediaPlayer = player
                                            isPlayingAudio = true
                                        } catch (_: Exception) {
                                            isPlayingAudio = false
                                            Toast.makeText(
                                                context,
                                                AppStrings.tr("לא ניתן לנגן את קובץ השמע", "Cannot play audio file"),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        if (isUserBubble) Color.White.copy(alpha = 0.2f)
                                        else MaterialTheme.colorScheme.primaryContainer,
                                        CircleShape
                                    )
                            ) {
                                Icon(
                                    imageVector = if (isPlayingAudio) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlayingAudio) AppStrings.tr("עצור", "Stop") else AppStrings.tr("נגן שמע", "Play audio"),
                                    tint = if (isUserBubble) Color.White else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column {
                                Text(
                                    text = attachmentName ?: AppStrings.tr("קובץ שמע / הקלטה קולית", "Audio / Voice Recording"),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isUserBubble) Color.White else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (isPlayingAudio) {
                                        AppStrings.tr("מנגן שמע כעת... 🔊", "Playing audio now... 🔊")
                                    } else {
                                        AppStrings.tr("לחץ לניגון או להורדה למכשיר", "Tap to play or download to device")
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isUserBubble) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { triggerDownload("audio/wav", "notifai_audio.wav") },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = AppStrings.tr("הורד קובץ שמע", "Download audio"),
                                    tint = if (isUserBubble) Color.White else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.AudioFile,
                                contentDescription = null,
                                tint = if (isUserBubble) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            else -> {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isUserBubble) Color.Black.copy(alpha = 0.22f)
                    else MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val intent = MediaStorageManager.createViewMediaIntent(context, attachmentUri, attachmentMimeType)
                            if (intent != null) {
                                try {
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    Toast.makeText(
                                        context,
                                        AppStrings.tr("לא נמצאה אפליקציה לפתיחת הקובץ", "No app found to open file"),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.InsertDriveFile,
                                contentDescription = null,
                                tint = if (isUserBubble) Color.White else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = attachmentName ?: AppStrings.tr("קובץ מצורף", "Attached File"),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isUserBubble) Color.White else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = AppStrings.tr("לחץ לפתיחה או להורדת הקובץ", "Tap to open or download file"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isUserBubble) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { triggerDownload("application/octet-stream", "notifai_file") },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = AppStrings.tr("הורד קובץ", "Download file"),
                                    tint = if (isUserBubble) Color.White else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = null,
                                tint = if (isUserBubble) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
