package com.mp.ai_engine.workers.model

import android.content.Context
import com.mp.ai_engine.service.ModelOperatorService
import com.mp.ai_engine.workers.installer.ModelInstaller


//Single Point Of Communication
object ModelManager {

    fun init(context: Context){
        //Init Installers
        ModelInstaller.initialize(context)
        startModelOperationService(context)
    }

    /**
     * Start the ModelOperatorService to manage model operations
     */
    fun startModelOperationService(context: Context){
        ModelOperatorService.start(context)
    }

    /**
     * Stop the ModelOperatorService
     */
    fun stopModelOperationService(context: Context){
        ModelOperatorService.stop(context)
    }

}