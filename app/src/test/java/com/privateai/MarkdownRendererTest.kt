package com.privateai

import com.privateai.ui.components.MarkdownElement
import com.privateai.ui.components.parseMarkdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownRendererTest {

    @Test
    fun `parseMarkdown correctly extracts markdown tables`() {
        val markdown = """
            Here is a comparison table:
            | Algorithm | Time Complexity | Space Complexity |
            | :--- | :---: | ---: |
            | Merge Sort | O(n log n) | O(n) |
            | Quick Sort | O(n log n) avg | O(log n) |
            
            This concludes the table.
        """.trimIndent()

        val elements = parseMarkdown(markdown)
        assertTrue(elements.isNotEmpty())

        val tableElement = elements.filterIsInstance<MarkdownElement.Table>().firstOrNull()
        assertNotNull("Table element was not parsed", tableElement)
        assertEquals(3, tableElement?.headers?.size)
        assertEquals("Algorithm", tableElement?.headers?.get(0))
        assertEquals("Time Complexity", tableElement?.headers?.get(1))
        assertEquals("Space Complexity", tableElement?.headers?.get(2))

        assertEquals(2, tableElement?.rows?.size)
        assertEquals(listOf("Merge Sort", "O(n log n)", "O(n)"), tableElement?.rows?.get(0))
        assertEquals(listOf("Quick Sort", "O(n log n) avg", "O(log n)"), tableElement?.rows?.get(1))
    }

    @Test
    fun `parseMarkdown handles unclosed streaming code block safely`() {
        val streamingCode = """
            Here is the implementation:
            ```kotlin
            fun binarySearch(arr: IntArray, target: Int): Int {
                var left = 0
                var right = arr.size - 1
        """.trimIndent()

        val elements = parseMarkdown(streamingCode)
        val codeElement = elements.filterIsInstance<MarkdownElement.Code>().firstOrNull()

        assertNotNull("Streaming unclosed code fence was not captured", codeElement)
        assertEquals("kotlin", codeElement?.language)
        assertTrue(codeElement?.code?.contains("binarySearch") == true)
    }

    @Test
    fun `parseMarkdown handles headers, bullet lists, and numbered lists`() {
        val markdown = """
            # Primary Header
            ## Secondary Header
            ### Subsection
            * Bullet item one
            - Bullet item two
            1. Step one
            2. Step two
        """.trimIndent()

        val elements = parseMarkdown(markdown)
        // Filter out empty paragraphs (blank lines)
        val meaningful = elements.filter {
            it !is MarkdownElement.Paragraph || it.text.isNotBlank()
        }

        assertEquals(7, meaningful.size)

        assertTrue(meaningful[0] is MarkdownElement.Header && (meaningful[0] as MarkdownElement.Header).level == 1)
        assertTrue(meaningful[1] is MarkdownElement.Header && (meaningful[1] as MarkdownElement.Header).level == 2)
        assertTrue(meaningful[2] is MarkdownElement.Header && (meaningful[2] as MarkdownElement.Header).level == 3)
        assertTrue(meaningful[3] is MarkdownElement.BulletItem)
        assertTrue(meaningful[4] is MarkdownElement.BulletItem)
        assertTrue(meaningful[5] is MarkdownElement.NumberedItem)
        assertTrue(meaningful[6] is MarkdownElement.NumberedItem)
    }
}
