package com.dark.tool_neuron.repo.ums

import com.dark.tool_neuron.data.Tags
import com.dark.tool_neuron.data.UmsCollections
import com.dark.tool_neuron.models.table_schema.Persona
import com.dark.ums.UmsRecord
import com.dark.ums.UnifiedMemorySystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray

class UmsPersonaRepository(private val ums: UnifiedMemorySystem) {

    private val collection = UmsCollections.PERSONAS
    private val _allPersonas = MutableStateFlow<List<Persona>>(emptyList())

    fun init() {
        ums.ensureCollection(collection)
        ums.addIndex(collection, Tags.Persona.ENTITY_ID, UnifiedMemorySystem.WIRE_BYTES)
        refreshCache()
    }

    private fun refreshCache() {
        _allPersonas.value = ums.getAll(collection).map { it.toPersona() }
            .sortedBy { it.createdAt }
    }

    suspend fun insert(persona: Persona) = withContext(Dispatchers.IO) {
        ums.put(collection, persona.toRecord())
        refreshCache()
    }

    suspend fun getAllOnce(): List<Persona> = withContext(Dispatchers.IO) {
        ums.getAll(collection).map { it.toPersona() }.sortedBy { it.createdAt }
    }

    suspend fun getById(id: String): Persona? = withContext(Dispatchers.IO) {
        ums.queryString(collection, Tags.Persona.ENTITY_ID, id)
            .firstOrNull()?.toPersona()
    }
}

private fun Persona.toRecord(existingId: Int = 0): UmsRecord {
    val b = UmsRecord.create()
    if (existingId != 0) b.id(existingId)
    b.putString(Tags.Persona.ENTITY_ID, id)
    b.putString(Tags.Persona.NAME, name)
    b.putString(Tags.Persona.AVATAR, avatar)
    b.putString(Tags.Persona.SYSTEM_PROMPT, systemPrompt)
    b.putString(Tags.Persona.GREETING, greeting)
    b.putBool(Tags.Persona.IS_DEFAULT, isDefault)
    b.putTimestamp(Tags.Persona.CREATED_AT, createdAt)
    b.putString(Tags.Persona.DESCRIPTION, description)
    b.putString(Tags.Persona.PERSONALITY, personality)
    b.putString(Tags.Persona.SCENARIO, scenario)
    b.putString(Tags.Persona.EXAMPLE_MESSAGES, exampleMessages)
    b.putString(Tags.Persona.ALTERNATE_GREETINGS, JSONArray(alternateGreetings).toString())
    b.putString(Tags.Persona.TAGS, JSONArray(tags).toString())
    if (avatarUri != null) b.putString(Tags.Persona.AVATAR_URI, avatarUri)
    b.putString(Tags.Persona.CREATOR_NOTES, creatorNotes)
    b.putString(Tags.Persona.SAMPLING_PROFILE, samplingProfile)
    b.putString(Tags.Persona.CONTROL_VECTORS, controlVectors)
    return b.build()
}

private fun UmsRecord.toPersona(): Persona {
    val altGreetingsJson = getString(Tags.Persona.ALTERNATE_GREETINGS) ?: "[]"
    val tagsJson = getString(Tags.Persona.TAGS) ?: "[]"
    return Persona(
        id = getString(Tags.Persona.ENTITY_ID) ?: "",
        name = getString(Tags.Persona.NAME) ?: "",
        avatar = getString(Tags.Persona.AVATAR) ?: "",
        systemPrompt = getString(Tags.Persona.SYSTEM_PROMPT) ?: "",
        greeting = getString(Tags.Persona.GREETING) ?: "",
        isDefault = getBool(Tags.Persona.IS_DEFAULT) ?: false,
        createdAt = getTimestamp(Tags.Persona.CREATED_AT) ?: System.currentTimeMillis(),
        description = getString(Tags.Persona.DESCRIPTION) ?: "",
        personality = getString(Tags.Persona.PERSONALITY) ?: "",
        scenario = getString(Tags.Persona.SCENARIO) ?: "",
        exampleMessages = getString(Tags.Persona.EXAMPLE_MESSAGES) ?: "",
        alternateGreetings = parseJsonStringList(altGreetingsJson),
        tags = parseJsonStringList(tagsJson),
        avatarUri = getString(Tags.Persona.AVATAR_URI),
        creatorNotes = getString(Tags.Persona.CREATOR_NOTES) ?: "",
        samplingProfile = getString(Tags.Persona.SAMPLING_PROFILE) ?: "",
        controlVectors = getString(Tags.Persona.CONTROL_VECTORS) ?: ""
    )
}

private fun parseJsonStringList(json: String): List<String> {
    if (json.isBlank() || json == "[]") return emptyList()
    return try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { arr.getString(it) }
    } catch (_: Exception) {
        emptyList()
    }
}
