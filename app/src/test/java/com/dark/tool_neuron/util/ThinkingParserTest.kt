package com.dark.tool_neuron.util

import org.junit.Assert.*
import org.junit.Test

class ThinkingParserTest {

    @Test
    fun `no think tags returns content as-is`() {
        val result = ThinkingParser.parse("Hello world")
        assertNull(result.thinkingText)
        assertEquals("Hello world", result.visibleText)
        assertFalse(result.isThinkingInProgress)
    }

    @Test
    fun `empty string returns empty`() {
        val result = ThinkingParser.parse("")
        assertNull(result.thinkingText)
        assertEquals("", result.visibleText)
        assertFalse(result.isThinkingInProgress)
    }

    @Test
    fun `single complete think block extracted`() {
        val result = ThinkingParser.parse("<think>reasoning here</think>The answer is 42.")
        assertEquals("reasoning here", result.thinkingText)
        assertEquals("The answer is 42.", result.visibleText)
        assertFalse(result.isThinkingInProgress)
    }

    @Test
    fun `multiple think blocks combined`() {
        val input = "<think>first thought</think>response one<think>second thought</think>response two"
        val result = ThinkingParser.parse(input)
        assertEquals("first thought\n\nsecond thought", result.thinkingText)
        assertEquals("response oneresponse two", result.visibleText)
        assertFalse(result.isThinkingInProgress)
    }

    @Test
    fun `unclosed think tag detected as streaming`() {
        val result = ThinkingParser.parse("<think>still thinking about this...")
        assertEquals("still thinking about this...", result.thinkingText)
        assertEquals("", result.visibleText)
        assertTrue(result.isThinkingInProgress)
    }

    @Test
    fun `completed think block followed by unclosed`() {
        val input = "<think>done thinking</think>partial answer<think>more reasoning"
        val result = ThinkingParser.parse(input)
        assertEquals("done thinking\n\nmore reasoning", result.thinkingText)
        assertEquals("partial answer", result.visibleText)
        assertTrue(result.isThinkingInProgress)
    }

    @Test
    fun `think block with newlines preserved`() {
        val input = "<think>line 1\nline 2\nline 3</think>answer"
        val result = ThinkingParser.parse(input)
        assertEquals("line 1\nline 2\nline 3", result.thinkingText)
        assertEquals("answer", result.visibleText)
    }

    @Test
    fun `empty think block returns null thinking`() {
        val result = ThinkingParser.parse("<think></think>just the answer")
        assertNull(result.thinkingText)
        assertEquals("just the answer", result.visibleText)
    }

    @Test
    fun `only think block no visible text`() {
        val result = ThinkingParser.parse("<think>all thinking no answer</think>")
        assertEquals("all thinking no answer", result.thinkingText)
        assertEquals("", result.visibleText)
        assertFalse(result.isThinkingInProgress)
    }

    @Test
    fun `whitespace around tags trimmed`() {
        val result = ThinkingParser.parse("  <think>  thought  </think>  answer  ")
        assertEquals("thought", result.thinkingText)
        assertEquals("answer", result.visibleText)
    }
}
