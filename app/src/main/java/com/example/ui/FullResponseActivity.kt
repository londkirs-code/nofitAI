package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NotifAiTheme

class FullResponseActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val responseText = intent.getStringExtra(EXTRA_RESPONSE_TEXT).orEmpty()
        val modelName = intent.getStringExtra(EXTRA_MODEL_NAME).orEmpty()

        setContent {
            NotifAiTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    FullResponseScreen(
                        responseText = responseText.ifBlank { "אין תוכן זמין להצגה" },
                        modelName = modelName.ifBlank { "NotifAI" },
                        onClose = { finish() }
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_RESPONSE_TEXT = "extra_response_text"
        const val EXTRA_MODEL_NAME = "extra_model_name"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullResponseScreen(
    responseText: String,
    modelName: String,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var fontSizeMultiplier by remember { mutableFloatStateOf(1f) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "תשובה מלאה",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = modelName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("full_response_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "סגור"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (fontSizeMultiplier > 0.8f) fontSizeMultiplier -= 0.15f
                        },
                        modifier = Modifier.testTag("zoom_out_btn")
                    ) {
                        Icon(Icons.Default.ZoomOut, contentDescription = "הקטן טקסט")
                    }
                    IconButton(
                        onClick = {
                            if (fontSizeMultiplier < 1.6f) fontSizeMultiplier += 0.15f
                        },
                        modifier = Modifier.testTag("zoom_in_btn")
                    ) {
                        Icon(Icons.Default.ZoomIn, contentDescription = "הגדל טקסט")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("NotifAI Response", responseText)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "התשובה הועתקה ללוח בהצלחה! 📋", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("copy_full_response_btn"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("העתק הכל", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, responseText)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "שתף תשובה מ-NotifAI"))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("share_full_response_btn")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("שתף")
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = responseText,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = (16 * fontSizeMultiplier).sp,
                            lineHeight = (24 * fontSizeMultiplier).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
