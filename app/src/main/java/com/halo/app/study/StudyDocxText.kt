package com.halo.app.study

import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/** Text-only extraction. Embedded objects and layout are not interpreted. */
object StudyDocxText {
    fun read(input: InputStream, checkActive: () -> Unit = {}): String {
        val xml = ZipInputStream(input).use { zip ->
            var entries = 0
            var scanned = 0
            val buffer = ByteArray(8192)
            while (true) {
                checkActive()
                val entry = zip.nextEntry ?: error("No document text found in DOCX")
                require(++entries <= 256) { "DOCX has too many entries" }
                val output = if (entry.name == "word/document.xml") java.io.ByteArrayOutputStream() else null
                while (true) {
                    checkActive()
                    val n = zip.read(buffer)
                    if (n < 0) break
                    scanned += n
                    require(scanned <= 24 * 1024 * 1024) { "DOCX exceeds extraction budget" }
                    output?.write(buffer, 0, n)
                    require((output?.size() ?: 0) <= 12 * 1024 * 1024) { "DOCX text exceeds extraction budget" }
                }
                if (output != null) return@use output.toByteArray()
            }
            @Suppress("UNREACHABLE_CODE") byteArrayOf()
        }
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
        val doc = factory.newDocumentBuilder().parse(xml.inputStream())
        val paragraphs = doc.getElementsByTagNameNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "p")
        val text = StringBuilder()
        fun append(node: org.w3c.dom.Node, depth: Int = 0) {
            require(depth <= 128) { "DOCX nesting exceeds extraction budget" }
            checkActive()
            when (node.localName) {
                "t" -> text.append(node.textContent)
                "tab" -> text.append('\t')
                "br", "cr" -> text.append('\n')
                else -> for (i in 0 until node.childNodes.length) append(node.childNodes.item(i), depth + 1)
            }
            require(text.length <= StudySourceLimits.DECODED_CHARS) { "DOCX text exceeds character budget; split the document" }
        }
        for (i in 0 until paragraphs.length) { append(paragraphs.item(i)); text.append('\n') }
        return text.toString().trim()
    }
}
