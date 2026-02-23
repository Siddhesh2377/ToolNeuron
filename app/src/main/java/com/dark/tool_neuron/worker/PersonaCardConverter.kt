package com.dark.tool_neuron.worker

import com.dark.tool_neuron.models.table_schema.Persona
import org.json.JSONArray
import org.json.JSONObject

/**
 * TavernAI Character Card v2 import/export utility.
 * Compatible with SillyTavern, Chub.ai, and other TavernAI-compatible tools.
 */
object PersonaCardConverter {

    /**
     * Export a Persona to TavernAI Character Card v2 JSON format.
     */
    fun exportToTavernV2(persona: Persona): String {
        val data = JSONObject().apply {
            put("name", persona.name)
            put("description", persona.description.ifBlank { persona.systemPrompt })
            put("personality", persona.personality)
            put("scenario", persona.scenario)
            put("first_mes", persona.greeting)
            put("alternate_greetings", JSONArray(persona.alternateGreetings))
            put("mes_example", persona.exampleMessages)
            put("tags", JSONArray(persona.tags))
            put("creator_notes", persona.creatorNotes)
            put("system_prompt", persona.systemPrompt)
            put("extensions", JSONObject().apply {
                if (persona.samplingProfile.isNotBlank()) {
                    put("sampling_profile", persona.samplingProfile)
                }
                if (persona.controlVectors.isNotBlank()) {
                    put("control_vectors", persona.controlVectors)
                }
            })
        }

        return JSONObject().apply {
            put("spec", "chara_card_v2")
            put("spec_version", "2.0")
            put("data", data)
        }.toString(2)
    }

    /**
     * Import a Persona from JSON. Auto-detects TavernAI v1 and v2 formats.
     */
    fun importFromJson(json: String): Persona {
        val root = JSONObject(json)

        // Detect v2 format (has "spec" and "data" fields)
        return if (root.optString("spec") == "chara_card_v2" && root.has("data")) {
            importV2(root.getJSONObject("data"))
        } else if (root.has("data")) {
            // Some v2 cards omit spec but have data wrapper
            importV2(root.getJSONObject("data"))
        } else {
            // V1 format: fields are at root level
            importV1(root)
        }
    }

    private fun importV2(data: JSONObject): Persona {
        return Persona(
            name = data.optString("name", "Imported Character"),
            description = data.optString("description", ""),
            personality = data.optString("personality", ""),
            scenario = data.optString("scenario", ""),
            greeting = data.optString("first_mes", ""),
            alternateGreetings = jsonArrayToStringList(data.optJSONArray("alternate_greetings")),
            exampleMessages = data.optString("mes_example", ""),
            tags = jsonArrayToStringList(data.optJSONArray("tags")),
            creatorNotes = data.optString("creator_notes", ""),
            systemPrompt = data.optString("system_prompt", ""),
            samplingProfile = data.optJSONObject("extensions")?.optString("sampling_profile", "") ?: "",
            controlVectors = data.optJSONObject("extensions")?.optString("control_vectors", "") ?: ""
        )
    }

    private fun importV1(root: JSONObject): Persona {
        return Persona(
            name = root.optString("name", root.optString("char_name", "Imported Character")),
            description = root.optString("description", root.optString("char_persona", "")),
            personality = root.optString("personality", ""),
            scenario = root.optString("scenario", root.optString("world_scenario", "")),
            greeting = root.optString("first_mes", root.optString("char_greeting", "")),
            alternateGreetings = jsonArrayToStringList(root.optJSONArray("alternate_greetings")),
            exampleMessages = root.optString("mes_example", root.optString("example_dialogue", "")),
            tags = jsonArrayToStringList(root.optJSONArray("tags")),
            systemPrompt = root.optString("system_prompt", "")
        )
    }

    private fun jsonArrayToStringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            array.optString(i)?.takeIf { it.isNotBlank() }
        }
    }
}
