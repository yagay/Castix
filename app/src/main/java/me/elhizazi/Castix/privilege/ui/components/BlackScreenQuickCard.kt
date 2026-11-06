package me.elhizazi.Castix.privilege.ui.components

import me.elhizazi.Castix.privilege.ui.AppStrings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness2
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.elhizazi.Castix.R
import me.elhizazi.Castix.display.DisplayMode
import me.elhizazi.Castix.ui.theme.CyberAmber
import me.elhizazi.Castix.ui.theme.CyberRed
import me.elhizazi.Castix.ui.theme.NeonGreen

@Composable
fun BlackScreenQuickCard(
    isBlackScreenActive: Boolean,
    hasOverlayPermission: Boolean,
    displayMode: DisplayMode,
    isServiceRunning: Boolean = false,
    isFloatingButtonEnabled: Boolean = false,
    onToggleBlackScreen: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val requiresOverlayPermission = displayMode != DisplayMode.SYSTEM_LOCK
    val missingOverlayPermission = requiresOverlayPermission && !hasOverlayPermission
    val secureLock = displayMode == DisplayMode.SYSTEM_LOCK

    val cardBorderColor = if (isBlackScreenActive) {
        CyberRed.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    }

    val statusLabel: String
    val statusColor: Color
    val statusBg: Color
    val statusBorder: Color

    when {
        missingOverlayPermission -> {
            statusLabel = AppStrings.text("permission_required_tag")
            statusColor = CyberAmber
            statusBg = CyberAmber.copy(alpha = 0.15f)
            statusBorder = CyberAmber.copy(alpha = 0.4f)
        }
        isBlackScreenActive -> {
            statusLabel = AppStrings.text("black_screen_status_engaged")
            statusColor = CyberRed
            statusBg = CyberRed.copy(alpha = 0.18f)
            statusBorder = CyberRed.copy(alpha = 0.5f)
        }
        isServiceRunning || isFloatingButtonEnabled -> {
            statusLabel = AppStrings.text("status_active")
            statusColor = NeonGreen
            statusBg = NeonGreen.copy(alpha = 0.12f)
            statusBorder = NeonGreen.copy(alpha = 0.35f)
        }
        else -> {
            statusLabel = AppStrings.text("status_inactive")
            statusColor = MaterialTheme.colorScheme.onSurfaceVariant
            statusBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            statusBorder = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("card_black_screen_quick"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, cardBorderColor)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Brightness2,
                            contentDescription = AppStrings.text("black_screen_title"),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (secureLock) AppStrings.text("screen_home_lock_title") else if (displayMode == DisplayMode.PHYSICAL_OFF)
                            AppStrings.text("screen_home_physical_title") else AppStrings.text("black_screen_title"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Text(
                        text = if (secureLock)
                            AppStrings.text("screen_home_lock_desc")
                            else if (displayMode == DisplayMode.PHYSICAL_OFF)
                            AppStrings.text("screen_home_physical_desc")
                            else AppStrings.text("black_screen_desc"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Status Badge
                Surface(
                    color = statusBg,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, statusBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(statusColor, CircleShape)
                        )
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    }
                }
            }

            // Power Saving Info Pill
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (secureLock) AppStrings.text("screen_home_keyguard") else AppStrings.text("save_power_badge"),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = NeonGreen
                    )
                    Text(
                        text = if (secureLock) AppStrings.text("screen_home_secure_sleep") else AppStrings.text("black_screen_playing_fallback"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Overlay Permission Warning Banner if needed
            if (missingOverlayPermission) {
                Surface(
                    color = CyberAmber.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CyberAmber.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = CyberAmber,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = AppStrings.text("overlay_perm_warning_desc"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onRequestOverlayPermission,
                            colors = ButtonDefaults.buttonColors(containerColor = CyberAmber),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text(
                                text = AppStrings.text("btn_grant_overlay"),
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // Main Action Button (Modern M3 styled with responsive colors)
            Button(
                onClick = onToggleBlackScreen,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("btn_toggle_black_screen"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isBlackScreenActive) CyberRed else MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = null,
                    tint = if (isBlackScreenActive) Color.White else MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (secureLock) AppStrings.text("screen_home_lock_action") else if (isBlackScreenActive) {
                            AppStrings.text("black_screen_action_off")
                        } else {
                            AppStrings.text("black_screen_action_on")
                        },
                    color = if (isBlackScreenActive) Color.White else MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            // Tip note
            Text(
                text = if (secureLock)
                    AppStrings.text("screen_home_lock_tip")
                    else if (displayMode == DisplayMode.PHYSICAL_OFF)
                    AppStrings.text("screen_home_physical_tip")
                    else AppStrings.text("black_screen_tip"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
    }
}

