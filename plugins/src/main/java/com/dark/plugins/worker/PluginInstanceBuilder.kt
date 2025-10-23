package com.dark.plugins.worker

import android.util.Log
import androidx.compose.runtime.Composable
import com.mp.plugin_api.api.PluginApi

internal object PluginInstanceBuilder {
    private const val TAG = "PluginLoader"

    /**
     * Instantiates a plugin from a ClassLoader.
     * Returns the PluginApi instance and its composable content function.
     */
    @Suppress("UNCHECKED_CAST")
    fun instantiatePlugin(
        classLoader: ClassLoader,
        className: String
    ): Pair<PluginApi, (@Composable () -> Unit)> {

        // Load the plugin class
        val pluginClass = classLoader.loadClass(className)
        Log.d(TAG, "Loaded class: $className")

        // Create plugin instance
        val instance = createInstance(pluginClass) as PluginApi
        Log.d(TAG, "Plugin instance created: ${instance.javaClass.simpleName}")

        // Invoke onCreate if available (optional lifecycle method)
        runCatching {
            val onCreateMethod = pluginClass.getMethod("onCreate")
            onCreateMethod.isAccessible = true
            onCreateMethod.invoke(instance)
            Log.d(TAG, "onCreate() invoked successfully")
        }.onFailure {
            Log.d(TAG, "onCreate() not invoked (optional): ${it.message}")
        }

        // Get composable content block
        val contentBlock = getContentBlock(instance, classLoader, pluginClass)
        Log.d(TAG, "Content block retrieved successfully")

        return instance to contentBlock
    }

    /**
     * Creates an instance of the plugin class using available constructors.
     * Tries in order:
     * 1. Zero-arg constructor
     * 2. Kotlin object INSTANCE field
     * 3. Companion object factory method
     */
    private fun createInstance(clazz: Class<*>): Any {
        Log.d(TAG, "Creating instance of ${clazz.simpleName}")

        // Try 1: Zero-arg constructor
        runCatching {
            val constructor = clazz.getDeclaredConstructor()
            constructor.isAccessible = true
            Log.d(TAG, "Using zero-arg constructor")
            return constructor.newInstance()
        }.onSuccess {
            return it
        }.onFailure {
            Log.v(TAG, "Zero-arg constructor not available: ${it.message}")
        }

        // Try 2: Kotlin object INSTANCE
        runCatching {
            val instanceField = clazz.getDeclaredField("INSTANCE")
            instanceField.isAccessible = true
            val instance = instanceField.get(null)
            Log.d(TAG, "Using Kotlin object INSTANCE")
            return checkNotNull(instance) { "INSTANCE field was null" }
        }.onSuccess {
            return it
        }.onFailure {
            Log.v(TAG, "INSTANCE field not available: ${it.message}")
        }

        // Try 3: Companion object factory
        runCatching {
            val companionClass = clazz.declaredClasses
                .firstOrNull { it.simpleName == "Companion" }
                ?: throw NoSuchFieldException("No Companion object found")

            val companionField = clazz.getDeclaredField("Companion")
            companionField.isAccessible = true
            val companion = companionField.get(null)
                ?: throw IllegalStateException("Companion field was null")

            val factoryMethod = companionClass.methods
                .firstOrNull { it.name == "create" || it.name == "getInstance" }
                ?: throw NoSuchMethodException("No factory method in Companion")

            factoryMethod.isAccessible = true
            val instance = factoryMethod.invoke(companion)
            Log.d(TAG, "Using Companion.${factoryMethod.name}()")
            return checkNotNull(instance) { "Factory method returned null" }
        }.onSuccess {
            return it
        }.onFailure {
            Log.v(TAG, "Companion factory not available: ${it.message}")
        }

        throw IllegalStateException(
            "Cannot instantiate ${clazz.simpleName}: no zero-arg constructor, " +
                    "INSTANCE field, or Companion factory method found"
        )
    }

