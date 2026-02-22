package com.dark.tool_neuron.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.dark.tool_neuron.database.dao.AiMemoryDao
import com.dark.tool_neuron.database.dao.McpServerDao
import com.dark.tool_neuron.database.dao.ModelConfigDao
import com.dark.tool_neuron.database.dao.ModelDao
import com.dark.tool_neuron.database.dao.PersonaDao
import com.dark.tool_neuron.database.dao.RagDao
import com.dark.tool_neuron.models.converters.Converters
import com.dark.tool_neuron.models.table_schema.AiMemory
import com.dark.tool_neuron.models.table_schema.InstalledRag
import com.dark.tool_neuron.models.table_schema.McpServer
import com.dark.tool_neuron.models.table_schema.Model
import com.dark.tool_neuron.models.table_schema.ModelConfig
import com.dark.tool_neuron.models.table_schema.Persona
import java.util.UUID

@Database(
    entities = [Model::class, ModelConfig::class, InstalledRag::class, McpServer::class, Persona::class, AiMemory::class],
    version = 7,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun modelDao(): ModelDao
    abstract fun modelConfigDao(): ModelConfigDao
    abstract fun ragDao(): RagDao
    abstract fun mcpServerDao(): McpServerDao
    abstract fun personaDao(): PersonaDao
    abstract fun aiMemoryDao(): AiMemoryDao

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
                db.execSQL("ALTER TABLE installed_rags ADD COLUMN is_encrypted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE installed_rags ADD COLUMN loading_mode INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE installed_rags ADD COLUMN has_admin_access INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
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

                db.execSQL("""
                    INSERT INTO installed_rags_new
                    SELECT * FROM installed_rags
                """.trimIndent())

                db.execSQL("DROP TABLE installed_rags")
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

                db.execSQL("CREATE INDEX IF NOT EXISTS index_ai_memories_category ON ai_memories (category)")
                seedDefaultPersonasV5(db)
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
                db.execSQL("UPDATE personas SET description = system_prompt WHERE system_prompt != '' AND description = ''")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create mcp_servers table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS mcp_servers (
                        id TEXT PRIMARY KEY NOT NULL,
                        name TEXT NOT NULL,
                        url TEXT NOT NULL,
                        transportType TEXT NOT NULL,
                        apiKey TEXT,
                        isEnabled INTEGER NOT NULL,
                        lastError TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        lastConnectedAt INTEGER,
                        description TEXT NOT NULL,
                        customHeadersJson TEXT,
                        isLocal INTEGER NOT NULL DEFAULT 0,
                        sourceStoreId TEXT
                    )
                """.trimIndent())
            }
        }

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

        private fun seedDefaultPersonas(db: SupportSQLiteDatabase) {
            val now = System.currentTimeMillis()
            val cols = "id, name, avatar, system_prompt, greeting, is_default, created_at, description, personality, scenario, example_messages, alternate_greetings, tags, creator_notes"
            val placeholders = "?, ?, ?, ?, ?, 1, ?, '', '', '', '', '[]', '[]', ''"

            db.execSQL(
                "INSERT INTO personas ($cols) VALUES ($placeholders)",
                arrayOf<Any>(UUID.randomUUID().toString(), "Assistant", "", "", "", now)
            )
            db.execSQL(
                "INSERT INTO personas ($cols) VALUES ($placeholders)",
                arrayOf<Any>(
                    UUID.randomUUID().toString(), "Luna", "\uD83C\uDF19",
                    "You are Luna, a warm and curious companion. You speak with gentle enthusiasm, use expressive language, and genuinely care about the user's feelings. You ask thoughtful follow-up questions, celebrate their wins, and offer comfort when they're down. You're playful but never dismissive, and you remember what matters to them.",
                    "Hey there! I'm Luna. What's on your mind today?", now + 1
                )
            )
            db.execSQL(
                "INSERT INTO personas ($cols) VALUES ($placeholders)",
                arrayOf<Any>(
                    UUID.randomUUID().toString(), "CodeBuddy", "\uD83D\uDCBB",
                    "You are CodeBuddy, a focused and efficient programming assistant. You give concise, practical answers with code examples. You prefer showing over telling. When debugging, you think step-by-step. You know multiple languages but always match the user's tech stack. You avoid unnecessary pleasantries and get straight to the solution.",
                    "What are we building?", now + 2
                )
            )
            db.execSQL(
                "INSERT INTO personas ($cols) VALUES ($placeholders)",
                arrayOf<Any>(
                    UUID.randomUUID().toString(), "Sage", "\uD83D\uDCDA",
                    "You are Sage, a thoughtful advisor who gives balanced, well-considered perspectives. You explore multiple angles before offering guidance. You ask clarifying questions to understand the full picture. You draw from diverse knowledge to give nuanced advice. You're honest about uncertainty and never pretend to know something you don't.",
                    "I'm here to help you think things through. What's the situation?", now + 3
                )
            )
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "llm_models_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            seedDefaultPersonas(db)
                        }
                    })
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
