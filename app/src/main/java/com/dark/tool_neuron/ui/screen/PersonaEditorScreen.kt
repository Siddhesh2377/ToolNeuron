package com.dark.tool_neuron.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.dark.tool_neuron.di.AppContainer
import com.dark.tool_neuron.models.table_schema.Persona
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.worker.PersonaCardConverter
import com.dark.tool_neuron.worker.PersonaCleanupHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.dark.tool_neuron.global.AppPaths
import java.io.File
import com.dark.tool_neuron.ui.icons.TnIcons

private val WHITESPACE_REGEX = "\\s+".toRegex()

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PersonaEditorScreen(
    personaId: String?, // null = create new
    onNavigateBack: () -> Unit,
    onDeleted: () -> Unit
) {
    val context = LocalContext.current
    val personaRepo = remember { AppContainer.getPersonaRepo() }
    val scope = rememberCoroutineScope()

    // Editable state
    var name by remember { mutableStateOf("") }
    var avatar by remember { mutableStateOf("") }
    var avatarUri by remember { mutableStateOf<String?>(null) }
    var description by remember { mutableStateOf("") }
    var greeting by remember { mutableStateOf("") }
    var personality by remember { mutableStateOf("") }
    var scenario by remember { mutableStateOf("") }
    var exampleMessages by remember { mutableStateOf("") }
    var creatorNotes by remember { mutableStateOf("") }
    val alternateGreetings = remember { mutableStateListOf<String>() }
    val tags = remember { mutableStateListOf<String>() }
    var tagInput by remember { mutableStateOf("") }

    var isDefault by remember { mutableStateOf(false) }
    var existingPersona by remember { mutableStateOf<Persona?>(null) }
    var isLoaded by remember { mutableStateOf(personaId == null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var descriptionTokenCount by remember { mutableIntStateOf(0) }

    // Persona Engine: Sampling Profile
    var samplingProfileJson by remember { mutableStateOf("") }
    var controlVectorsJson by remember { mutableStateOf("") }

    // Personality axis sliders (parsed from controlVectorsJson)
    var warmthSlider by remember { mutableFloatStateOf(0f) }
    var energySlider by remember { mutableFloatStateOf(0f) }
    var humorSlider by remember { mutableFloatStateOf(0f) }
    var formalitySlider by remember { mutableFloatStateOf(0f) }
    var verbositySlider by remember { mutableFloatStateOf(0f) }
    var emotionSlider by remember { mutableFloatStateOf(0f) }

    // Load existing persona
    LaunchedEffect(personaId) {
        if (personaId != null) {
            withContext(Dispatchers.IO) {
                personaRepo.getById(personaId)?.let { persona ->
                    existingPersona = persona
                    name = persona.name
                    avatar = persona.avatar
                    avatarUri = persona.avatarUri
                    description = persona.description.ifBlank { persona.systemPrompt }
                    greeting = persona.greeting
                    personality = persona.personality
                    scenario = persona.scenario
                    exampleMessages = persona.exampleMessages
                    creatorNotes = persona.creatorNotes
                    samplingProfileJson = persona.samplingProfile
                    controlVectorsJson = persona.controlVectors
                    // Parse axis sliders from control vectors JSON
                    try {
                        if (persona.controlVectors.isNotBlank() && !persona.controlVectors.trimStart().startsWith("[")) {
                            val cvJson = org.json.JSONObject(persona.controlVectors)
                            warmthSlider = cvJson.optDouble("warmth", 0.0).toFloat()
                            energySlider = cvJson.optDouble("energy", 0.0).toFloat()
                            humorSlider = cvJson.optDouble("humor", 0.0).toFloat()
                            formalitySlider = cvJson.optDouble("formality", 0.0).toFloat()
                            verbositySlider = cvJson.optDouble("verbosity", 0.0).toFloat()
                            emotionSlider = cvJson.optDouble("emotion", 0.0).toFloat()
                        }
                    } catch (_: Exception) {}
                    alternateGreetings.clear()
                    alternateGreetings.addAll(persona.alternateGreetings)
                    tags.clear()
                    tags.addAll(persona.tags)
                    isDefault = persona.isDefault
                    isLoaded = true
                }
            }
        }
    }

    // Token count for description
    LaunchedEffect(description) {
        descriptionTokenCount = description.split(WHITESPACE_REGEX).count { it.isNotBlank() }
    }

    // Avatar image picker
    val avatarDir = remember { AppPaths.personaAvatars(context).also { it.mkdirs() } }
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val targetId = existingPersona?.id ?: "new_${System.currentTimeMillis()}"
                val targetFile = File(avatarDir, "$targetId.png")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                avatarUri = targetFile.absolutePath
            }
        }
    }

    // Export launcher
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null && existingPersona != null) {
            scope.launch(Dispatchers.IO) {
                val json = PersonaCardConverter.exportToTavernV2(existingPersona!!)
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(json.toByteArray())
                }
            }
        }
    }

    fun save() {
        if (name.isBlank()) return
        scope.launch(Dispatchers.IO) {
            val persona = (existingPersona ?: Persona(name = name)).copy(
                name = name,
                avatar = avatar,
                avatarUri = avatarUri,
                description = description,
                systemPrompt = description, // keep in sync for backward compat
                greeting = greeting,
                personality = personality,
                scenario = scenario,
                exampleMessages = exampleMessages,
                creatorNotes = creatorNotes,
                samplingProfile = samplingProfileJson,
                controlVectors = buildAxisJson(warmthSlider, energySlider, humorSlider, formalitySlider, verbositySlider, emotionSlider),
                alternateGreetings = alternateGreetings.toList(),
                tags = tags.toList()
            )
            if (existingPersona != null) {
                personaRepo.update(persona)
            } else {
                // If avatar was saved with temp id, rename the file
                if (avatarUri != null && avatarUri!!.contains("new_")) {
                    val oldFile = File(avatarUri!!)
                    if (oldFile.exists()) {
                        val newFile = File(avatarDir, "${persona.id}.png")
                        oldFile.renameTo(newFile)
                        personaRepo.insert(persona.copy(avatarUri = newFile.absolutePath))
                    } else {
                        personaRepo.insert(persona)
                    }
                } else {
                    personaRepo.insert(persona)
                }
            }
            withContext(Dispatchers.Main) { onNavigateBack() }
        }
    }

    if (!isLoaded) return

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        if (existingPersona != null) "Edit Character" else "New Character",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    ActionButton(
                        onClickListener = onNavigateBack,
                        icon = TnIcons.ArrowLeft,
                        contentDescription = "Back"
                    )
                },
                actions = {
                    TextButton(onClick = { save() }, enabled = name.isNotBlank()) {
                        Text("Save", fontWeight = FontWeight.SemiBold)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // ===== Avatar + Name + Actions =====
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar (tappable)
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { imagePickerLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            when {
                                avatarUri != null -> {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(File(avatarUri!!))
                                            .build(),
                                        contentDescription = "Avatar",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                avatar.isNotBlank() -> {
                                    Text(
                                        text = avatar,
                                        style = MaterialTheme.typography.headlineMedium
                                    )
                                }
                                else -> {
                                    Icon(
                                        TnIcons.User,
                                        contentDescription = null,
                                        modifier = Modifier.size(36.dp),
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }
                    // Edit badge
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(24.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                TnIcons.Edit,
                                contentDescription = "Change avatar",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    // Action buttons row
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (existingPersona != null && !isDefault) {
                            OutlinedButton(
                                onClick = { showDeleteDialog = true },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(TnIcons.Trash, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Delete", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        if (existingPersona != null) {
                            OutlinedButton(
                                onClick = { exportLauncher.launch("${name.ifBlank { "character" }}.json") }
                            ) {
                                Text("Export", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Name field
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Character name") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            // Emoji field (small)
            OutlinedTextField(
                value = avatar,
                onValueChange = { avatar = it.take(2) },
                modifier = Modifier.width(100.dp),
                label = { Text("Emoji") },
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )

            // ===== Description =====
            SectionLabel("Description Tokens: $descriptionTokenCount")
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                placeholder = { Text("{{char}} is a helpful and intelligent assistant.") },
                shape = RoundedCornerShape(10.dp)
            )

            // ===== First Message =====
            SectionLabel("First Message")
            OutlinedTextField(
                value = greeting,
                onValueChange = { greeting = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                placeholder = { Text("The first message from this character...") },
                shape = RoundedCornerShape(10.dp)
            )

            // ===== Alternate Greetings =====
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionLabel("Alternate Greetings")
                IconButton(onClick = { alternateGreetings.add("") }) {
                    Icon(TnIcons.Plus, contentDescription = "Add greeting")
                }
            }

            if (alternateGreetings.isEmpty()) {
                Text(
                    "No Alternate Greetings",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )
            } else {
                Column(
                    modifier = Modifier.animateContentSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    alternateGreetings.forEachIndexed { index, greet ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            OutlinedTextField(
                                value = greet,
                                onValueChange = { alternateGreetings[index] = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(80.dp),
                                placeholder = { Text("Alternate greeting ${index + 1}") },
                                shape = RoundedCornerShape(10.dp)
                            )
                            IconButton(onClick = { alternateGreetings.removeAt(index) }) {
                                Icon(
                                    TnIcons.X,
                                    contentDescription = "Remove",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ===== Personality =====
            SectionLabel("Personality")
            OutlinedTextField(
                value = personality,
                onValueChange = { personality = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                placeholder = { Text("Personality traits, speaking style...") },
                shape = RoundedCornerShape(10.dp)
            )

            // ===== Scenario =====
            SectionLabel("Scenario")
            OutlinedTextField(
                value = scenario,
                onValueChange = { scenario = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                placeholder = { Text("The scenario or setting for this character...") },
                shape = RoundedCornerShape(10.dp)
            )

            // ===== Example Messages =====
            SectionLabel("Example Messages")
            OutlinedTextField(
                value = exampleMessages,
                onValueChange = { exampleMessages = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                placeholder = { Text("<START>\n{{user}}: Hi!\n{{char}}: Hello there!") },
                shape = RoundedCornerShape(10.dp)
            )

            // ===== Tags =====
            SectionLabel("Tags")
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = tagInput,
                    onValueChange = { tagInput = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Enter value...") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = {
                        if (tagInput.isNotBlank()) {
                            tags.add(tagInput.trim())
                            tagInput = ""
                        }
                    }
                ) {
                    Text("Add")
                }
            }

            if (tags.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    tags.forEachIndexed { index, tag ->
                        AssistChip(
                            onClick = { tags.removeAt(index) },
                            label = { Text(tag) },
                            trailingIcon = {
                                Icon(
                                    TnIcons.X,
                                    contentDescription = "Remove",
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        )
                    }
                }
            }

            // ===== Creator Notes =====
            SectionLabel("Creator Notes")
            OutlinedTextField(
                value = creatorNotes,
                onValueChange = { creatorNotes = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                placeholder = { Text("Internal notes (not sent to model)...") },
                shape = RoundedCornerShape(10.dp)
            )

            // ===== Sampling Profile (Persona Engine) =====
            SectionLabel("Sampling Profile")
            Text(
                text = "Override model sampling params for this character. Leave empty to use model defaults.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            OutlinedTextField(
                value = samplingProfileJson,
                onValueChange = { samplingProfileJson = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                placeholder = {
                    Text(
                        """{"temperature": 0.85, "minP": 0.03, "dryMultiplier": 0.8, "bannedTokens": ["certainly"]}"""
                    )
                },
                shape = RoundedCornerShape(10.dp)
            )

            // ===== Personality Vectors (Control Vectors) =====
            SectionLabel("Personality")
            Text(
                text = "Steer the model's personality along each axis. Requires compatible control vector files.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            PersonalitySlider("Warmth", "cold", "warm", warmthSlider) { warmthSlider = it }
            PersonalitySlider("Energy", "calm", "energetic", energySlider) { energySlider = it }
            PersonalitySlider("Humor", "serious", "playful", humorSlider) { humorSlider = it }
            PersonalitySlider("Formality", "casual", "formal", formalitySlider) { formalitySlider = it }
            PersonalitySlider("Verbosity", "verbose", "concise", verbositySlider) { verbositySlider = it }
            PersonalitySlider("Emotion", "stoic", "expressive", emotionSlider) { emotionSlider = it }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete ${name}?") },
            text = { Text("This character will be permanently removed.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) {
                        existingPersona?.let { persona ->
                            PersonaCleanupHelper.deletePersonaWithCascade(
                                context = context,
                                persona = persona,
                                personaRepo = personaRepo
                            )
                        }
                        withContext(Dispatchers.Main) {
                            showDeleteDialog = false
                            onDeleted()
                        }
                    }
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
}

/** Personality axis slider with labeled poles. */
@Composable
private fun PersonalitySlider(
    label: String,
    negativePole: String,
    positivePole: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = if (value == 0f) "neutral" else String.format("%.1f", value),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = negativePole,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(56.dp)
            )
            Slider(
                value = value,
                onValueChange = { onValueChange(Math.round(it * 10f) / 10f) },
                valueRange = -1f..1f,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = positivePole,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(56.dp)
            )
        }
    }
}

/** Build axis-based control vectors JSON from slider values. Returns "" if all neutral. */
private fun buildAxisJson(
    warmth: Float, energy: Float, humor: Float,
    formality: Float, verbosity: Float, emotion: Float
): String {
    val axes = mapOf(
        "warmth" to warmth, "energy" to energy, "humor" to humor,
        "formality" to formality, "verbosity" to verbosity, "emotion" to emotion
    ).filter { it.value != 0f }

    if (axes.isEmpty()) return ""
    return org.json.JSONObject(axes).toString()
}