    /**
     * Gets the composable content block from the plugin instance.
     * Tries multiple strategies to find the composable content.
     */
    private fun getContentBlock(
        instance: PluginApi,
        classLoader: ClassLoader,
        pluginClass: Class<*>
    ): @Composable () -> Unit {

        // Try 1: Direct call to content() method (since PluginApi implements ComposePlugin)
        runCatching {
            // PluginApi already has content() method, just call it
            @Suppress("UNCHECKED_CAST")
            val block = instance.content() as? (@Composable () -> Unit)
            if (block != null) {
                Log.d(TAG, "Using PluginApi.content() method (direct call)")
                return block
            }
        }.onSuccess {
            return { it }
        }.onFailure {
            Log.v(TAG, "Direct content() call failed: ${it.message}")
        }

        // Try 2: Reflection on content() method
        runCatching {
            val contentMethod = pluginClass.getMethod("content")
            contentMethod.isAccessible = true

            @Suppress("UNCHECKED_CAST")
            val block = contentMethod.invoke(instance) as? (@Composable () -> Unit)

            if (block != null) {
                Log.d(TAG, "Using content() via reflection")
                return block
            }
        }.onSuccess {
            return { it }
        }.onFailure {
            Log.v(TAG, "Reflection content() failed: ${it.message}")
        }

        // Try 3: Find method named "content" with any return type
        runCatching {
            val contentMethod = pluginClass.methods
                .firstOrNull { method ->
                    method.name == "content" && method.parameterCount == 0
                } ?: throw NoSuchMethodException("No content() method found")

            contentMethod.isAccessible = true

            @Suppress("UNCHECKED_CAST")
            val block = contentMethod.invoke(instance) as? (@Composable () -> Unit)

            Log.d(TAG, "Using method named 'content': ${contentMethod.returnType.name}")
            return checkNotNull(block) {
                "content() returned null or invalid composable"
            }
        }.onSuccess {
            return it
        }.onFailure {
            Log.v(TAG, "Named content() search failed: ${it.message}")
        }

        // Try 4: ComposePlugin interface (for plugins not extending PluginApi)
        runCatching {
            val composePluginInterface = Class.forName(
                "com.mp.plugin_api.api.ComposePlugin",
                false,
                classLoader
            )

            if (composePluginInterface.isAssignableFrom(pluginClass)) {
                val contentMethod = composePluginInterface.getDeclaredMethod("content")
                contentMethod.isAccessible = true

                @Suppress("UNCHECKED_CAST")
                val block = contentMethod.invoke(instance) as? (@Composable () -> Unit)

                if (block != null) {
                    Log.d(TAG, "Using ComposePlugin interface")
                    return block
                }
            }
        }.onFailure {
            Log.v(TAG, "ComposePlugin interface failed: ${it.message}")
        }

        // Try 5: Any method returning Function0-like type (last resort)
        runCatching {
            val contentMethod = pluginClass.methods
                .firstOrNull { method ->
                    method.parameterCount == 0 &&
                            (method.returnType.name.contains("Function0") ||
                                    method.returnType.name.contains("ComposableBlock"))
                } ?: throw NoSuchMethodException(
                "No method returning Function0/ComposableBlock found"
            )

            contentMethod.isAccessible = true

            @Suppress("UNCHECKED_CAST")
            val block = contentMethod.invoke(instance) as? (@Composable () -> Unit)

            Log.d(TAG, "Using fallback method: ${contentMethod.name}()")
            return checkNotNull(block) {
                "${contentMethod.name}() returned null"
            }
        }.onSuccess {
            return it
        }

        // If all attempts failed, provide detailed error
        val availableMethods = pluginClass.methods
            .filter { it.parameterCount == 0 }
            .joinToString { "${it.name}(): ${it.returnType.simpleName}" }

        throw IllegalStateException(
            "Cannot get composable content from ${pluginClass.simpleName}. " +
                    "Tried: direct call, reflection, interface lookup, fallback. " +
                    "Available zero-arg methods: [$availableMethods]"
        )
    }
}