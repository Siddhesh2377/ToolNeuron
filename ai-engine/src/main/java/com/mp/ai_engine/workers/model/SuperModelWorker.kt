package com.mp.ai_engine.workers.model

open class SuperModelWorker<A, B> {

    open fun loadModel(modelData: A): Result<String>{
        return Result.success("Init The Service")
    }

    open fun unloadModel(){

    }

    open suspend  fun runTask(task: B){

    }

}