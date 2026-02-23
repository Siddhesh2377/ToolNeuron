package com.dark.tool_neuron.models.table_schema

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "personas")
data class Persona(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "avatar")
    val avatar: String = "",

    @ColumnInfo(name = "system_prompt")
    val systemPrompt: String = "",

    @ColumnInfo(name = "greeting")
    val greeting: String = "",

    @ColumnInfo(name = "is_default")
    val isDefault: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    // Character card fields
    @ColumnInfo(name = "description")
    val description: String = "",

    @ColumnInfo(name = "personality")
    val personality: String = "",

    @ColumnInfo(name = "scenario")
    val scenario: String = "",

    @ColumnInfo(name = "example_messages")
    val exampleMessages: String = "",

    @ColumnInfo(name = "alternate_greetings")
    val alternateGreetings: List<String> = emptyList(),

    @ColumnInfo(name = "tags")
    val tags: List<String> = emptyList(),

    @ColumnInfo(name = "avatar_uri")
    val avatarUri: String? = null,

    @ColumnInfo(name = "creator_notes")
    val creatorNotes: String = "",

    // Persona Engine: per-character sampling profile (JSON)
    @ColumnInfo(name = "sampling_profile")
    val samplingProfile: String = "",

    // Persona Engine: control vectors (JSON array of {path, strength})
    @ColumnInfo(name = "control_vectors")
    val controlVectors: String = ""
) {
    /**
     * Build the effective system prompt from structured character card fields.
     * Falls back to legacy [systemPrompt] if no structured fields are populated.
     */
    fun buildEffectiveSystemPrompt(): String {
        if (description.isNotBlank() || personality.isNotBlank() || scenario.isNotBlank()) {
            return buildString {
                // Clear identity framing so the model knows who it is vs the user
                append("You are {{char}}. You must stay in character as {{char}} at all times. The person you are talking to is {{user}} — never call them {{char}}.")
                append("\n\n")
                if (description.isNotBlank()) {
                    append("About {{char}}:\n")
                    append(description)
                }
                if (personality.isNotBlank()) {
                    append("\n\n")
                    append("{{char}}'s personality: ")
                    append(personality)
                }
                if (scenario.isNotBlank()) {
                    append("\n\n")
                    append("Scenario: ")
                    append(scenario)
                }
                if (exampleMessages.isNotBlank()) {
                    append("\n\n")
                    append("Example dialogue:\n")
                    append(exampleMessages)
                }
                append("\n\nIMPORTANT: Keep responses natural and proportional. Reply to casual messages (greetings, short questions) with 1-3 sentences. Only give longer responses when the topic genuinely requires depth.")
            }
        }
        // Legacy fallback: wrap raw systemPrompt with identity framing too
        if (systemPrompt.isNotBlank()) {
            return "You are {{char}}. Stay in character. The person you are talking to is {{user}} — never call them {{char}}.\n\n$systemPrompt"
        }
        return systemPrompt
    }

    /**
     * Build a short reinforcement directive injected AFTER chat history,
     * right before the model generates. This is the most influential position
     * for small models (research: SillyTavern "Post-History Instructions").
     */
    fun buildPostHistoryInstruction(): String {
        val traits = personality.takeIf { it.isNotBlank() }
            ?: description.take(300).takeIf { it.isNotBlank() }
            ?: return ""
        // Keep up to 20 traits for richer character reinforcement on small models.
        // Research shows post-history is the MOST influential position for character
        // consistency — 6 traits was too aggressive for 0.5B-3B models.
        val compressed = traits.split(Regex("[,;.\\n]+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .take(20)
            .joinToString(", ")
        return buildString {
            append("[Write {{char}}'s next response. Stay in character as {{char}}.")
            append(" {{char}} traits: $compressed.")
            if (scenario.isNotBlank()) {
                append(" Current scenario: ${scenario.take(150)}.")
            }
            append(" Match your response length to the conversation — short casual messages deserve short replies (1-2 sentences), not essays.")
            append(" Never write actions or dialogue for {{user}}. Never refer to {{user}} as {{char}}.]")
        }
    }

    /**
     * Apply {{char}} and {{user}} template substitutions.
     */
    fun applyTemplateVars(text: String, userName: String = "User"): String {
        return text
            .replace("{{char}}", name)
            .replace("{{user}}", userName)
    }
}
