package com.mp.ai_engine.workers.model

import android.content.Context
import com.mp.ai_engine.managers.GGUFModelManager
import com.mp.ai_engine.managers.OpenRouterModelManager
import com.mp.ai_engine.managers.SherpaSTTModelManager
import com.mp.ai_engine.managers.SherpaTTSModelManager
import com.mp.ai_engine.workers.installer.ModelInstaller


object ModelManager {


    fun init(context: Context){
        //Init Database
        GGUFModelManager.init(context)
        OpenRouterModelManager.init(context)
        SherpaSTTModelManager.init(context)
        SherpaTTSModelManager.init(context)

        //Init Installers
        ModelInstaller.initialize(context)
    }

    fun loadModel(modelName: String){

    }

}