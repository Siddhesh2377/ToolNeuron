package com.dark.tool_neuron.terminal

import android.content.Context
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient

class TerminalViewClientImpl(
    private val view: TerminalView,
    private val extraKeysState: ExtraKeysState,
    initialTextSizeSp: Int,
) : TerminalViewClient {

    private var copyMode: Boolean = false
    private var currentTextSize: Int = initialTextSizeSp

    override fun onScale(scale: Float): Float {
        val min = 14
        val max = 72
        val next = (currentTextSize * scale).toInt().coerceIn(min, max)
        if (next != currentTextSize) {
            currentTextSize = next
            view.setTextSize(next)
        }
        return 1f
    }

    override fun onSingleTapUp(e: MotionEvent?) {
        view.requestFocus()
        showKeyboard()
    }

    private fun showKeyboard() {
        val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(view, 0)
    }

    override fun shouldBackButtonBeMappedToEscape(): Boolean = true
    override fun shouldEnforceCharBasedInput(): Boolean = false
    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
    override fun isTerminalViewSelected(): Boolean = true

    override fun copyModeChanged(copyMode: Boolean) { this.copyMode = copyMode }

    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession?): Boolean {
        if (session == null) return false
        if (e.isCtrlPressed || extraKeysState.ctrl) {
            val lower = Character.toLowerCase(e.unicodeChar).toInt()
            if (lower in 0x20..0x7e) {
                val ctrlCode = (lower and 0x1f).toChar()
                session.write(byteArrayOf(ctrlCode.code.toByte()), 0, 1)
                return true
            }
        }
        return false
    }

    override fun onKeyUp(keyCode: Int, e: KeyEvent?): Boolean = false

    override fun onLongPress(event: MotionEvent?): Boolean {
        showKeyboard()
        return true
    }

    override fun readControlKey(): Boolean = extraKeysState.consumeCtrlIfPending()
    override fun readAltKey(): Boolean = extraKeysState.consumeAltIfPending()
    override fun readShiftKey(): Boolean = extraKeysState.consumeShiftIfPending()
    override fun readFnKey(): Boolean = extraKeysState.consumeFnIfPending()

    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession?): Boolean = false

    override fun onEmulatorSet() { view.invalidate() }

    override fun logError(tag: String?, message: String?) {}
    override fun logWarn(tag: String?, message: String?) {}
    override fun logInfo(tag: String?, message: String?) {}
    override fun logDebug(tag: String?, message: String?) {}
    override fun logVerbose(tag: String?, message: String?) {}
    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {}
    override fun logStackTrace(tag: String?, e: Exception?) {}
}

class ExtraKeysState {
    var ctrl: Boolean = false
    var alt: Boolean = false
    var shift: Boolean = false
    var fn: Boolean = false

    private var ctrlOneShot: Boolean = false
    private var altOneShot: Boolean = false
    private var shiftOneShot: Boolean = false
    private var fnOneShot: Boolean = false

    fun toggleCtrl() { ctrl = !ctrl; ctrlOneShot = ctrl }
    fun toggleAlt() { alt = !alt; altOneShot = alt }
    fun toggleShift() { shift = !shift; shiftOneShot = shift }
    fun toggleFn() { fn = !fn; fnOneShot = fn }

    fun consumeCtrlIfPending(): Boolean {
        val v = ctrlOneShot
        if (v) { ctrlOneShot = false; ctrl = false }
        return v
    }
    fun consumeAltIfPending(): Boolean {
        val v = altOneShot
        if (v) { altOneShot = false; alt = false }
        return v
    }
    fun consumeShiftIfPending(): Boolean {
        val v = shiftOneShot
        if (v) { shiftOneShot = false; shift = false }
        return v
    }
    fun consumeFnIfPending(): Boolean {
        val v = fnOneShot
        if (v) { fnOneShot = false; fn = false }
        return v
    }
}
