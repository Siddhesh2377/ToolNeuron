package com.dark.tool_neuron.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.dark.tool_neuron.database.dao.McpServerDao
import com.dark.tool_neuron.database.dao.ModelConfigDao
import com.dark.tool_neuron.database.dao.ModelDao
import com.dark.tool_neuron.database.dao.RagDao
import com.dark.tool_neuron.models.converters.Converters
import com.dark.tool_neuron.models.table_schema.InstalledRag
import com.dark.tool_neuron.models.table_schema.McpServer
import com.dark.tool_neuron.models.table_schema.Model
import com.dark.tool_neuron.models.table_schema.ModelConfig

@Database(
    entities = [Model::class, ModelConfig::class, InstalledRag::class, McpServer::class],
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun modelDao(): ModelDao
    abstract fun modelConfigDao(): ModelConfigDao
    abstract fun ragDao(): RagDao
    abstract fun mcpServerDao(): McpServerDao

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
                // Create mcp_servers table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS mcp_servers (
                        id TEXT PRIMARY KEY NOT NULL,
                        name TEXT NOT NULL,
                        url TEXT NOT NULL,
                        transportType TEXT NOT NULL,
                        apiKey TEXT,
                        isEnabled INTEGER NOT NULL,
                        connectionStatus TEXT NOT NULL,
                        lastError TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        lastConnectedAt INTEGER,
                        description TEXT NOT NULL,
                        customHeadersJson TEXT
                    )
                """.trimIndent())
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "llm_models_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}