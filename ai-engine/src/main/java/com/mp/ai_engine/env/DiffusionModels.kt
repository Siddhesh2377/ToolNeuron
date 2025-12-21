package com.mp.ai_engine.env

import com.mp.ai_engine.util.getChipsetSuffix
import com.mp.ai_engine.util.getDeviceSoc

object DiffusionModels {

    fun model1(){
        val soc = getDeviceSoc()
        val suffix = getChipsetSuffix(soc) ?: "min"
        val fileUri = "xororz/sd-qnn/resolve/main/AnythingV5_qnn2.28_${suffix}.zip"
    }

}