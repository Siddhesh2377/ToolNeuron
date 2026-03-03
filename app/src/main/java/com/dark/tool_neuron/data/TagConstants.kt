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
        const val MODEL_NAME = 1
        const val MODEL_PATH = 2
        const val PATH_TYPE = 3       // VARINT: 0=Local, 1=SAF
        const val PROVIDER_TYPE = 4   // VARINT: 0=GGUF, 1=Diffusion, 2=TTS
        const val FILE_SIZE = 5       // FIXED64
        const val IS_ACTIVE = 6       // VARINT: 0/1
    }

    object Config {
        const val MODEL_ID = 1        // VARINT (foreign key)
        const val LOADING_PARAMS = 2  // BYTES (JSON)
        const val INFERENCE_PARAMS = 3 // BYTES (JSON)
    }

    object Persona {
        const val NAME = 1
        const val SYSTEM_PROMPT = 2
        const val DESCRIPTION = 3
        const val PERSONALITY = 4
        const val SCENARIO = 5
        const val EXAMPLE_MESSAGES = 6
        const val ALTERNATE_GREETINGS = 7  // BYTES (JSON)
        const val TAGS = 8                  // BYTES (JSON)
        const val AVATAR_URI = 9
        const val CREATOR_NOTES = 10
        const val SAMPLING_PROFILE = 11    // BYTES (JSON)
        const val CONTROL_VECTORS = 12     // BYTES (JSON)
        const val CREATED_AT = 13          // FIXED64
    }

    object Memory {
        const val FACT = 1
        const val CATEGORY = 2
        const val IMPORTANCE = 3      // FIXED32 (float)
        const val CREATED_AT = 4      // FIXED64
        const val LAST_ACCESSED_AT = 5 // FIXED64
        const val ACCESS_COUNT = 6    // VARINT
        const val SOURCE_CONTEXT = 7
        const val EMBEDDING = 8       // BYTES (JSON)
        const val IS_SUMMARIZED = 9   // VARINT: 0/1
        const val SUMMARY_GROUP_ID = 10 // VARINT
        const val PERSONA_ID = 11
    }

    object KgEntity {
        const val NAME = 1
        const val TYPE = 2
        const val DESCRIPTION = 3
        const val ATTRIBUTES = 4
        const val CREATED_AT = 5      // FIXED64
        const val LAST_UPDATED_AT = 6 // FIXED64
        const val MENTION_COUNT = 7   // VARINT
    }

    object KgRelation {
        const val SOURCE_ENTITY_ID = 1 // VARINT
        const val TARGET_ENTITY_ID = 2 // VARINT
        const val RELATION_TYPE = 3
        const val WEIGHT = 4          // FIXED32 (float)
        const val CONTEXT = 5
        const val CREATED_AT = 6      // FIXED64
        const val PERSONA_ID = 7
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
