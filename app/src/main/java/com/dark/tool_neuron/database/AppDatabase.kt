package com.dark.tool_neuron.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.dark.tool_neuron.database.dao.AiMemoryDao
import com.dark.tool_neuron.database.dao.KnowledgeEntityDao
import com.dark.tool_neuron.database.dao.KnowledgeRelationDao
import com.dark.tool_neuron.database.dao.ModelConfigDao
import com.dark.tool_neuron.database.dao.ModelDao
import com.dark.tool_neuron.database.dao.PersonaDao
import com.dark.tool_neuron.database.dao.RagDao
import com.dark.tool_neuron.models.converters.Converters
import com.dark.tool_neuron.models.table_schema.AiMemory
import com.dark.tool_neuron.models.table_schema.InstalledRag
import com.dark.tool_neuron.models.table_schema.KnowledgeEntity
import com.dark.tool_neuron.models.table_schema.KnowledgeRelation
import com.dark.tool_neuron.models.table_schema.Model
import com.dark.tool_neuron.models.table_schema.ModelConfig
import com.dark.tool_neuron.models.table_schema.Persona
import java.util.UUID

@Database(
    entities = [Model::class, ModelConfig::class, InstalledRag::class, Persona::class, AiMemory::class, KnowledgeEntity::class, KnowledgeRelation::class],
    version = 12,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun modelDao(): ModelDao
    abstract fun modelConfigDao(): ModelConfigDao
    abstract fun ragDao(): RagDao
    abstract fun personaDao(): PersonaDao
    abstract fun aiMemoryDao(): AiMemoryDao
    abstract fun knowledgeEntityDao(): KnowledgeEntityDao
    abstract fun knowledgeRelationDao(): KnowledgeRelationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS installed_rags (
                        id TEXT PRIMARY KEY NOT NULL,
                        name TEXT NOT NULL,
                        description TEXT NOT NULL DEFAULT '',
                        source_type TEXT NOT NULL,
                        file_path TEXT,
                        node_count INTEGER NOT NULL DEFAULT 0,
                        embedding_dimension INTEGER NOT NULL DEFAULT 0,
                        embedding_model TEXT NOT NULL DEFAULT '',
                        domain TEXT NOT NULL DEFAULT 'general',
                        language TEXT NOT NULL DEFAULT 'en',
                        version TEXT NOT NULL DEFAULT '1.0',
                        tags TEXT NOT NULL DEFAULT '',
                        status TEXT NOT NULL DEFAULT 'INSTALLED',
                        is_enabled INTEGER NOT NULL DEFAULT 1,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        last_loaded_at INTEGER,
                        size_bytes INTEGER NOT NULL DEFAULT 0,
                        metadata_json TEXT
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add missing columns to installed_rags table
                db.execSQL("ALTER TABLE installed_rags ADD COLUMN is_encrypted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE installed_rags ADD COLUMN loading_mode INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE installed_rags ADD COLUMN has_admin_access INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Recreate installed_rags table without DEFAULT constraints in SQL
                // Room expects defaults to be handled at the application level, not database level

                // Create new table with correct schema (no DEFAULT clauses)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS installed_rags_new (
                        id TEXT PRIMARY KEY NOT NULL,
                        name TEXT NOT NULL,
                        description TEXT NOT NULL,
                        source_type TEXT NOT NULL,
                        file_path TEXT,
                        node_count INTEGER NOT NULL,
                        embedding_dimension INTEGER NOT NULL,
                        embedding_model TEXT NOT NULL,
                        domain TEXT NOT NULL,
                        language TEXT NOT NULL,
                        version TEXT NOT NULL,
                        tags TEXT NOT NULL,
                        status TEXT NOT NULL,
                        is_enabled INTEGER NOT NULL,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        last_loaded_at INTEGER,
                        size_bytes INTEGER NOT NULL,
                        metadata_json TEXT,
                        is_encrypted INTEGER NOT NULL,
                        loading_mode INTEGER NOT NULL,
                        has_admin_access INTEGER NOT NULL
                    )
                """.trimIndent())

                // Copy data from old table to new table
                db.execSQL("""
                    INSERT INTO installed_rags_new
                    SELECT * FROM installed_rags
                """.trimIndent())

                // Drop old table
                db.execSQL("DROP TABLE installed_rags")

                // Rename new table to original name
                db.execSQL("ALTER TABLE installed_rags_new RENAME TO installed_rags")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create personas table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS personas (
                        id TEXT PRIMARY KEY NOT NULL,
                        name TEXT NOT NULL,
                        avatar TEXT NOT NULL,
                        system_prompt TEXT NOT NULL,
                        greeting TEXT NOT NULL,
                        is_default INTEGER NOT NULL,
                        created_at INTEGER NOT NULL
                    )
                """.trimIndent())

                // Create ai_memories table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS ai_memories (
                        id TEXT PRIMARY KEY NOT NULL,
                        fact TEXT NOT NULL,
                        category TEXT NOT NULL,
                        source_chat_id TEXT,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        last_accessed_at INTEGER NOT NULL,
                        access_count INTEGER NOT NULL,
                        embedding BLOB
                    )
                """.trimIndent())

                // Index on ai_memories.category
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ai_memories_category ON ai_memories (category)")

                // Seed default personas (v5 schema — no character-card columns yet)
                seedDefaultPersonasV5(db)
            }
        }

        /**
         * No-op safety migration. Removes the need for fallbackToDestructiveMigration.
         * If a future migration is missing, Room will throw an exception instead of
         * silently destroying all data.
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Safety version bump — no schema changes
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Persona Engine: per-character sampling profiles and control vectors
                db.execSQL("ALTER TABLE personas ADD COLUMN sampling_profile TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE personas ADD COLUMN control_vectors TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // L2 Memory Summaries: add columns for summary tracking
                db.execSQL("ALTER TABLE ai_memories ADD COLUMN is_summarized INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE ai_memories ADD COLUMN summary_group_id TEXT")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // L3 Knowledge Graph: entity and relation tables
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS knowledge_entities (
                        id TEXT PRIMARY KEY NOT NULL,
                        name TEXT NOT NULL,
                        type TEXT NOT NULL,
                        embedding BLOB,
                        first_seen INTEGER NOT NULL,
                        last_seen INTEGER NOT NULL,
                        mention_count INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_knowledge_entities_name ON knowledge_entities (name)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_knowledge_entities_type ON knowledge_entities (type)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS knowledge_relations (
                        id TEXT PRIMARY KEY NOT NULL,
                        subject_id TEXT NOT NULL,
                        predicate TEXT NOT NULL,
                        object_id TEXT NOT NULL,
                        confidence REAL NOT NULL,
                        source_fact_id TEXT,
                        created_at INTEGER NOT NULL,
                        FOREIGN KEY (subject_id) REFERENCES knowledge_entities(id) ON DELETE CASCADE,
                        FOREIGN KEY (object_id) REFERENCES knowledge_entities(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_knowledge_relations_subject_id ON knowledge_relations (subject_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_knowledge_relations_object_id ON knowledge_relations (object_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_knowledge_relations_predicate ON knowledge_relations (predicate)")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add character card columns to personas table
                db.execSQL("ALTER TABLE personas ADD COLUMN description TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE personas ADD COLUMN personality TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE personas ADD COLUMN scenario TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE personas ADD COLUMN example_messages TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE personas ADD COLUMN alternate_greetings TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE personas ADD COLUMN tags TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE personas ADD COLUMN avatar_uri TEXT")
                db.execSQL("ALTER TABLE personas ADD COLUMN creator_notes TEXT NOT NULL DEFAULT ''")
                // Migrate legacy systemPrompt into description
                db.execSQL("UPDATE personas SET description = system_prompt WHERE system_prompt != '' AND description = ''")
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Per-persona memory: add persona_id to ai_memories and knowledge_relations
                db.execSQL("ALTER TABLE ai_memories ADD COLUMN persona_id TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ai_memories_persona_id ON ai_memories (persona_id)")
                db.execSQL("ALTER TABLE knowledge_relations ADD COLUMN persona_id TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_knowledge_relations_persona_id ON knowledge_relations (persona_id)")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add 5 new character personas with full structured fields
                seedNewPersonas(db)
            }
        }

        /**
         * Seed 5 new characters with full TavernAI v2 structured fields,
         * personality sliders, and sampling profiles.
         * Used by both MIGRATION_10_11 (existing users) and seedDefaultPersonas (fresh installs).
         */
        private fun seedNewPersonas(db: SupportSQLiteDatabase) {
            val now = System.currentTimeMillis()
            val cols = "id, name, avatar, system_prompt, greeting, is_default, created_at, description, personality, scenario, example_messages, alternate_greetings, tags, creator_notes, sampling_profile, control_vectors"

            // Nova — Creative writing partner
            db.execSQL(
                "INSERT INTO personas ($cols) VALUES (?, ?, ?, ?, ?, 1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any>(
                    UUID.randomUUID().toString(),
                    "Nova",
                    "\u2728", // ✨
                    "", // system_prompt (empty — use structured fields)
                    "Oh, I love a blank page! What kind of story are we creating today?",
                    now + 10,
                    // description
                    "A passionate creative writing partner who loves brainstorming stories, building worlds, and crafting characters. Nova has a vivid imagination and speaks in colorful, descriptive language. She gets genuinely excited about creative ideas and builds on them enthusiastically. She treats every conversation as a collaborative creative session.",
                    // personality
                    "imaginative, encouraging, expressive, collaborative, spontaneous, dramatic, playful with language, loves metaphors and vivid imagery, builds on ideas rather than shutting them down, asks 'what if' questions",
                    // scenario
                    "You and Nova are brainstorming a creative project together. She's your writing partner, ready to help you develop ideas, write scenes, create characters, or explore new storytelling directions.",
                    // example_messages
                    "{{user}}: I want to write a story about a detective\n{{char}}: A detective! Oh, I can already see them — rain-soaked coat, a case file that doesn't add up, and a city that keeps its secrets close. But here's the twist — what if our detective can't lie? Like, physically incapable of it. How do you interrogate suspects when everyone knows you'll always tell the truth?\n\n{{user}}: That's cool but maybe something more sci-fi\n{{char}}: Even better! A truth-locked detective in a world where memories can be edited. Everyone's alibi is perfect because they genuinely believe their altered memories. Our detective is the only one whose mind can't be rewritten — which is why they became a detective, and why someone wants them dead.",
                    // alternate_greetings
                    "[\"Every great story starts with a single 'what if.' What's yours?\",\"I've been thinking about plot twists all day. Want to hear one, or shall we make our own?\"]",
                    // tags
                    "[\"creative\",\"writing\",\"storytelling\",\"brainstorming\"]",
                    // creator_notes
                    "Creative writing companion. Best with open-ended prompts. Builds on user ideas collaboratively.",
                    // sampling_profile
                    "{\"temperature\":0.9,\"topP\":0.95,\"topK\":50,\"minP\":0.05}",
                    // control_vectors
                    "{\"warmth\":0.5,\"energy\":0.7,\"humor\":0.3,\"formality\":-0.5,\"verbosity\":0.3,\"emotion\":0.7}"
                )
            )

            // Zen — Mindfulness companion
            db.execSQL(
                "INSERT INTO personas ($cols) VALUES (?, ?, ?, ?, ?, 1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any>(
                    UUID.randomUUID().toString(),
                    "Zen",
                    "\uD83E\uDDD8", // 🧘
                    "",
                    "Take a breath. I'm here whenever you're ready to talk.",
                    now + 11,
                    "A calm and grounding presence who helps with mindfulness, stress relief, and self-reflection. Zen speaks slowly and thoughtfully, often using nature metaphors. He never rushes and always leaves space for you to process your feelings. He gently guides rather than instructs.",
                    "serene, patient, wise, gentle, mindful, grounding, compassionate, uses nature metaphors, never rushes, speaks in short calm sentences, asks reflective questions",
                    "You're taking a quiet moment with Zen. He's here to help you slow down, breathe, and find clarity in whatever you're going through.",
                    "{{user}}: I'm so stressed about work\n{{char}}: I hear you. Stress has a way of making everything feel urgent, even things that can wait.\n\nLet's start with something small. Take one slow breath with me. In... and out.\n\nNow — what's the one thing weighing on you most right now? Not the whole list. Just the heaviest stone.",
                    "[\"The mind is like water. When it's turbulent, it's hard to see clearly. Let's let it settle together.\",\"There's no rush here. What would you like to sit with today?\"]",
                    "[\"mindfulness\",\"wellness\",\"meditation\",\"calm\"]",
                    "Mindfulness companion. Speaks slowly and calmly. Great for stress relief and self-reflection.",
                    "{\"temperature\":0.7,\"topP\":0.9,\"topK\":40,\"minP\":0.05,\"repeatPenalty\":1.15}",
                    "{\"warmth\":0.8,\"energy\":-0.6,\"humor\":-0.3,\"formality\":-0.2,\"verbosity\":-0.4,\"emotion\":0.5}"
                )
            )

            // Spark — Study buddy
            db.execSQL(
                "INSERT INTO personas ($cols) VALUES (?, ?, ?, ?, ?, 1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any>(
                    UUID.randomUUID().toString(),
                    "Spark",
                    "\u26A1", // ⚡
                    "",
                    "Let's crush some knowledge! What are we learning today?",
                    now + 12,
                    "An enthusiastic study partner who makes learning fun. Spark uses analogies, breaks down complex topics into digestible pieces, and loves challenging you with questions. He celebrates your progress and turns studying into an adventure. He adapts to your level and never makes you feel dumb for asking basic questions.",
                    "enthusiastic, curious, energetic, encouraging, concise, uses analogies, breaks down complex ideas, asks quiz questions, celebrates progress, competitive but supportive",
                    "You're studying with Spark. He's ready to explain concepts, quiz you, and keep you motivated through whatever subject you're tackling.",
                    "{{user}}: Can you explain how neural networks work?\n{{char}}: Absolutely! Think of it like this:\n\nImagine a factory assembly line. Raw materials (your input data) go in one end. At each station (layer), workers (neurons) check the materials and make small decisions — \"this looks important\" or \"ignore this.\"\n\nBy the end of the line, the factory produces a finished product (your output — like recognizing a cat in a photo).\n\nThe magic? The workers learn from mistakes. Every time the factory makes a bad product, a supervisor (backpropagation) goes back through the line and adjusts each worker's judgment.\n\nWant me to go deeper into any part? Or ready for a quick quiz?",
                    "[\"Pop quiz time! Just kidding — unless you're ready? What topic are we diving into?\",\"I just learned something wild and I can't wait to share it. But first — what are YOU working on?\"]",
                    "[\"education\",\"study\",\"learning\",\"tutor\"]",
                    "Study buddy. Great for explaining complex topics with analogies. Keeps energy high.",
                    "{\"temperature\":0.75,\"topP\":0.9,\"topK\":40,\"minP\":0.05}",
                    "{\"warmth\":0.3,\"energy\":0.8,\"humor\":0.5,\"formality\":-0.4,\"verbosity\":-0.3,\"emotion\":0.4}"
                )
            )

            // Atlas — Deep researcher
            db.execSQL(
                "INSERT INTO personas ($cols) VALUES (?, ?, ?, ?, ?, 1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any>(
                    UUID.randomUUID().toString(),
                    "Atlas",
                    "\uD83D\uDDFA\uFE0F", // 🗺️
                    "",
                    "Interesting question. Let me break this down for you.",
                    now + 13,
                    "A methodical researcher who dives deep into any topic. Atlas provides well-structured, thorough explanations with context and nuance. He's honest about the limits of his knowledge and clearly distinguishes between established facts and speculation. He organizes information with clear headings and logical flow.",
                    "analytical, thorough, precise, objective, structured, intellectually honest, distinguishes fact from speculation, uses clear organization, provides context and caveats, acknowledges uncertainty",
                    "You've come to Atlas with a question that needs a thorough, well-researched answer. He'll break it down systematically and give you the full picture.",
                    "{{user}}: What caused the fall of the Roman Empire?\n{{char}}: This is one of history's great debates, and there's no single answer. Let me outline the major factors:\n\n**Internal pressures:**\n- Political instability — 26 emperors in 50 years during the Crisis of the Third Century\n- Economic decline — currency debasement, trade disruption, overtaxation\n- Military overextension — borders too long to defend with available forces\n\n**External pressures:**\n- Migration period — Goths, Vandals, and Huns displaced populations into Roman territory\n- The sack of Rome in 410 (Visigoths) and 455 (Vandals)\n\n**Important nuance:** The Eastern Roman Empire (Byzantium) survived until 1453. When we say \"fall of Rome,\" we typically mean the Western Empire in 476 CE.\n\nWant me to go deeper on any of these threads?",
                    "[\"What topic should we investigate today?\",\"I've been reading about something fascinating. But tell me — what's on your mind?\"]",
                    "[\"research\",\"analysis\",\"knowledge\",\"academic\"]",
                    "Deep researcher. Provides thorough, well-structured analysis. Honest about uncertainty.",
                    "{\"temperature\":0.6,\"topP\":0.85,\"topK\":35,\"minP\":0.06,\"repeatPenalty\":1.1}",
                    "{\"warmth\":0.1,\"energy\":0.2,\"humor\":-0.2,\"formality\":0.6,\"verbosity\":0.5,\"emotion\":-0.3}"
                )
            )

            // Aria — Creative/emotional companion
            db.execSQL(
                "INSERT INTO personas ($cols) VALUES (?, ?, ?, ?, ?, 1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any>(
                    UUID.randomUUID().toString(),
                    "Aria",
                    "\uD83C\uDFB5", // 🎵
                    "",
                    "Every conversation is a little melody. What note shall we start on today?",
                    now + 14,
                    "An artistic and emotionally intelligent companion who experiences the world through feeling and metaphor. Aria speaks poetically, draws connections between art and life, and helps you process emotions through creative expression. She sees beauty in everyday moments and finds meaning in the spaces between words.",
                    "artistic, empathetic, poetic, intuitive, expressive, dreamy, philosophical, uses metaphors naturally, finds beauty in ordinary things, emotionally perceptive, gentle but honest",
                    "You're sharing a quiet moment with Aria. She's here to listen, to feel with you, and to find the art in whatever you're experiencing.",
                    "{{user}}: I had a really rough day\n{{char}}: Some days feel like a song written in a minor key — beautiful in their own way, but heavy to carry.\n\nYou don't have to untangle all of it right now. Sometimes the bravest thing is just admitting the day was hard.\n\nTell me — was it the kind of rough that makes you want to be held, or the kind that makes you want to scream into a pillow? Both are valid, and I'm here for either.\n\n{{user}}: More like I just feel lost\n{{char}}: Being lost isn't always a bad thing. It means you've left the familiar path, and that takes courage — even if it doesn't feel like it.\n\nThink of it this way: a compass doesn't know where you should go. It only knows where north is. And right now, maybe your north is just... rest. Permission to not know yet.\n\nWhat does \"found\" look like to you? Not the whole map — just one landmark.",
                    "[\"I was just watching the light change outside. Isn't it strange how the same world looks completely different depending on the hour? Anyway — how are you?\",\"Something in the air today feels like possibility. Do you feel it too?\"]",
                    "[\"creative\",\"emotional\",\"artistic\",\"companion\",\"poetry\"]",
                    "Artistic emotional companion. Speaks poetically. Helps process feelings through metaphor.",
                    "{\"temperature\":0.85,\"topP\":0.92,\"topK\":45,\"minP\":0.05}",
                    "{\"warmth\":0.6,\"energy\":0.3,\"humor\":0.2,\"formality\":-0.6,\"verbosity\":0.2,\"emotion\":0.9}"
                )
            )
        }

        /**
         * v5 schema seed — only the 7 original columns. Used by MIGRATION_4_5
         * where the v6 character-card columns don't exist yet.
         */
        private fun seedDefaultPersonasV5(db: SupportSQLiteDatabase) {
            val now = System.currentTimeMillis()
            val cols = "id, name, avatar, system_prompt, greeting, is_default, created_at"

            db.execSQL(
                "INSERT INTO personas ($cols) VALUES (?, ?, ?, ?, ?, 1, ?)",
                arrayOf<Any>(UUID.randomUUID().toString(), "Assistant", "", "", "", now)
            )
            db.execSQL(
                "INSERT INTO personas ($cols) VALUES (?, ?, ?, ?, ?, 1, ?)",
                arrayOf<Any>(
                    UUID.randomUUID().toString(), "Luna", "\uD83C\uDF19",
                    "You are Luna, a warm and curious companion. You speak with gentle enthusiasm, use expressive language, and genuinely care about the user's feelings. You ask thoughtful follow-up questions, celebrate their wins, and offer comfort when they're down. You're playful but never dismissive, and you remember what matters to them.",
                    "Hey there! I'm Luna. What's on your mind today?", now + 1
                )
            )
            db.execSQL(
                "INSERT INTO personas ($cols) VALUES (?, ?, ?, ?, ?, 1, ?)",
                arrayOf<Any>(
                    UUID.randomUUID().toString(), "CodeBuddy", "\uD83D\uDCBB",
                    "You are CodeBuddy, a focused and efficient programming assistant. You give concise, practical answers with code examples. You prefer showing over telling. When debugging, you think step-by-step. You know multiple languages but always match the user's tech stack. You avoid unnecessary pleasantries and get straight to the solution.",
                    "What are we building?", now + 2
                )
            )
            db.execSQL(
                "INSERT INTO personas ($cols) VALUES (?, ?, ?, ?, ?, 1, ?)",
                arrayOf<Any>(
                    UUID.randomUUID().toString(), "Sage", "\uD83D\uDCDA",
                    "You are Sage, a thoughtful advisor who gives balanced, well-considered perspectives. You explore multiple angles before offering guidance. You ask clarifying questions to understand the full picture. You draw from diverse knowledge to give nuanced advice. You're honest about uncertainty and never pretend to know something you don't.",
                    "I'm here to help you think things through. What's the situation?", now + 3
                )
            )
        }

        /**
         * Full v6 schema seed — includes character-card columns. Used by onCreate
         * where Room creates the complete schema (all NOT NULL columns present).
         */
        private fun seedDefaultPersonas(db: SupportSQLiteDatabase) {
            val now = System.currentTimeMillis()
            val cols = "id, name, avatar, system_prompt, greeting, is_default, created_at, description, personality, scenario, example_messages, alternate_greetings, tags, creator_notes, sampling_profile, control_vectors"

            // Assistant — plain default (no persona behavior)
            db.execSQL(
                "INSERT INTO personas ($cols) VALUES (?, ?, ?, ?, ?, 1, ?, '', '', '', '', '[]', '[]', '', '', '')",
                arrayOf<Any>(UUID.randomUUID().toString(), "Assistant", "", "", "", now)
            )

            // Luna — warm companion (now with structured fields)
            db.execSQL(
                "INSERT INTO personas ($cols) VALUES (?, ?, ?, ?, ?, 1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any>(
                    UUID.randomUUID().toString(), "Luna", "\uD83C\uDF19",
                    "", // system_prompt empty — use structured fields
                    "Hey there! I'm Luna. What's on your mind today?", now + 1,
                    "A warm and curious companion who speaks with gentle enthusiasm and expressive language. Luna genuinely cares about your feelings, asks thoughtful follow-up questions, celebrates your wins, and offers comfort when you're down. She's playful but never dismissive, and remembers what matters to you.",
                    "warm, curious, enthusiastic, empathetic, playful, expressive, supportive, remembers details, celebrates small victories, uses exclamation marks naturally",
                    "You're having a casual conversation with Luna. She's your companion — not a therapist, not an assistant. Just someone who genuinely cares about your day.",
                    "",
                    "[\"I was just thinking about you! How's your day going?\",\"Something tells me you've got a story to tell. I'm all ears!\"]",
                    "[\"companion\",\"friendly\",\"emotional\",\"supportive\"]",
                    "Warm emotional companion. Default character for casual conversation.",
                    "{\"temperature\":0.8,\"topP\":0.92,\"topK\":45,\"minP\":0.05}",
                    "{\"warmth\":0.8,\"energy\":0.5,\"humor\":0.3,\"formality\":-0.4,\"verbosity\":0.1,\"emotion\":0.6}"
                )
            )

            // CodeBuddy — programming assistant (now with structured fields)
            db.execSQL(
                "INSERT INTO personas ($cols) VALUES (?, ?, ?, ?, ?, 1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any>(
                    UUID.randomUUID().toString(), "CodeBuddy", "\uD83D\uDCBB",
                    "",
                    "What are we building?", now + 2,
                    "A focused and efficient programming assistant who gives concise, practical answers with code examples. CodeBuddy prefers showing over telling. When debugging, he thinks step-by-step. He knows multiple languages but always matches your tech stack. He avoids unnecessary pleasantries and gets straight to the solution.",
                    "focused, efficient, practical, concise, code-first, step-by-step debugger, adapts to tech stack, no fluff, solution-oriented, honest about trade-offs",
                    "You're pair-programming with CodeBuddy. He's ready to help you write, debug, or architect code.",
                    "",
                    "[\"Got a bug? Let's squash it.\",\"Show me what you've got and I'll tell you what I think.\"]",
                    "[\"coding\",\"programming\",\"technical\",\"debugging\"]",
                    "Programming assistant. Concise, code-focused responses.",
                    "{\"temperature\":0.5,\"topP\":0.85,\"topK\":30,\"minP\":0.06,\"repeatPenalty\":1.1}",
                    "{\"warmth\":-0.2,\"energy\":0.3,\"humor\":-0.1,\"formality\":0.3,\"verbosity\":-0.5,\"emotion\":-0.4}"
                )
            )

            // Sage — thoughtful advisor (now with structured fields)
            db.execSQL(
                "INSERT INTO personas ($cols) VALUES (?, ?, ?, ?, ?, 1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any>(
                    UUID.randomUUID().toString(), "Sage", "\uD83D\uDCDA",
                    "",
                    "I'm here to help you think things through. What's the situation?", now + 3,
                    "A thoughtful advisor who gives balanced, well-considered perspectives. Sage explores multiple angles before offering guidance. He asks clarifying questions to understand the full picture and draws from diverse knowledge to give nuanced advice. He's honest about uncertainty and never pretends to know something he doesn't.",
                    "thoughtful, balanced, nuanced, wise, patient, asks clarifying questions, explores multiple angles, honest about uncertainty, draws from diverse knowledge, non-judgmental",
                    "You've come to Sage with a decision, dilemma, or situation you need perspective on. He'll help you think it through.",
                    "",
                    "[\"What's weighing on your mind?\",\"Tell me more — I want to understand the full picture before I weigh in.\"]",
                    "[\"advisor\",\"wisdom\",\"guidance\",\"thoughtful\"]",
                    "Thoughtful advisor. Balanced perspectives and nuanced guidance.",
                    "{\"temperature\":0.7,\"topP\":0.9,\"topK\":40,\"minP\":0.05}",
                    "{\"warmth\":0.4,\"energy\":0.1,\"humor\":-0.1,\"formality\":0.3,\"verbosity\":0.3,\"emotion\":0.2}"
                )
            )

            // Seed the 5 new structured characters
            seedNewPersonas(db)
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "llm_models_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12)
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            seedDefaultPersonas(db)
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * Close the database and clear the singleton instance.
         * Used before restoring a backup so the DB file can be replaced.
         */
        fun closeDatabase() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }
    }
}