package com.example.ui.station

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CompositionConfig
import com.example.model.CompositionElement
import com.example.model.CompositionElementType
import com.example.model.GameScaleMode
import com.example.model.GameVideoConfig
import com.example.ui.components.EsportsCard
import com.example.ui.components.EsportsSlider
import com.example.ui.components.EsportsToggleRow
import com.example.ui.theme.EsportsCyan
import com.example.ui.theme.EsportsGold
import com.example.ui.theme.EsportsGreen
import com.example.ui.theme.EsportsPurple
import com.example.ui.theme.EsportsRed
import com.example.ui.theme.EsportsSurface
import com.example.ui.theme.EsportsSurfaceBorder
import com.example.ui.theme.EsportsSurfaceVariant
import com.example.ui.theme.EsportsTextMuted
import com.example.ui.theme.EsportsTextPrimary
import com.example.ui.theme.EsportsTextSecondary

@Composable
fun VisualCompositionStudioCard(
    compositionConfig: CompositionConfig,
    onSelectLayer: (String) -> Unit,
    onSelectElement: (String?) -> Unit,
    onAddElement: (CompositionElementType, String?, Uri?, String?, String?) -> Unit,
    onRemoveElement: (String) -> Unit,
    onToggleVisibility: (String) -> Unit,
    onUpdatePosition: (String, Float, Float) -> Unit,
    onUpdateSize: (String, Float, Float) -> Unit,
    onUpdateCrop: (String, Float, Float, Float, Float) -> Unit,
    onUpdateScale: (String, Float) -> Unit,
    onUpdateRotation: (String, Float) -> Unit,
    onUpdateOpacity: (String, Float) -> Unit,
    onMoveLayerUp: (String) -> Unit,
    onMoveLayerDown: (String) -> Unit,
    onUpdateText: (String, String?, String?) -> Unit,
    onUpdateColors: (String, String, String) -> Unit,
    onUpdateVideoLoop: (String, Boolean) -> Unit,
    onSetContentUri: (String, Uri) -> Unit,
    // Game Video transformations
    onSetGameScaleMode: (GameScaleMode) -> Unit,
    onUpdateGamePosition: (Float, Float) -> Unit,
    onMoveGame: (Float, Float) -> Unit,
    onUpdateGameSize: (Float, Float) -> Unit,
    onUpdateGameScale: (Float) -> Unit,
    onUpdateGameCrop: (Float, Float, Float, Float) -> Unit,
    onUpdateGameRotation: (Float) -> Unit,
    onUpdateGameOpacity: (Float) -> Unit,
    onToggleGameVisibility: () -> Unit,
    onResetGameTransform: () -> Unit,
    onSetGameFitPreset: () -> Unit,
    onSetGameFillPreset: () -> Unit,
    onSetGameFullscreenPreset: () -> Unit,
    onSetGameBackgroundColor: (String) -> Unit,
    onToggleGridOverlay: () -> Unit,
    outputPreviewEnabled: Boolean = true,
    onToggleOutputPreview: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingAddType by remember { mutableStateOf<CompositionElementType?>(null) }
    var customElementName by remember { mutableStateOf("") }
    var customElementTitle by remember { mutableStateOf("") }
    var customElementSubtitle by remember { mutableStateOf("") }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null && pendingAddType != null) {
            onAddElement(
                pendingAddType!!,
                customElementName.ifBlank { null },
                uri,
                customElementTitle.ifBlank { null },
                customElementSubtitle.ifBlank { null }
            )
            pendingAddType = null
            customElementName = ""
        }
    }

    val replaceUriPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val selectedId = compositionConfig.selectedElementId
        if (uri != null && selectedId != null) {
            onSetContentUri(selectedId, uri)
        }
    }

    val isGameSelected = compositionConfig.isGameVideoSelected
    val selectedElement = compositionConfig.elements.find { it.id == compositionConfig.selectedElementId }

    // Dialog for adding elements
    if (showAddDialog && pendingAddType != null) {
        val type = pendingAddType!!
        AlertDialog(
            onDismissRequest = {
                showAddDialog = false
                pendingAddType = null
            },
            title = {
                Text(
                    text = "ADD ${type.label.uppercase()}",
                    color = EsportsTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Customize the layer details before placing it onto the output composition canvas.",
                        color = EsportsTextSecondary,
                        fontSize = 12.sp
                    )

                    OutlinedTextField(
                        value = customElementName,
                        onValueChange = { customElementName = it },
                        label = { Text("Layer Label / Name") },
                        placeholder = { Text(type.label) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EsportsCyan,
                            unfocusedBorderColor = EsportsSurfaceBorder,
                            focusedTextColor = EsportsTextPrimary,
                            unfocusedTextColor = EsportsTextPrimary
                        ),
                        singleLine = true
                    )

                    if (type == CompositionElementType.BANNER ||
                        type == CompositionElementType.BOTTOM_STRIP ||
                        type == CompositionElementType.MEME
                    ) {
                        OutlinedTextField(
                            value = customElementTitle,
                            onValueChange = { customElementTitle = it },
                            label = { Text("Title / Primary Text") },
                            placeholder = { Text("e.g. GRAND FINALS LIVE") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EsportsCyan,
                                unfocusedBorderColor = EsportsSurfaceBorder,
                                focusedTextColor = EsportsTextPrimary,
                                unfocusedTextColor = EsportsTextPrimary
                            ),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = customElementSubtitle,
                            onValueChange = { customElementSubtitle = it },
                            label = { Text("Subtitle / Secondary Marquee") },
                            placeholder = { Text("e.g. FOLLOW @MVP_ESPORTS") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EsportsCyan,
                                unfocusedBorderColor = EsportsSurfaceBorder,
                                focusedTextColor = EsportsTextPrimary,
                                unfocusedTextColor = EsportsTextPrimary
                            ),
                            singleLine = true
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAddDialog = false
                        when (type) {
                            CompositionElementType.PHOTO,
                            CompositionElementType.PNG -> {
                                photoPickerLauncher.launch("image/*")
                            }
                            CompositionElementType.VIDEO -> {
                                photoPickerLauncher.launch("video/*")
                            }
                            else -> {
                                onAddElement(
                                    type,
                                    customElementName.ifBlank { null },
                                    null,
                                    customElementTitle.ifBlank { null },
                                    customElementSubtitle.ifBlank { null }
                                )
                                pendingAddType = null
                                customElementName = ""
                                customElementTitle = ""
                                customElementSubtitle = ""
                            }
                        }
                    }
                ) {
                    Text(
                        text = if (type == CompositionElementType.PHOTO || type == CompositionElementType.PNG || type == CompositionElementType.VIDEO) "Select Media..." else "Add Layer",
                        color = EsportsCyan,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddDialog = false
                        pendingAddType = null
                    }
                ) {
                    Text("Cancel", color = EsportsTextMuted)
                }
            },
            containerColor = EsportsSurfaceVariant
        )
    }

    EsportsCard(
        headerColor = EsportsCyan,
        accentBorder = true,
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = "Visual Composition Studio",
                        tint = EsportsCyan,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "OUTPUT COMPOSITION ENGINE",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.8.sp
                            ),
                            color = EsportsTextPrimary
                        )
                        Text(
                            text = "GPU-Accelerated 16:9 Live Canvas Layout & Game Sizing",
                            style = MaterialTheme.typography.bodySmall,
                            color = EsportsTextSecondary
                        )
                    }
                }

                // UI Isolation Shield Badge
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = EsportsGreen.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, EsportsGreen.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "UI Isolated",
                            tint = EsportsGreen,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "ZERO UI LEAK",
                            color = EsportsGreen,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Surface(
                onClick = onToggleOutputPreview,
                shape = RoundedCornerShape(8.dp),
                color = if (outputPreviewEnabled) EsportsCyan.copy(alpha = 0.18f) else EsportsSurfaceVariant
            ) {
                Text(
                    text = if (outputPreviewEnabled) "PREVIEW ON — tap to free phone" else "PREVIEW OFF — tap to show layout",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    color = EsportsTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (outputPreviewEnabled) {
                OutputCompositionInteractivePreview(
                    compositionConfig = compositionConfig,
                    onSelectLayer = onSelectLayer,
                    onUpdateGamePosition = onUpdateGamePosition,
                    onUpdateElementPosition = onUpdatePosition,
                    onToggleGrid = onToggleGridOverlay
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // LAYER STACK TABS: GAME VIDEO vs OVERLAYS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Game Video Feed Tab
                Surface(
                    onClick = { onSelectLayer("GAME_VIDEO") },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isGameSelected) EsportsCyan.copy(alpha = 0.20f) else EsportsSurfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(
                        width = if (isGameSelected) 2.dp else 1.dp,
                        color = if (isGameSelected) EsportsCyan else EsportsSurfaceBorder
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SportsEsports,
                            contentDescription = "Game Video Layer",
                            tint = if (isGameSelected) EsportsCyan else EsportsTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "GAME FEED BASE",
                                color = if (isGameSelected) EsportsCyan else EsportsTextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "${compositionConfig.gameVideoConfig.scaleMode.label} • ${(compositionConfig.gameVideoConfig.scale * 100).toInt()}% Zoom",
                                color = EsportsTextMuted,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                // Overlays Stack Tab
                Surface(
                onClick = {
                        val firstOverlay = compositionConfig.elements.firstOrNull()?.id
                        if (firstOverlay != null) {
                            onSelectLayer(firstOverlay)
                        } else {
                            // Empty stack: still switch away from Game so + Photo / + Video show
                            onSelectLayer("OVERLAYS_STACK")
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = if (!isGameSelected) EsportsGold.copy(alpha = 0.20f) else EsportsSurfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(
                        width = if (!isGameSelected) 2.dp else 1.dp,
                        color = if (!isGameSelected) EsportsGold else EsportsSurfaceBorder
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = "Overlays Stack",
                            tint = if (!isGameSelected) EsportsGold else EsportsTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "OVERLAY LAYERS",
                                color = if (!isGameSelected) EsportsGold else EsportsTextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "${compositionConfig.elements.size} Active Elements",
                                color = EsportsTextMuted,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // CONDITIONAL CONTROLS BODY
            if (isGameSelected) {
                // GAME VIDEO POSITIONING & CROPPING INSPECTOR
                GameVideoControlsInspector(
                    gameConfig = compositionConfig.gameVideoConfig,
                    onSetScaleMode = onSetGameScaleMode,
                    onUpdatePosition = onUpdateGamePosition,
                    onMoveGame = onMoveGame,
                    onUpdateSize = onUpdateGameSize,
                    onUpdateScale = onUpdateGameScale,
                    onUpdateCrop = onUpdateGameCrop,
                    onUpdateRotation = onUpdateGameRotation,
                    onUpdateOpacity = onUpdateGameOpacity,
                    onToggleVisibility = onToggleGameVisibility,
                    onResetTransform = onResetGameTransform,
                    onSetFitPreset = onSetGameFitPreset,
                    onSetFillPreset = onSetGameFillPreset,
                    onSetFullscreenPreset = onSetGameFullscreenPreset,
                    onSetBackgroundColor = onSetGameBackgroundColor
                )
            } else {
                // OVERLAYS STACK & ELEMENT INSPECTOR
                Column {
                    // Quick Add Buttons Row
                    Text(
                        text = "ADD INTENTIONAL OUTPUT OVERLAYS",
                        color = EsportsTextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AddElementButton(
                            icon = Icons.Default.Image,
                            label = "+ Photo",
                            tint = EsportsCyan,
                            modifier = Modifier.weight(1f)
                        ) {
                            pendingAddType = CompositionElementType.PHOTO
                            showAddDialog = true
                        }
                        AddElementButton(
                            icon = Icons.AutoMirrored.Filled.Label,
                            label = "+ PNG Logo",
                            tint = EsportsGold,
                            modifier = Modifier.weight(1f)
                        ) {
                            pendingAddType = CompositionElementType.PNG
                            showAddDialog = true
                        }
                        AddElementButton(
                            icon = Icons.Default.Animation,
                            label = "+ Meme",
                            tint = EsportsGreen,
                            modifier = Modifier.weight(1f)
                        ) {
                            pendingAddType = CompositionElementType.MEME
                            showAddDialog = true
                        }
                        AddElementButton(
                            icon = Icons.Default.Movie,
                            label = "+ Video",
                            tint = EsportsPurple,
                            modifier = Modifier.weight(1f)
                        ) {
                            pendingAddType = CompositionElementType.VIDEO
                            showAddDialog = true
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AddElementButton(
                            icon = Icons.Default.TextFields,
                            label = "+ Top Banner",
                            tint = EsportsGold,
                            modifier = Modifier.weight(1f)
                        ) {
                            pendingAddType = CompositionElementType.BANNER
                            showAddDialog = true
                        }
                        AddElementButton(
                            icon = Icons.Default.Layers,
                            label = "+ Bottom Strip",
                            tint = EsportsCyan,
                            modifier = Modifier.weight(1f)
                        ) {
                            pendingAddType = CompositionElementType.BOTTOM_STRIP
                            showAddDialog = true
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Active Elements Layer Stack
                    Text(
                        text = "ACTIVE COMPOSITION LAYERS (${compositionConfig.elements.size})",
                        color = EsportsTextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        compositionConfig.elements.sortedByDescending { it.zIndex }.forEach { elem ->
                            CompositionLayerItem(
                                element = elem,
                                isSelected = elem.id == compositionConfig.selectedElementId,
                                onSelect = {
                                    onSelectLayer(elem.id)
                                    onSelectElement(elem.id)
                                },
                                onToggleVisibility = { onToggleVisibility(elem.id) },
                                onMoveUp = { onMoveLayerUp(elem.id) },
                                onMoveDown = { onMoveLayerDown(elem.id) },
                                onDelete = { onRemoveElement(elem.id) }
                            )
                        }
                    }

                    // Element Inspector & Transforms
                    if (selectedElement != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        ElementInspectorPanel(
                            element = selectedElement,
                            onUpdatePosition = { x, y -> onUpdatePosition(selectedElement.id, x, y) },
                            onUpdateSize = { w, h -> onUpdateSize(selectedElement.id, w, h) },
                            onUpdateCrop = { l, t, r, b -> onUpdateCrop(selectedElement.id, l, t, r, b) },
                            onUpdateScale = { s -> onUpdateScale(selectedElement.id, s) },
                            onUpdateRotation = { r -> onUpdateRotation(selectedElement.id, r) },
                            onUpdateOpacity = { o -> onUpdateOpacity(selectedElement.id, o) },
                            onUpdateText = { t, st -> onUpdateText(selectedElement.id, t, st) },
                            onUpdateColors = { ac, bg -> onUpdateColors(selectedElement.id, ac, bg) },
                            onUpdateVideoLoop = { l -> onUpdateVideoLoop(selectedElement.id, l) },
                            onReplaceContent = {
                                val mime = if (selectedElement.type == CompositionElementType.VIDEO) "video/*" else "image/*"
                                replaceUriPickerLauncher.launch(mime)
                            }
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// INTERACTIVE COMPOSITION PREVIEW (16:9 Exact Output Simulation)
// -------------------------------------------------------------

@Composable
private fun OutputCompositionInteractivePreview(
    compositionConfig: CompositionConfig,
    onSelectLayer: (String) -> Unit,
    onUpdateGamePosition: (Float, Float) -> Unit,
    onUpdateElementPosition: (String, Float, Float) -> Unit,
    onToggleGrid: () -> Unit
) {
    val game = compositionConfig.gameVideoConfig
    val isGameSelected = compositionConfig.isGameVideoSelected
    val selectedId = compositionConfig.selectedElementId

    val bgParsedColor = try {
        val clean = game.backgroundColorHex.trim().removePrefix("#")
        val longVal = if (clean.length == 8) clean.toLong(16) else if (clean.length == 6) ("FF$clean").toLong(16) else 0xFF050A14
        Color(longVal)
    } catch (_: Exception) {
        Color(0xFF050A14)
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = EsportsSurfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, EsportsSurfaceBorder)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Preview HUD Status Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(EsportsCyan, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "CANVAS: ${compositionConfig.canvasWidth}x${compositionConfig.canvasHeight} (16:9)",
                        color = EsportsTextPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onToggleGrid,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.GridOn,
                            contentDescription = "Toggle Grid Guidelines",
                            tint = if (compositionConfig.showGridOverlay) EsportsCyan else EsportsTextMuted,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isGameSelected) "SELECTED: [GAME BASE]" else "SELECTED: [OVERLAY]",
                        color = if (isGameSelected) EsportsCyan else EsportsGold,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 16:9 Interactive Canvas Viewport
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(bgParsedColor)
                    .border(1.5.dp, if (isGameSelected) EsportsCyan.copy(alpha = 0.5f) else EsportsSurfaceBorder, RoundedCornerShape(8.dp))
                    .pointerInput(compositionConfig.selectedLayerId) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val canvasW = size.width.toFloat()
                            val canvasH = size.height.toFloat()
                            if (canvasW > 0 && canvasH > 0) {
                                val deltaX = dragAmount.x / canvasW
                                val deltaY = dragAmount.y / canvasH
                                if (compositionConfig.isGameVideoSelected) {
                                    val newX = (game.xPercent + deltaX).coerceIn(0.0f, 1.0f)
                                    val newY = (game.yPercent + deltaY).coerceIn(0.0f, 1.0f)
                                    onUpdateGamePosition(newX, newY)
                                } else {
                                    val elemId = compositionConfig.selectedElementId
                                    val targetElem = compositionConfig.elements.find { it.id == elemId }
                                    if (targetElem != null) {
                                        val newX = (targetElem.xPercent + deltaX).coerceIn(0.0f, 1.0f)
                                        val newY = (targetElem.yPercent + deltaY).coerceIn(0.0f, 1.0f)
                                        onUpdateElementPosition(targetElem.id, newX, newY)
                                    }
                                }
                            }
                        }
                    }
            ) {
                val boxWidthPx = maxWidth
                val boxHeightPx = maxHeight

                // Optional Rule-of-Thirds Grid Overlay (UI ONLY)
                if (compositionConfig.showGridOverlay) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        val gridColor = EsportsCyan.copy(alpha = 0.25f)
                        // Vertical guidelines
                        drawLine(
                            color = gridColor,
                            start = Offset(size.width / 3f, 0f),
                            end = Offset(size.width / 3f, size.height),
                            pathEffect = pathEffect,
                            strokeWidth = 1f
                        )
                        drawLine(
                            color = gridColor,
                            start = Offset(size.width * 2f / 3f, 0f),
                            end = Offset(size.width * 2f / 3f, size.height),
                            pathEffect = pathEffect,
                            strokeWidth = 1f
                        )
                        // Horizontal guidelines
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, size.height / 3f),
                            end = Offset(size.width, size.height / 3f),
                            pathEffect = pathEffect,
                            strokeWidth = 1f
                        )
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, size.height * 2f / 3f),
                            end = Offset(size.width, size.height * 2f / 3f),
                            pathEffect = pathEffect,
                            strokeWidth = 1f
                        )
                        // Center crosshair
                        drawLine(
                            color = EsportsCyan.copy(alpha = 0.4f),
                            start = Offset(size.width / 2f - 10f, size.height / 2f),
                            end = Offset(size.width / 2f + 10f, size.height / 2f),
                            strokeWidth = 1.5f
                        )
                        drawLine(
                            color = EsportsCyan.copy(alpha = 0.4f),
                            start = Offset(size.width / 2f, size.height / 2f - 10f),
                            end = Offset(size.width / 2f, size.height / 2f + 10f),
                            strokeWidth = 1.5f
                        )
                    }
                }

                // 1. GAME VIDEO BASE LAYER WIREFRAME
                if (game.isVisible) {
                    val gameBaseW = if (game.scaleMode == GameScaleMode.CUSTOM) game.widthPercent else 1.0f
                    val gameBaseH = if (game.scaleMode == GameScaleMode.CUSTOM) game.heightPercent else 1.0f

                    val gameW = (boxWidthPx * gameBaseW * game.scale).coerceAtLeast(40.dp)
                    val gameH = (boxHeightPx * gameBaseH * game.scale).coerceAtLeast(25.dp)

                    val startOffset = (boxWidthPx * game.xPercent) - (gameW / 2f)
                    val topOffset = (boxHeightPx * game.yPercent) - (gameH / 2f)

                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    x = (startOffset.toPx()).toInt(),
                                    y = (topOffset.toPx()).toInt()
                                )
                            }
                            .size(gameW, gameH)
                            .rotate(game.rotationDeg)
                            .background(
                                Color(0xFF0F172A).copy(alpha = game.opacity),
                                RoundedCornerShape(4.dp)
                            )
                            .border(
                                width = if (isGameSelected) 2.dp else 1.dp,
                                color = if (isGameSelected) EsportsCyan else EsportsCyan.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .clickable { onSelectLayer("GAME_VIDEO") },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SportsEsports,
                                contentDescription = "Game Stream",
                                tint = if (isGameSelected) EsportsCyan else EsportsTextMuted,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "🎮 GAME VIDEO FEED",
                                color = if (isGameSelected) EsportsCyan else EsportsTextPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "${game.scaleMode.label} • ${(game.scale * 100).toInt()}% • Crop(L:${(game.cropLeft * 100).toInt()}% R:${(game.cropRight * 100).toInt()}%)",
                                color = EsportsTextMuted,
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        // Corner drag handle indicators (UI ONLY) when Game Video is selected
                        if (isGameSelected) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(EsportsCyan, CircleShape)
                                    .align(Alignment.TopStart)
                            )
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(EsportsCyan, CircleShape)
                                    .align(Alignment.TopEnd)
                            )
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(EsportsCyan, CircleShape)
                                    .align(Alignment.BottomStart)
                            )
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(EsportsCyan, CircleShape)
                                    .align(Alignment.BottomEnd)
                            )
                        }
                    }
                }

                // 2. OVERLAY LAYERS WIREFRAME (Ascending Z-Index)
                compositionConfig.elements.filter { it.isVisible }.sortedBy { it.zIndex }.forEach { elem ->
                    val isElemSelected = elem.id == selectedId
                    val borderColor = if (isElemSelected) EsportsGold else when (elem.type) {
                        CompositionElementType.BOTTOM_STRIP -> EsportsCyan.copy(alpha = 0.8f)
                        CompositionElementType.BANNER -> EsportsGold.copy(alpha = 0.8f)
                        CompositionElementType.MEME -> EsportsGreen.copy(alpha = 0.8f)
                        CompositionElementType.VIDEO -> EsportsPurple.copy(alpha = 0.8f)
                        else -> Color.White.copy(alpha = 0.8f)
                    }

                    val elemW = (boxWidthPx * elem.widthPercent * elem.scale).coerceAtLeast(30.dp)
                    val elemH = (boxHeightPx * elem.heightPercent * elem.scale).coerceAtLeast(18.dp)

                    val startOffset = (boxWidthPx * elem.xPercent) - (elemW / 2f)
                    val topOffset = (boxHeightPx * elem.yPercent) - (elemH / 2f)

                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    x = (startOffset.toPx()).toInt(),
                                    y = (topOffset.toPx()).toInt()
                                )
                            }
                            .size(elemW, elemH)
                            .rotate(elem.rotationDeg)
                            .background(
                                Color(0xFF030712).copy(alpha = elem.opacity * 0.9f),
                                RoundedCornerShape(4.dp)
                            )
                            .border(
                                width = if (isElemSelected) 2.dp else 1.dp,
                                color = borderColor,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .clickable { onSelectLayer(elem.id) },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(2.dp)
                        ) {
                            Text(
                                text = elem.name.take(16),
                                color = if (isElemSelected) EsportsGold else Color.White,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            if (!elem.titleText.isNullOrBlank()) {
                                Text(
                                    text = elem.titleText.take(14),
                                    color = EsportsTextMuted,
                                    fontSize = 7.sp,
                                    maxLines = 1
                                )
                            }
                        }

                        // Corner handles when overlay is selected
                        if (isElemSelected) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(EsportsGold, CircleShape)
                                    .align(Alignment.TopStart)
                            )
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(EsportsGold, CircleShape)
                                    .align(Alignment.TopEnd)
                            )
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(EsportsGold, CircleShape)
                                    .align(Alignment.BottomStart)
                            )
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(EsportsGold, CircleShape)
                                    .align(Alignment.BottomEnd)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Hint Text for Interactive Drag
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "👆 Touch & drag inside canvas to reposition active layer in real-time",
                    color = EsportsTextMuted,
                    fontSize = 10.sp
                )
                Text(
                    text = "Controls = UI Only",
                    color = EsportsGreen,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

// -------------------------------------------------------------
// GAME VIDEO CONTROLS INSPECTOR (Fit, Fill, Fullscreen, Custom, Crop, Scale)
// -------------------------------------------------------------

@Composable
private fun GameVideoControlsInspector(
    gameConfig: GameVideoConfig,
    onSetScaleMode: (GameScaleMode) -> Unit,
    onUpdatePosition: (Float, Float) -> Unit,
    onMoveGame: (Float, Float) -> Unit,
    onUpdateSize: (Float, Float) -> Unit,
    onUpdateScale: (Float) -> Unit,
    onUpdateCrop: (Float, Float, Float, Float) -> Unit,
    onUpdateRotation: (Float) -> Unit,
    onUpdateOpacity: (Float) -> Unit,
    onToggleVisibility: () -> Unit,
    onResetTransform: () -> Unit,
    onSetFitPreset: () -> Unit,
    onSetFillPreset: () -> Unit,
    onSetFullscreenPreset: () -> Unit,
    onSetBackgroundColor: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = EsportsSurface,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, EsportsCyan.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Inspector Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.SportsEsports,
                        contentDescription = "Game Transform",
                        tint = EsportsCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "GAME FEED POSITION & SCALING",
                        color = EsportsTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Surface(
                    onClick = onResetTransform,
                    shape = RoundedCornerShape(6.dp),
                    color = EsportsSurfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(1.dp, EsportsCyan.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset Game Transform",
                            tint = EsportsCyan,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Reset Default",
                            color = EsportsCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 1. PRESET SCALE MODES (Fit, Fill, Fullscreen, Custom)
            Text(
                text = "SCALE MODE PRESETS",
                color = EsportsTextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ScaleModeChip(
                    title = "FIT (Safe)",
                    subtitle = "Aspect Fit",
                    isSelected = gameConfig.scaleMode == GameScaleMode.FIT,
                    onClick = onSetFitPreset,
                    modifier = Modifier.weight(1f)
                )
                ScaleModeChip(
                    title = "FILL",
                    subtitle = "No Black Bars",
                    isSelected = gameConfig.scaleMode == GameScaleMode.FILL,
                    onClick = onSetFillPreset,
                    modifier = Modifier.weight(1f)
                )
                ScaleModeChip(
                    title = "FULLSCREEN",
                    subtitle = "Stretch 100%",
                    isSelected = gameConfig.scaleMode == GameScaleMode.FULLSCREEN,
                    onClick = onSetFullscreenPreset,
                    modifier = Modifier.weight(1f)
                )
                ScaleModeChip(
                    title = "CUSTOM",
                    subtitle = "Manual Pos/Crop",
                    isSelected = gameConfig.scaleMode == GameScaleMode.CUSTOM,
                    onClick = { onSetScaleMode(GameScaleMode.CUSTOM) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. 4-WAY MOVE D-PAD & QUICK ZOOM STEPPER
            Text(
                text = "PRECISION NUDGE & ZOOM CONTROLS",
                color = EsportsTextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // D-Pad
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = { onMoveGame(0f, -0.02f) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move Up", tint = EsportsCyan)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = { onMoveGame(-0.02f, 0f) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Move Left", tint = EsportsCyan)
                        }
                        IconButton(
                            onClick = { onUpdatePosition(0.5f, 0.5f) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.CenterFocusStrong, contentDescription = "Center", tint = EsportsGold)
                        }
                        IconButton(
                            onClick = { onMoveGame(0.02f, 0f) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Move Right", tint = EsportsCyan)
                        }
                    }
                    IconButton(
                        onClick = { onMoveGame(0f, 0.02f) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move Down", tint = EsportsCyan)
                    }
                }

                // Quick Zoom Buttons
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            onClick = { onUpdateScale((gameConfig.scale - 0.1f).coerceAtLeast(0.2f)) },
                            shape = RoundedCornerShape(6.dp),
                            color = EsportsSurfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(1.dp, EsportsSurfaceBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "-0.1x",
                                color = EsportsTextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                        Surface(
                            onClick = { onUpdateScale(1.0f) },
                            shape = RoundedCornerShape(6.dp),
                            color = EsportsCyan.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, EsportsCyan.copy(alpha = 0.4f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "1.0x Reset",
                                color = EsportsCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                        Surface(
                            onClick = { onUpdateScale((gameConfig.scale + 0.1f).coerceAtMost(3.0f)) },
                            shape = RoundedCornerShape(6.dp),
                            color = EsportsSurfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(1.dp, EsportsSurfaceBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "+0.1x",
                                color = EsportsTextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            onClick = { onUpdateScale(1.25f) },
                            shape = RoundedCornerShape(6.dp),
                            color = EsportsSurfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(1.dp, EsportsSurfaceBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "1.25x Zoom",
                                color = EsportsTextSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                        Surface(
                            onClick = { onUpdateScale(1.50f) },
                            shape = RoundedCornerShape(6.dp),
                            color = EsportsSurfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(1.dp, EsportsSurfaceBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "1.50x Zoom",
                                color = EsportsTextSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. CONTINUOUS SLIDERS (PAN X, PAN Y, ZOOM MULTIPLIER)
            EsportsSlider(
                title = "Game Horizontal Position (X)",
                value = gameConfig.xPercent,
                onValueChange = { onUpdatePosition(it, gameConfig.yPercent) },
                valueLabel = "${(gameConfig.xPercent * 100).toInt()}%",
                valueRange = 0.0f..1.0f
            )

            EsportsSlider(
                title = "Game Vertical Position (Y)",
                value = gameConfig.yPercent,
                onValueChange = { onUpdatePosition(gameConfig.xPercent, it) },
                valueLabel = "${(gameConfig.yPercent * 100).toInt()}%",
                valueRange = 0.0f..1.0f
            )

            EsportsSlider(
                title = "Game Zoom / Scale Multiplier",
                value = gameConfig.scale,
                onValueChange = onUpdateScale,
                valueLabel = String.format("%.2fx", gameConfig.scale),
                valueRange = 0.20f..3.0f
            )

            if (gameConfig.scaleMode == GameScaleMode.CUSTOM) {
                EsportsSlider(
                    title = "Custom Width (% of Canvas)",
                    value = gameConfig.widthPercent,
                    onValueChange = { onUpdateSize(it, gameConfig.heightPercent) },
                    valueLabel = "${(gameConfig.widthPercent * 100).toInt()}%",
                    valueRange = 0.1f..2.0f
                )

                EsportsSlider(
                    title = "Custom Height (% of Canvas)",
                    value = gameConfig.heightPercent,
                    onValueChange = { onUpdateSize(gameConfig.widthPercent, it) },
                    valueLabel = "${(gameConfig.heightPercent * 100).toInt()}%",
                    valueRange = 0.1f..2.0f
                )
            }

            // 4. 4-WAY GPU EDGE CROPPING
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "4-WAY INDEPENDENT GPU EDGE CROP",
                    color = EsportsTextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                TextButton(
                    onClick = { onUpdateCrop(0f, 0f, 0f, 0f) }
                ) {
                    Text("Clear Crop", color = EsportsCyan, fontSize = 10.sp)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    EsportsSlider(
                        title = "Crop Left",
                        value = gameConfig.cropLeft,
                        onValueChange = { onUpdateCrop(it, gameConfig.cropTop, gameConfig.cropRight, gameConfig.cropBottom) },
                        valueLabel = "${(gameConfig.cropLeft * 100).toInt()}%",
                        valueRange = 0.0f..0.45f
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    EsportsSlider(
                        title = "Crop Right",
                        value = gameConfig.cropRight,
                        onValueChange = { onUpdateCrop(gameConfig.cropLeft, gameConfig.cropTop, it, gameConfig.cropBottom) },
                        valueLabel = "${(gameConfig.cropRight * 100).toInt()}%",
                        valueRange = 0.0f..0.45f
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    EsportsSlider(
                        title = "Crop Top",
                        value = gameConfig.cropTop,
                        onValueChange = { onUpdateCrop(gameConfig.cropLeft, it, gameConfig.cropRight, gameConfig.cropBottom) },
                        valueLabel = "${(gameConfig.cropTop * 100).toInt()}%",
                        valueRange = 0.0f..0.45f
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    EsportsSlider(
                        title = "Crop Bottom",
                        value = gameConfig.cropBottom,
                        onValueChange = { onUpdateCrop(gameConfig.cropLeft, gameConfig.cropTop, gameConfig.cropRight, it) },
                        valueLabel = "${(gameConfig.cropBottom * 100).toInt()}%",
                        valueRange = 0.0f..0.45f
                    )
                }
            }

            // 5. ROTATION & OPACITY
            EsportsSlider(
                title = "Game Feed Rotation",
                value = gameConfig.rotationDeg,
                onValueChange = onUpdateRotation,
                valueLabel = "${gameConfig.rotationDeg.toInt()}°",
                valueRange = -180f..180f
            )

            // Quick Rotation Snap Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                RotationSnapButton("0° Normal", 0f, onUpdateRotation, Modifier.weight(1f))
                RotationSnapButton("90° CW", 90f, onUpdateRotation, Modifier.weight(1f))
                RotationSnapButton("180° Invert", 180f, onUpdateRotation, Modifier.weight(1f))
                RotationSnapButton("-90° CCW", -90f, onUpdateRotation, Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(10.dp))

            EsportsSlider(
                title = "Game Feed Opacity",
                value = gameConfig.opacity,
                onValueChange = onUpdateOpacity,
                valueLabel = "${(gameConfig.opacity * 100).toInt()}%",
                valueRange = 0.05f..1.0f
            )

            // 6. CANVAS BACKGROUND COLOR (Letterboxing Backing)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "LETTERBOX CANVAS BACKGROUND COLOR",
                color = EsportsTextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                BgColorChip("Cyber Navy", "#FF050A14", gameConfig.backgroundColorHex, onSetBackgroundColor, Modifier.weight(1f))
                BgColorChip("Pitch Black", "#FF000000", gameConfig.backgroundColorHex, onSetBackgroundColor, Modifier.weight(1f))
                BgColorChip("Deep Slate", "#FF0B1220", gameConfig.backgroundColorHex, onSetBackgroundColor, Modifier.weight(1f))
                BgColorChip("Midnight", "#FF120824", gameConfig.backgroundColorHex, onSetBackgroundColor, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ScaleModeChip(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = if (isSelected) EsportsCyan.copy(alpha = 0.20f) else EsportsSurfaceVariant,
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) EsportsCyan else EsportsSurfaceBorder
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                color = if (isSelected) EsportsCyan else EsportsTextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                color = EsportsTextMuted,
                fontSize = 8.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun RotationSnapButton(
    label: String,
    deg: Float,
    onRotate: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = { onRotate(deg) },
        shape = RoundedCornerShape(4.dp),
        color = EsportsSurfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, EsportsSurfaceBorder),
        modifier = modifier
    ) {
        Text(
            text = label,
            color = EsportsTextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 5.dp)
        )
    }
}

@Composable
private fun BgColorChip(
    label: String,
    hex: String,
    currentHex: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isSelected = currentHex.equals(hex, ignoreCase = true)
    Surface(
        onClick = { onSelect(hex) },
        shape = RoundedCornerShape(6.dp),
        color = if (isSelected) EsportsCyan.copy(alpha = 0.15f) else EsportsSurfaceVariant,
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) EsportsCyan else EsportsSurfaceBorder
        ),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            val previewColor = try {
                Color(hex.trim().removePrefix("#").toLong(16))
            } catch (_: Exception) {
                Color.Black
            }
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(previewColor, CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                color = if (isSelected) EsportsCyan else EsportsTextSecondary,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }
    }
}

// -------------------------------------------------------------
// OVERLAY LAYER ITEMS & INSPECTOR
// -------------------------------------------------------------

@Composable
private fun AddElementButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = EsportsSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, tint.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                color = EsportsTextPrimary,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun CompositionLayerItem(
    element: CompositionElement,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onToggleVisibility: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) EsportsGold else EsportsSurfaceBorder,
        label = "border"
    )

    Surface(
        onClick = onSelect,
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) EsportsGold.copy(alpha = 0.10f) else EsportsSurface,
        border = androidx.compose.foundation.BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor)
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
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = when (element.type) {
                        CompositionElementType.PHOTO -> EsportsCyan.copy(alpha = 0.2f)
                        CompositionElementType.PNG -> EsportsGold.copy(alpha = 0.2f)
                        CompositionElementType.MEME -> EsportsGreen.copy(alpha = 0.2f)
                        CompositionElementType.VIDEO -> EsportsPurple.copy(alpha = 0.2f)
                        CompositionElementType.BANNER -> EsportsGold.copy(alpha = 0.2f)
                        CompositionElementType.BOTTOM_STRIP -> EsportsCyan.copy(alpha = 0.2f)
                        CompositionElementType.CUSTOM_GRAPHICS -> EsportsCyan.copy(alpha = 0.2f)
                    }
                ) {
                    Text(
                        text = element.type.badgeText,
                        color = when (element.type) {
                            CompositionElementType.PHOTO -> EsportsCyan
                            CompositionElementType.PNG -> EsportsGold
                            CompositionElementType.MEME -> EsportsGreen
                            CompositionElementType.VIDEO -> EsportsPurple
                            CompositionElementType.BANNER -> EsportsGold
                            CompositionElementType.BOTTOM_STRIP -> EsportsCyan
                            CompositionElementType.CUSTOM_GRAPHICS -> EsportsCyan
                        },
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column {
                    Text(
                        text = element.name,
                        color = if (element.isVisible) EsportsTextPrimary else EsportsTextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = "Z:${element.zIndex} • Pos: (${(element.xPercent * 100).toInt()}%, ${(element.yPercent * 100).toInt()}%) • α: ${(element.opacity * 100).toInt()}%",
                        color = EsportsTextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Quick Layer Actions
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleVisibility, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = if (element.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = "Toggle Visibility",
                        tint = if (element.isVisible) EsportsCyan else EsportsTextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(onClick = onMoveUp, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = "Move Layer Up",
                        tint = EsportsTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(onClick = onMoveDown, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = "Move Layer Down",
                        tint = EsportsTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = EsportsRed,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ElementInspectorPanel(
    element: CompositionElement,
    onUpdatePosition: (Float, Float) -> Unit,
    onUpdateSize: (Float, Float) -> Unit,
    onUpdateCrop: (Float, Float, Float, Float) -> Unit,
    onUpdateScale: (Float) -> Unit,
    onUpdateRotation: (Float) -> Unit,
    onUpdateOpacity: (Float) -> Unit,
    onUpdateText: (String?, String?) -> Unit,
    onUpdateColors: (String, String) -> Unit,
    onUpdateVideoLoop: (Boolean) -> Unit,
    onReplaceContent: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = EsportsSurface,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, EsportsGold.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Inspector",
                        tint = EsportsGold,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "INSPECTOR: ${element.name.uppercase()}",
                        color = EsportsTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                if (element.type == CompositionElementType.PHOTO ||
                    element.type == CompositionElementType.PNG ||
                    element.type == CompositionElementType.VIDEO
                ) {
                    Surface(
                        onClick = onReplaceContent,
                        shape = RoundedCornerShape(6.dp),
                        color = EsportsSurfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(1.dp, EsportsGold.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = "Replace File",
                            color = EsportsGold,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Position Presets
            Text(
                text = "POSITION PRESETS",
                color = EsportsTextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PresetButton("Top-L", 0.15f, 0.15f, onUpdatePosition)
                PresetButton("Top-R", 0.85f, 0.15f, onUpdatePosition)
                PresetButton("Center", 0.50f, 0.50f, onUpdatePosition)
                PresetButton("Bottom-L", 0.15f, 0.85f, onUpdatePosition)
                PresetButton("Bottom-R", 0.85f, 0.85f, onUpdatePosition)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Move X / Y Sliders
            EsportsSlider(
                title = "Horizontal Position (X)",
                value = element.xPercent,
                onValueChange = { onUpdatePosition(it, element.yPercent) },
                valueLabel = "${(element.xPercent * 100).toInt()}%",
                valueRange = 0.02f..0.98f
            )

            EsportsSlider(
                title = "Vertical Position (Y)",
                value = element.yPercent,
                onValueChange = { onUpdatePosition(element.xPercent, it) },
                valueLabel = "${(element.yPercent * 100).toInt()}%",
                valueRange = 0.02f..0.98f
            )

            // Resize Width / Height Sliders
            EsportsSlider(
                title = "Width (% of Canvas)",
                value = element.widthPercent,
                onValueChange = { onUpdateSize(it, element.heightPercent) },
                valueLabel = "${(element.widthPercent * 100).toInt()}%",
                valueRange = 0.05f..1.0f
            )

            EsportsSlider(
                title = "Height (% of Canvas)",
                value = element.heightPercent,
                onValueChange = { onUpdateSize(element.widthPercent, it) },
                valueLabel = "${(element.heightPercent * 100).toInt()}%",
                valueRange = 0.03f..1.0f
            )

            // Scale & Opacity
            EsportsSlider(
                title = "Overall Scale Multiplier",
                value = element.scale,
                onValueChange = onUpdateScale,
                valueLabel = String.format("%.2fx", element.scale),
                valueRange = 0.2f..3.0f
            )

            EsportsSlider(
                title = "Layer Opacity",
                value = element.opacity,
                onValueChange = onUpdateOpacity,
                valueLabel = "${(element.opacity * 100).toInt()}%",
                valueRange = 0.05f..1.0f
            )

            EsportsSlider(
                title = "Rotation Angle",
                value = element.rotationDeg,
                onValueChange = onUpdateRotation,
                valueLabel = "${element.rotationDeg.toInt()}°",
                valueRange = -180f..180f
            )

            // Crop Controls Section
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "GPU CROP BOUNDARIES",
                color = EsportsTextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    EsportsSlider(
                        title = "Crop Left",
                        value = element.cropLeft,
                        onValueChange = { onUpdateCrop(it, element.cropTop, element.cropRight, element.cropBottom) },
                        valueLabel = "${(element.cropLeft * 100).toInt()}%",
                        valueRange = 0.0f..0.45f
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    EsportsSlider(
                        title = "Crop Right",
                        value = element.cropRight,
                        onValueChange = { onUpdateCrop(element.cropLeft, element.cropTop, it, element.cropBottom) },
                        valueLabel = "${(element.cropRight * 100).toInt()}%",
                        valueRange = 0.0f..0.45f
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    EsportsSlider(
                        title = "Crop Top",
                        value = element.cropTop,
                        onValueChange = { onUpdateCrop(element.cropLeft, it, element.cropRight, element.cropBottom) },
                        valueLabel = "${(element.cropTop * 100).toInt()}%",
                        valueRange = 0.0f..0.45f
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    EsportsSlider(
                        title = "Crop Bottom",
                        value = element.cropBottom,
                        onValueChange = { onUpdateCrop(element.cropLeft, element.cropTop, element.cropRight, it) },
                        valueLabel = "${(element.cropBottom * 100).toInt()}%",
                        valueRange = 0.0f..0.45f
                    )
                }
            }

            // Video loop toggle
            if (element.type == CompositionElementType.VIDEO) {
                Spacer(modifier = Modifier.height(8.dp))
                EsportsToggleRow(
                    title = "Loop Video Clip",
                    subtitle = "Seamlessly repeat video overlay on broadcast",
                    checked = element.loopVideo,
                    onCheckedChange = onUpdateVideoLoop
                )
            }

            // Text Inputs for Banners / Bottom Strips / Memes
            if (element.type == CompositionElementType.BOTTOM_STRIP ||
                element.type == CompositionElementType.BANNER ||
                element.type == CompositionElementType.MEME
            ) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "OVERLAY TYPOGRAPHY & TEXT",
                    color = EsportsTextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(6.dp))

                var titleInput by remember(element.id, element.titleText) { mutableStateOf(element.titleText ?: "") }
                var subInput by remember(element.id, element.subtitleText) { mutableStateOf(element.subtitleText ?: "") }

                OutlinedTextField(
                    value = titleInput,
                    onValueChange = {
                        titleInput = it
                        onUpdateText(it, subInput)
                    },
                    label = { Text("Primary Header / Ticker Text") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EsportsGold,
                        unfocusedBorderColor = EsportsSurfaceBorder,
                        focusedTextColor = EsportsTextPrimary,
                        unfocusedTextColor = EsportsTextPrimary
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = subInput,
                    onValueChange = {
                        subInput = it
                        onUpdateText(titleInput, it)
                    },
                    label = { Text("Secondary Marquee / Sponsor Subtitle") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EsportsGold,
                        unfocusedBorderColor = EsportsSurfaceBorder,
                        focusedTextColor = EsportsTextPrimary,
                        unfocusedTextColor = EsportsTextPrimary
                    ),
                    singleLine = true
                )
            }
        }
    }
}

@Composable
private fun PresetButton(
    label: String,
    x: Float,
    y: Float,
    onSelect: (Float, Float) -> Unit
) {
    Surface(
        onClick = { onSelect(x, y) },
        shape = RoundedCornerShape(4.dp),
        color = EsportsSurfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, EsportsSurfaceBorder)
    ) {
        Text(
            text = label,
            color = EsportsTextSecondary,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
        )
    }
}
