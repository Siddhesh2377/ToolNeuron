package com.dark.tool_neuron.service

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log

/**
 * Bridge for interacting with the Termux app to run local MCP servers.
 * Uses Termux's RUN_COMMAND intent API to execute commands.
 */
object TermuxBridge {
    private const val TAG = "TermuxBridge"

    const val TERMUX_PACKAGE = "com.termux"
    private const val RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"
    private const val RUN_COMMAND_ACTION = "com.termux.RUN_COMMAND"
    const val RUN_COMMAND_PERMISSION = "com.termux.permission.RUN_COMMAND"

    private const val EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
    private const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
    private const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
    private const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
    private const val EXTRA_SESSION_ACTION = "com.termux.RUN_COMMAND_SESSION_ACTION"

    const val FDROID_URL = "https://f-droid.org/packages/com.termux/"
    const val GITHUB_URL = "https://github.com/termux/termux-app/releases"

    /**
     * Check if Termux is installed on the device.
     */
    fun isTermuxInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Check if the app has the RUN_COMMAND permission for Termux.
     */
    fun hasRunCommandPermission(context: Context): Boolean {
        return context.checkSelfPermission(RUN_COMMAND_PERMISSION) ==
                PackageManager.PERMISSION_GRANTED
    }

    /**
     * Launch the Termux app.
     */
    fun launchTermux(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } else {
            Log.w(TAG, "Could not launch Termux — no launch intent found")
        }
    }

    /**
     * Run a command in Termux via the RUN_COMMAND intent.
     *
     * @param context Android context
     * @param command The executable path (e.g., "/data/data/com.termux/files/usr/bin/python")
     * @param arguments Command arguments
     * @param background If true, runs in background without a terminal session
     * @param workdir Working directory for the command
     */
    fun runCommand(
        context: Context,
        command: String,
        arguments: Array<String> = emptyArray(),
        background: Boolean = true,
        workdir: String? = null
    ) {
        val intent = Intent(RUN_COMMAND_ACTION).apply {
            setClassName(TERMUX_PACKAGE, RUN_COMMAND_SERVICE)
            putExtra(EXTRA_COMMAND_PATH, command)
            putExtra(EXTRA_ARGUMENTS, arguments)
            putExtra(EXTRA_BACKGROUND, background)
            if (workdir != null) {
                putExtra(EXTRA_WORKDIR, workdir)
            }
            // 0 = open new session, 1 = attach to current, 2 = do nothing
            putExtra(EXTRA_SESSION_ACTION, "0")
        }
        try {
            context.startForegroundService(intent)
            Log.d(TAG, "Sent RUN_COMMAND to Termux: $command ${arguments.joinToString(" ")}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send RUN_COMMAND to Termux", e)
        }
    }

    /**
     * Install a pip package in Termux.
     */
    fun pipInstall(context: Context, packageName: String) {
        runCommand(
            context = context,
            command = "/data/data/com.termux/files/usr/bin/bash",
            arguments = arrayOf("-c", "pip install $packageName"),
            background = false
        )
    }

    /**
     * Start a Python-based MCP server in Termux.
     *
     * @param context Android context
     * @param pipPackage The pip package name of the MCP server
     * @param port The port to run the server on
     * @param extraArgs Additional arguments for the server command
     */
    fun startMcpServer(
        context: Context,
        pipPackage: String,
        port: Int,
        extraArgs: Array<String> = emptyArray()
    ) {
        val serverCmd = buildString {
            append("python -m $pipPackage --port $port")
            if (extraArgs.isNotEmpty()) {
                append(" ")
                append(extraArgs.joinToString(" "))
            }
        }
        runCommand(
            context = context,
            command = "/data/data/com.termux/files/usr/bin/bash",
            arguments = arrayOf("-c", serverCmd),
            background = true
        )
    }

    /**
     * Get the localhost URL for a Termux-hosted MCP server.
     */
    fun getLocalServerUrl(port: Int, path: String = "/sse"): String {
        return "http://127.0.0.1:$port$path"
    }
}
