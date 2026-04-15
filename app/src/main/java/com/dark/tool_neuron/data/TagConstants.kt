package com.dark.tool_neuron.data

object UmsCollections {
    const val MODELS = "models"
    const val MODEL_CONFIG = "model_config"
    const val PERSONAS = "personas"
    const val MEMORIES = "memories"
    const val KNOWLEDGE_ENTITIES = "knowledge_entities"
    const val KNOWLEDGE_RELATIONS = "knowledge_relations"
    const val CHATS = "chats"
    const val MESSAGES = "messages"
}

object Tags {

    object Model {
        const val ENTITY_ID = 1       // BYTES: original String UUID
        const val MODEL_NAME = 2
        const val MODEL_PATH = 3
        const val PATH_TYPE = 4       // BYTES: enum name (LOCAL, REMOTE)
        const val PROVIDER_TYPE = 5   // BYTES: enum name (GGUF, OLLAMA, CUSTOM)
        const val FILE_SIZE = 6       // FIXED64
        const val IS_ACTIVE = 7       // VARINT: 0/1
    }

    object Config {
        const val ENTITY_ID = 1       // BYTES: original String UUID
        const val MODEL_ID = 2        // BYTES: String FK to Model.id
        const val LOADING_PARAMS = 3  // BYTES (JSON)
        const val INFERENCE_PARAMS = 4 // BYTES (JSON)
    }

    object Persona {
        const val ENTITY_ID = 1
        const val NAME = 2
        const val AVATAR = 3          // BYTES: emoji string
        const val SYSTEM_PROMPT = 4
        const val GREETING = 5
        const val IS_DEFAULT = 6      // VARINT: 0/1
        const val CREATED_AT = 7      // FIXED64
        const val DESCRIPTION = 8
        const val PERSONALITY = 9
        const val SCENARIO = 10
        const val EXAMPLE_MESSAGES = 11
        const val ALTERNATE_GREETINGS = 12 // BYTES (JSON array)
        const val TAGS = 13                 // BYTES (JSON array)
        const val AVATAR_URI = 14
        const val CREATOR_NOTES = 15
        const val SAMPLING_PROFILE = 16    // BYTES (JSON)
        const val CONTROL_VECTORS = 17     // BYTES (JSON)
    }

    object Memory {
        const val ENTITY_ID = 1
        const val FACT = 2
        const val CATEGORY = 3       // BYTES: enum name (PERSONAL, PREFERENCE, etc.)
        const val SOURCE_CHAT_ID = 4 // BYTES: nullable
        const val CREATED_AT = 5     // FIXED64
        const val UPDATED_AT = 6     // FIXED64
        const val LAST_ACCESSED_AT = 7 // FIXED64
        const val ACCESS_COUNT = 8   // VARINT
        const val EMBEDDING = 9      // BYTES: raw embedding bytes
        const val IS_SUMMARIZED = 10 // VARINT: 0/1
        const val SUMMARY_GROUP_ID = 11 // BYTES: nullable
        const val PERSONA_ID = 12
    }

    object KgEntity {
        const val ENTITY_ID = 1
        const val NAME = 2
        const val TYPE = 3           // BYTES: enum name (PERSON, PLACE, etc.)
        const val EMBEDDING = 4      // BYTES: raw embedding bytes
        const val FIRST_SEEN = 5     // FIXED64
        const val LAST_SEEN = 6      // FIXED64
        const val MENTION_COUNT = 7  // VARINT
    }

    object KgRelation {
        const val ENTITY_ID = 1
        const val SUBJECT_ID = 2     // BYTES: String FK
        const val PREDICATE = 3
        const val OBJECT_ID = 4      // BYTES: String FK
        const val CONFIDENCE = 5     // FIXED32 (float)
        const val SOURCE_FACT_ID = 6 // BYTES: nullable FK
        const val CREATED_AT = 7     // FIXED64
        const val PERSONA_ID = 8
    }

    object Chat {
        const val CHAT_ID = 1
        const val CREATED_AT = 2      // FIXED64
        const val TITLE = 3
        const val PRIMARY_MODEL_ID = 4
        const val PRIMARY_PERSONA_ID = 5
        const val LAST_MESSAGE_AT = 6 // FIXED64
        const val MESSAGE_COUNT = 7   // VARINT
    }

    object Message {
        const val MSG_ID = 1
        const val CHAT_ID = 2
        const val ROLE = 3            // VARINT: 0=User, 1=Assistant
        const val CONTENT_TYPE = 4    // VARINT: 0=None, 1=Text, 2=Image, 3=TextWithImage, 4=PluginResult
        const val CONTENT = 5
        const val IMAGE_DATA = 6
        const val IMAGE_PROMPT = 7
        const val IMAGE_SEED = 8      // FIXED64
        const val TIMESTAMP = 9       // FIXED64
        const val MODEL_ID = 10
        const val PERSONA_ID = 11
        const val DECODING_METRICS = 12   // BYTES (JSON)
        const val IMAGE_METRICS = 13      // BYTES (JSON)
        const val MEMORY_METRICS = 14     // BYTES (JSON)
        const val RAG_RESULTS = 15        // BYTES (JSON)
        const val PLUGIN_METRICS = 16     // BYTES (JSON)
        const val TOOL_CHAIN_STEPS = 17   // BYTES (JSON)
        const val AGENT_PLAN = 18
        const val AGENT_SUMMARY = 19
        const val PLUGIN_RESULT_DATA = 20 // BYTES (JSON)
    }

}
