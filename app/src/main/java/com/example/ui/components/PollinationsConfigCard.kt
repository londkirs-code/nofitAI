package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ProviderType
import com.example.util.AppStrings
import com.example.util.PollinationsAspectRatio
import com.example.util.PollinationsImageManager

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PollinationsConfigCard(
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    hasActiveGeminiKeyInRepo: Boolean = false,
    onModelChanged: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    var selectedRatio by remember { mutableStateOf(PollinationsImageManager.getAspectRatio(context)) }
    var selectedCount by remember { mutableIntStateOf(PollinationsImageManager.getImageCount(context)) }
    var selectedModel by remember { mutableStateOf(PollinationsImageManager.getSelectedModel(context)) }
    var rememberGoogleKey by remember { mutableStateOf(PollinationsImageManager.isRememberGoogleKeyEnabled(context)) }
    var googleKeyInput by remember {
        mutableStateOf(PollinationsImageManager.getGoogleTranslationKey(context).orEmpty())
    }

    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = AppStrings.tr(
                                "🎨 מחולל תמונות Pollinations מובנה (ללא צורך במפתח)",
                                "🎨 Built-in Pollinations Image Generator (No Key Needed)"
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = AppStrings.tr(
                                "יחס: ${selectedRatio.label} • כמות: $selectedCount תמונות • מודל: $selectedModel • הגנת צניעות וסינון מובנים",
                                "Ratio: ${selectedRatio.label} • Count: $selectedCount • Model: $selectedModel • Built-in Modesty & Filter"
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = AppStrings.tr("פתח/סגור הגדרות תמונה", "Toggle image settings"),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // Quick bar always visible for fast Aspect Ratio & Quantity switching
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = AppStrings.tr("יחס תמונה:", "Ratio:"),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                PollinationsAspectRatio.entries.forEach { ratio ->
                    val isSelected = selectedRatio == ratio
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedRatio = ratio
                            PollinationsImageManager.setAspectRatio(context, ratio)
                        },
                        label = {
                            Text(
                                text = ratio.label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = AppStrings.tr("כמות תמונות (1–4):", "Count (1–4):"),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                (1..4).forEach { count ->
                    val isSelected = selectedCount == count
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedCount = count
                            PollinationsImageManager.setImageCount(context, count)
                        },
                        label = {
                            Text(
                                text = if (count == 1) AppStrings.tr("תמונה 1", "1 Image")
                                else AppStrings.tr("$count תמונות", "$count Images"),
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondary,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondary
                        )
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider()

                    // Model selection for Pollinations
                    Text(
                        text = AppStrings.tr("בחר מנוע יצירת תמונה (Pollinations):", "Select Pollinations Image Engine:"),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ProviderType.POLLINATIONS.suggestedModels.forEach { modelName ->
                            val isSelected = selectedModel == modelName
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedModel = modelName
                                    PollinationsImageManager.setSelectedModel(context, modelName)
                                    onModelChanged?.invoke(modelName)
                                },
                                label = {
                                    Text(
                                        text = if (modelName == "flux") "flux (ברירת מחדל ⭐)" else modelName,
                                        fontSize = 11.sp
                                    )
                                },
                                leadingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                } else null
                            )
                        }
                    }

                    HorizontalDivider()

                    // Automatic Translation + Privacy ("זכור") section
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Translate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = AppStrings.tr(
                                "תרגום אוטומטי לעברית -> אנגלית ופרטיות מפתח:",
                                "Auto Hebrew -> English Translation & Key Privacy:"
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = AppStrings.tr(
                            "התיאור בעברית מתורגם אוטומטית לאנגלית לפני היצירה לתוצאות טובות יותר (פועל כשמוזן מפתח Google). פרטיות: המפתח נשמר במכשיר רק אם מסמנים \"זכור\", ונשלח ישירות לגוגל.",
                            "Hebrew prompts are automatically translated to English before generation when a Google key is provided. Privacy: The key is saved on device ONLY if 'Remember' is checked, and sent directly to Google."
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (hasActiveGeminiKeyInRepo && googleKeyInput.isBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = AppStrings.tr(
                                    "✓ זוהה מפתח Google Gemini פעיל באפליקציה — תרגום אוטומטי של התיאור מעברית לאנגלית פועל אוטומטית!",
                                    "✓ Active Google Gemini key detected — automatic Hebrew-to-English prompt translation is active!"
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = googleKeyInput,
                        onValueChange = { newVal ->
                            googleKeyInput = newVal
                            PollinationsImageManager.updateGoogleTranslationKey(
                                context = context,
                                rawKey = newVal,
                                rememberOnDevice = rememberGoogleKey
                            )
                        },
                        label = {
                            Text(
                                AppStrings.tr(
                                    "מפתח Google לתרגום אוטומטי (אופציונלי - AIza...)",
                                    "Optional Google Key for Auto-Translation (AIza...)"
                                ),
                                fontSize = 12.sp
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val nextRemember = !rememberGoogleKey
                                rememberGoogleKey = nextRemember
                                PollinationsImageManager.updateGoogleTranslationKey(
                                    context = context,
                                    rawKey = googleKeyInput,
                                    rememberOnDevice = nextRemember
                                )
                                Toast.makeText(
                                    context,
                                    if (nextRemember) {
                                        AppStrings.tr("המפתח יישמר במכשיר באופן מוצפן", "Key will be saved on device")
                                    } else {
                                        AppStrings.tr("מצב פרטיות: המפתח זמין לסשן הנוכחי בלבד ולא נשמר במכשיר", "Privacy mode: Key kept in RAM only and not saved on device")
                                    },
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = rememberGoogleKey,
                            onCheckedChange = { checked ->
                                rememberGoogleKey = checked
                                PollinationsImageManager.updateGoogleTranslationKey(
                                    context = context,
                                    rawKey = googleKeyInput,
                                    rememberOnDevice = checked
                                )
                            }
                        )
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = AppStrings.tr(
                                "זכור מפתח במכשיר (אם לא מסומן, המפתח לא יישמר במכשיר ויישלח ישירות לגוגל בלבד)",
                                "Remember key on device (if unchecked, key is never stored on disk and sent directly to Google)"
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp
                        )
                    }

                    HorizontalDivider()

                    // Active Policy Summary Badges (Request Filtering + Modesty Rule)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = AppStrings.tr(
                                        "כללי יצירת תמונה מובנים (פעילים תמיד בכל בקשה):",
                                        "Built-in Image Generation Rules (Always Active):"
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = AppStrings.tr(
                                    "• הנחיית צניעות: כל בקשה נשלחת עם הוראה למנוע יצירת דמויות נשים ובנות. מותרים גברים, בנים וילדים קטנים עד גיל 6, ובמקרה אחר סצנה ללא אנשים.\n" +
                                        "• סינון בקשות: ביצירת תמונה האפליקציה מסרבת בנימוס לשאלות כלליות שאינן תיאור תמונה.\n" +
                                        "• גלריה: לחיצה על כל תמונה מגדילה לתצוגה מלאה + כפתור הורדה לכל תמונה בנפרד.",
                                    "• Modesty Instruction: Prevents generating women and girls. Allowed: men, boys, and children up to age 6; otherwise prefers a scene without people.\n" +
                                        "• Request Filtering: Politely refuses general questions that are not image descriptions.\n" +
                                        "• Gallery: Tap any image to enlarge + individual download button for each image."
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
