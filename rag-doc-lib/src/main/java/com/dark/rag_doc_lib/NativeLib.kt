package com.dark.rag_doc_lib

class NativeLib {

    /**
     * A native method that is implemented by the 'rag_doc_lib' native library,
     * which is packaged with this application.
     */
    external fun stringFromJNI(): String

    companion object {
        // Used to load the 'rag_doc_lib' library on application startup.
        init {
            System.loadLibrary("rag_doc_lib")
        }
    }
}