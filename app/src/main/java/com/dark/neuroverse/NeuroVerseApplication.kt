package com.dark.neuroverse

import android.app.Application
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

class NeuroVerseApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.d("NeuroVerseApplication", "✅ Application started")
        TaskRegistry.init(this)
        ModelManager.init(this)

        val db = DatabaseProvider.getDatabase(this)
        val modelName = UserPrefs.getCurrentModel(this)

        CoroutineScope(Dispatchers.IO).launch {
            if (modelName.first()?.isNotEmpty() ?: true){
                Neuron.loadModel(
                    db.ModelDAO().getModelByName()?.modelPath,
                    systemPrompt = taskRouterSystemPrompt
                )
            }else{
                Neuron.loadModel(
                    db.ModelDAO().getAllModels().first()[0].modelPath,
                    systemPrompt = taskRouterSystemPrompt
                )
            }
        }

    }
}
