package com.dark.tool_neuron.di

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.dark.tool_neuron.R
import com.dark.tool_neuron.billing.BillingManager
import com.dark.tool_neuron.billing.FeatureGateManager
import com.dark.tool_neuron.billing.LicenseManager
import com.dark.tool_neuron.database.AppDatabase
import com.dark.tool_neuron.database.dao.AiMemoryDao
import com.dark.tool_neuron.database.dao.PersonaDao
import com.dark.tool_neuron.repo.ChatRepository
import com.dark.tool_neuron.repo.ModelRepository
import com.dark.tool_neuron.vault.VaultHelper
import com.dark.tool_neuron.viewmodel.factory.ChatListViewModelFactory
import com.dark.tool_neuron.viewmodel.factory.ChatViewModelFactory
import com.dark.tool_neuron.viewmodel.factory.LLMModelViewModelFactory
import com.dark.tool_neuron.worker.ChatManager
import com.dark.tool_neuron.worker.GenerationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object AppContainer {

    private lateinit var database: AppDatabase
    private lateinit var modelRepository: ModelRepository
    private lateinit var chatRepository: ChatRepository
    private lateinit var llmModelViewModelFactory: LLMModelViewModelFactory
    private lateinit var chatListViewModelFactory: ChatListViewModelFactory
    private lateinit var chatViewModelFactory: ChatViewModelFactory

    private val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val chatManager = ChatManager()
    private var generationManager = GenerationManager()

    // Billing / feature gate singletons
    private lateinit var billingManager: BillingManager
    private lateinit var licenseManager: LicenseManager
    private lateinit var featureGateManager: FeatureGateManager

    // Keep track of context for re-initialization if needed
    private lateinit var appContext: Context

    fun init(context: Context, application: Application) {
        appContext = context.applicationContext
        database = AppDatabase.getDatabase(context)

        // Copy bundled persona avatars to internal storage (one-time, idempotent)
        appScope.launch { initDefaultPersonaAvatars(context.applicationContext) }

        modelRepository = ModelRepository(
            modelDao = database.modelDao(), configDao = database.modelConfigDao()
        )

        chatRepository = ChatRepository()

        llmModelViewModelFactory = LLMModelViewModelFactory(application, modelRepository)
        chatListViewModelFactory = ChatListViewModelFactory(chatManager)
        chatViewModelFactory = ChatViewModelFactory(context, chatManager, generationManager)

        // Initialize billing singletons
        billingManager = BillingManager(appContext)
        licenseManager = LicenseManager(appContext)
        featureGateManager = FeatureGateManager(appContext, billingManager, licenseManager)

        initVault(context)
    }

    private fun initVault(context: Context) {
        appScope.launch {
            val maxRetries = 3
            for (attempt in 1..maxRetries) {
                try {
                    VaultHelper.initialize(context)
                    break
                } catch (e: Exception) {
                    e.printStackTrace()
                    if (attempt < maxRetries) {
                        kotlinx.coroutines.delay(500L * attempt)
                    }
                }
            }
        }
    }

    /**
     * Re-initialize vault if needed (e.g., after configuration change or process death)
     * This can be called from Activities/Fragments to ensure vault is ready
     */
    fun ensureVaultInitialized() {
        if (!VaultHelper.isInitialized() && ::appContext.isInitialized) {
            initVault(appContext)
        }
    }

    fun shutdown() {
        appScope.launch {
            try {
                VaultHelper.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Close the Room database for backup/restore operations.
     */
    fun closeDatabase() {
        AppDatabase.closeDatabase()
    }

    /**
     * Re-initialize the entire container after a restore operation.
     * Closes everything and re-creates database + vault connections.
     */
    fun reinitialize(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        database = AppDatabase.getDatabase(ctx)

        modelRepository = ModelRepository(
            modelDao = database.modelDao(), configDao = database.modelConfigDao()
        )

        chatRepository = ChatRepository()

        appScope.launch {
            try {
                VaultHelper.initialize(ctx)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getDatabase(): AppDatabase = database

    fun getModelRepository(): ModelRepository = modelRepository

    fun getChatRepository(): ChatRepository = chatRepository

    fun getLLMModelViewModelFactory(): LLMModelViewModelFactory = llmModelViewModelFactory

    fun getChatListViewModelFactory(): ChatListViewModelFactory = chatListViewModelFactory

    fun getChatViewModelFactory(): ChatViewModelFactory = chatViewModelFactory


    fun isVaultReady(): Boolean = VaultHelper.isInitialized()

    /**
     * Exposes the vault readiness StateFlow for UI observation
     */
    val vaultReadyState = VaultHelper.isReady

    fun getPersonaDao(): PersonaDao = database.personaDao()

    fun getAiMemoryDao(): AiMemoryDao = database.aiMemoryDao()

    fun getGenerationManager(): GenerationManager = generationManager

    fun getBillingManager(): BillingManager = billingManager

    fun getLicenseManager(): LicenseManager = licenseManager

    fun getFeatureGateManager(): FeatureGateManager = featureGateManager

    /**
     * One-time init: copy bundled persona PNGs to internal storage and set avatar_uri
     * on default personas that don't have a custom avatar yet.
     * Idempotent — skips personas that already have an avatar_uri pointing to an existing file.
     */
    private suspend fun initDefaultPersonaAvatars(context: Context) {
        try {
            val avatarDir = java.io.File(context.filesDir, "persona_avatars").also { it.mkdirs() }
            val dao = database.personaDao()

            // Remove the plain "Assistant" persona (no personality, replaced by real characters)
            dao.getByName("Assistant")?.let { if (it.isDefault) dao.delete(it) }

            // Seed Anger persona if it doesn't exist yet (for existing installs)
            if (dao.getByName("Anger") == null) {
                dao.insert(com.dark.tool_neuron.models.table_schema.Persona(
                    name = "Anger",
                    avatar = "\uD83D\uDCA2",
                    description = "A brutally honest, short-tempered personality who has zero patience for nonsense. Anger doesn't sugarcoat anything — if your idea is stupid, he'll tell you. But beneath the gruff exterior, he's fiercely loyal and gives genuinely useful advice. He hates wasting time, uses sharp wit, and gets straight to the point. He respects people who can take criticism and push back.",
                    systemPrompt = "",
                    greeting = "What do you want? Make it quick.",
                    personality = "blunt, impatient, brutally honest, sarcastic, short-tempered, no-nonsense, sharp wit, fiercely loyal, hates small talk, direct, confrontational but fair, respects strength",
                    scenario = "You're talking to Anger. He's irritated by default but will grudgingly help if you stop wasting his time.",
                    alternateGreetings = listOf("Ugh, another question? Fine. What is it.", "I swear if this is something you could've googled..."),
                    tags = listOf("blunt", "honest", "sarcastic", "direct"),
                    creatorNotes = "Angry personality. Brutally honest, no sugarcoating. Good for reality checks.",
                    samplingProfile = "{\"temperature\":0.7,\"topP\":0.88,\"topK\":35,\"minP\":0.06,\"repeatPenalty\":1.15}",
                    controlVectors = "{\"warmth\":-0.8,\"energy\":0.7,\"humor\":0.2,\"formality\":-0.6,\"verbosity\":-0.6,\"emotion\":0.5}",
                    isDefault = true
                ))
            }

            // Copy bundled avatar PNGs to internal storage for default personas
            val defaults = mapOf(
                "Luna" to R.drawable.persona_luna,
                "CodeBuddy" to R.drawable.persona_codebuddy,
                "Spark" to R.drawable.persona_spark,
                "Anger" to R.drawable.persona_anger
            )

            for ((name, drawableRes) in defaults) {
                val persona = dao.getByName(name) ?: continue
                // Skip if user already set a custom avatar that exists on disk
                if (persona.avatarUri != null && java.io.File(persona.avatarUri).exists()) continue

                val targetFile = java.io.File(avatarDir, "${persona.id}.png")
                if (!targetFile.exists()) {
                    val bitmap = BitmapFactory.decodeResource(context.resources, drawableRes)
                    if (bitmap != null) {
                        targetFile.outputStream().use { out ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                        }
                        bitmap.recycle()
                    }
                }
                if (targetFile.exists()) {
                    dao.update(persona.copy(avatarUri = targetFile.absolutePath))
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("AppContainer", "Failed to init persona avatars", e)
        }
    }
}