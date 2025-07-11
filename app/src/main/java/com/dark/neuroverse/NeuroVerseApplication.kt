package com.dark.neuroverse

import android.app.Application
import android.content.Intent
import android.os.Environment
import android.provider.Settings
import android.util.Log
import com.dark.ai_manager.ai.data.db.DatabaseProvider
import com.dark.ai_manager.ai.local.Neuron
import com.dark.neuroverse.utils.UserPrefs
import com.dark.neuroverse.utils.taskRouterSystemPrompt
import com.dark.neuroverse.worker.model.ModelManager
import com.dark.task_manager.register.TaskRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

@Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
class NeuroVerseApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.d("NeuroVerseApplication", "✅ Application started")
        TaskRegistry.init(this)
        ModelManager.init(this)

        val db = DatabaseProvider.getDatabase(this)

        CoroutineScope(Dispatchers.IO).launch {
            if (!UserPrefs.isTermsAccepted(applicationContext).first()) return@launch

            val modelName = UserPrefs.getCurrentModel(applicationContext).first() ?: ""

            if (modelName.isNotEmpty()) {
                Neuron.loadModel(
                    File(
                        db.ModelDAO().getModelByName(modelName)?.modelPath
                    ),
                    systemPrompt = taskRouterSystemPrompt
                )

            } else {
                Neuron.loadModel(
                    File(db.ModelDAO().getAllModels().first()[0].modelPath),
                    systemPrompt = taskRouterSystemPrompt
                )
            }
        }

    }
}
