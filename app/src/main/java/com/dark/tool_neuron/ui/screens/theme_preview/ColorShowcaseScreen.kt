package com.dark.tool_neuron.ui.screens.theme_preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.ui.components.TnProgressBar
import com.dark.tool_neuron.ui.icons.TnIcons

@Composable
fun ColorShowcaseScreen(paletteName: String, isDark: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Scaffold(
        containerColor = scheme.background,
        contentColor = scheme.onBackground,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Header(paletteName = paletteName, isDark = isDark)
            KeyRolesGrid(scheme)
            SurfacesStrip(scheme)
            ButtonsRow()
            ChipsRow()
            CardsRow()
            ControlsRow()
            TextFieldsBlock()
            ChatBubbleDemo()
            TypographyBlock()
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Header(paletteName: String, isDark: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = scheme.primaryContainer,
        contentColor = scheme.onPrimaryContainer,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                imageVector = TnIcons.Sparkles,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = paletteName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (isDark) "Dark mode" else "Light mode",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun KeyRolesGrid(scheme: ColorScheme) {
    Text("Key color roles", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    val items = listOf(
        "primary" to (scheme.primary to scheme.onPrimary),
        "primaryContainer" to (scheme.primaryContainer to scheme.onPrimaryContainer),
        "secondary" to (scheme.secondary to scheme.onSecondary),
        "secondaryContainer" to (scheme.secondaryContainer to scheme.onSecondaryContainer),
        "tertiary" to (scheme.tertiary to scheme.onTertiary),
        "tertiaryContainer" to (scheme.tertiaryContainer to scheme.onTertiaryContainer),
        "error" to (scheme.error to scheme.onError),
        "errorContainer" to (scheme.errorContainer to scheme.onErrorContainer),
    )
    items.chunked(2).forEach { pair ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            pair.forEach { (label, colors) ->
                ColorSwatch(label, colors.first, colors.second, modifier = Modifier.weight(1f))
            }
            if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ColorSwatch(label: String, bg: Color, fg: Color, modifier: Modifier = Modifier) {
    Surface(
        color = bg,
        contentColor = fg,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.height(72.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "#${bg.hex()}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun SurfacesStrip(scheme: ColorScheme) {
    Text("Surfaces", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SurfaceTile("background", scheme.background, scheme.onBackground, modifier = Modifier.weight(1f))
        SurfaceTile("surface", scheme.surface, scheme.onSurface, modifier = Modifier.weight(1f))
        SurfaceTile("surfaceVariant", scheme.surfaceVariant, scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SurfaceTile("outline", scheme.outline, scheme.surface, modifier = Modifier.weight(1f))
        SurfaceTile("outlineVariant", scheme.outlineVariant, scheme.onSurface, modifier = Modifier.weight(1f))
        SurfaceTile("surfaceTint", scheme.surfaceTint, scheme.onPrimary, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SurfaceTile(label: String, bg: Color, fg: Color, modifier: Modifier = Modifier) {
    Surface(
        color = bg,
        contentColor = fg,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.height(56.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ButtonsRow() {
    Text("Buttons", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = {}) { Text("Filled") }
        ElevatedButton(onClick = {}) { Text("Elevated") }
        OutlinedButton(onClick = {}) { Text("Outlined") }
        TextButton(onClick = {}) { Text("Text") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = {},
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) { Text("Destructive") }
        Button(
            onClick = {},
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        ) { Text("Secondary") }
    }
}

@Composable
private fun ChipsRow() {
    Text("Chips", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AssistChip(onClick = {}, label = { Text("Assist") })
        FilterChip(selected = true, onClick = {}, label = { Text("Active") })
        FilterChip(selected = false, onClick = {}, label = { Text("Inactive") })
    }
}

@Composable
private fun CardsRow() {
    Text("Cards", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            modifier = Modifier.weight(1f),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("Card", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("surfaceVariant", style = MaterialTheme.typography.bodySmall)
            }
        }
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
            modifier = Modifier.weight(1f),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("Card", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("primaryContainer", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ControlsRow() {
    var sw by remember { mutableStateOf(true) }
    var slider by remember { mutableStateOf(0.6f) }
    Text("Controls", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Switch", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                Switch(checked = sw, onCheckedChange = { sw = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Slider", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                Slider(value = slider, onValueChange = { slider = it }, modifier = Modifier.weight(2f))
            }
            TnProgressBar(
                progress = slider,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TextFieldsBlock() {
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("Typed text") }
    Text("Text fields", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = a,
            onValueChange = { a = it },
            placeholder = { Text("Empty outlined") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = b,
            onValueChange = { b = it },
            label = { Text("Filled label") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ChatBubbleDemo() {
    val scheme = MaterialTheme.colorScheme
    Text("Chat bubbles", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Surface(
            color = scheme.surfaceVariant,
            contentColor = scheme.onSurfaceVariant,
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp),
        ) {
            Text(
                "Assistant reply with some detail about the requested answer.",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Surface(
            color = scheme.primary,
            contentColor = scheme.onPrimary,
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomEnd = 18.dp, bottomStart = 18.dp),
        ) {
            Text(
                "User question about the palette.",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun TypographyBlock() {
    Text("Typography", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Display Large", style = MaterialTheme.typography.displaySmall)
            Text("Headline Medium", style = MaterialTheme.typography.headlineMedium)
            Text("Title Medium", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Body reads naturally and maintains contrast.", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Label small — used on chips and tight rows.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun Color.hex(): String {
    val a = (alpha * 255).toInt()
    val r = (red * 255).toInt()
    val g = (green * 255).toInt()
    val b = (blue * 255).toInt()
    val hex = listOf(a, r, g, b).joinToString("") { "%02X".format(it) }
    return if (hex.startsWith("FF")) hex.drop(2) else hex
}
