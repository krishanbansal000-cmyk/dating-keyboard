package com.datingcopilot.keyboard.nboard

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.StringReader
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource

enum class LayoutBottomStyle(val value: String) {
    CLASSIC("classic"),
    GBOARD("gboard");

    companion object {
        fun parse(raw: String?): LayoutBottomStyle {
            return when (raw?.trim()?.lowercase(Locale.US)) {
                GBOARD.value -> GBOARD
                else -> CLASSIC
            }
        }
    }
}

enum class LayoutPackSource {
    BUILTIN,
    IMPORTED
}

data class LayoutPack(
    val id: String,
    val displayName: String,
    val row1: List<String>,
    val row2: List<String>,
    val row3: List<String>,
    val isQwertyLike: Boolean,
    val bottomStyle: LayoutBottomStyle,
    val source: LayoutPackSource,
    val variants: Map<String, List<String>> = emptyMap()
) {
    fun isGboardStyle(): Boolean = bottomStyle == LayoutBottomStyle.GBOARD
}

class LayoutPackParseException(message: String) : IllegalArgumentException(message)

object LayoutPackManager {
    const val BUILTIN_AZERTY_CLASSIC_ID = "builtin.azerty.classic"
    const val BUILTIN_AZERTY_GBOARD_ID = "builtin.azerty.gboard"
    const val BUILTIN_QWERTY_CLASSIC_ID = "builtin.qwerty.classic"
    const val BUILTIN_QWERTY_GBOARD_ID = "builtin.qwerty.gboard"

    private const val IMPORT_DIR = "layout_packs"

    private val builtinPacks = listOf(
        LayoutPack(
            id = BUILTIN_AZERTY_CLASSIC_ID,
            displayName = "Azerty (legacy)",
            row1 = AZERTY_ROW_1,
            row2 = AZERTY_ROW_2,
            row3 = AZERTY_ROW_3,
            isQwertyLike = false,
            bottomStyle = LayoutBottomStyle.CLASSIC,
            source = LayoutPackSource.BUILTIN
        ),
        LayoutPack(
            id = BUILTIN_AZERTY_GBOARD_ID,
            displayName = "Azerty (gboard)",
            row1 = AZERTY_ROW_1,
            row2 = AZERTY_ROW_2,
            row3 = GBOARD_AZERTY_ROW_3,
            isQwertyLike = false,
            bottomStyle = LayoutBottomStyle.GBOARD,
            source = LayoutPackSource.BUILTIN
        ),
        LayoutPack(
            id = BUILTIN_QWERTY_CLASSIC_ID,
            displayName = "Qwerty (legacy)",
            row1 = QWERTY_ROW_1,
            row2 = QWERTY_ROW_2,
            row3 = QWERTY_ROW_3,
            isQwertyLike = true,
            bottomStyle = LayoutBottomStyle.CLASSIC,
            source = LayoutPackSource.BUILTIN
        ),
        LayoutPack(
            id = BUILTIN_QWERTY_GBOARD_ID,
            displayName = "Qwerty (gboard)",
            row1 = QWERTY_ROW_1,
            row2 = GBOARD_QWERTY_ROW_2,
            row3 = GBOARD_QWERTY_ROW_3,
            isQwertyLike = true,
            bottomStyle = LayoutBottomStyle.GBOARD,
            source = LayoutPackSource.BUILTIN
        )
    )

    fun defaultPack(): LayoutPack = builtinPacks.first { it.id == BUILTIN_AZERTY_CLASSIC_ID }

    fun defaultPackIdForLegacyMode(mode: KeyboardLayoutMode): String {
        return when (mode) {
            KeyboardLayoutMode.AZERTY -> BUILTIN_AZERTY_CLASSIC_ID
            KeyboardLayoutMode.QWERTY -> BUILTIN_QWERTY_CLASSIC_ID
            KeyboardLayoutMode.GBOARD_AZERTY -> BUILTIN_AZERTY_GBOARD_ID
            KeyboardLayoutMode.GBOARD_QWERTY -> BUILTIN_QWERTY_GBOARD_ID
        }
    }

    fun listInstalled(context: Context): List<LayoutPack> {
        val imported = loadImportedPacks(context)
        val importedById = imported.associateBy { it.id }

        val builtins = builtinPacks
            .filterNot { importedById.containsKey(it.id) }
            .sortedBy { it.displayName.lowercase(Locale.US) }

        val importedSorted = imported
            .sortedBy { it.displayName.lowercase(Locale.US) }

        return builtins + importedSorted
    }

    fun listImported(context: Context): List<LayoutPack> {
        return loadImportedPacks(context)
            .sortedBy { it.displayName.lowercase(Locale.US) }
    }

    fun resolveActive(context: Context): LayoutPack {
        val installed = listInstalled(context)
        if (installed.isEmpty()) {
            return defaultPack()
        }

        val activeId = KeyboardModeSettings.loadActiveLayoutPackId(context)
        installed.firstOrNull { it.id == activeId }?.let { return it }

        val fallbackId = defaultPackIdForLegacyMode(KeyboardModeSettings.loadLayoutMode(context))
        installed.firstOrNull { it.id == fallbackId }?.let { return it }

        return installed.first()
    }

    fun setActive(context: Context, packId: String): Boolean {
        val installed = listInstalled(context)
        if (installed.none { it.id == packId }) {
            return false
        }
        KeyboardModeSettings.saveActiveLayoutPackId(context, packId)
        return true
    }

    fun importFromUri(context: Context, uri: Uri): LayoutPack {
        val xml = context.contentResolver.openInputStream(uri)
            ?.bufferedReader()
            ?.use { it.readText() }
            ?.trim()
            ?: throw LayoutPackParseException("Could not read file")

        if (xml.isBlank()) {
            throw LayoutPackParseException("Layout file is empty")
        }

        val parsed = parseLayoutPackXml(xml, LayoutPackSource.IMPORTED)
        if (isBuiltinPackId(parsed.id)) {
            throw LayoutPackParseException("Pack id '${parsed.id}' is reserved by a built-in pack")
        }

        val dir = importDirectory(context)
        if (!dir.exists()) {
            dir.mkdirs()
        }

        deleteImportedPackFilesById(context, parsed.id)

        val safeId = parsed.id
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9._-]"), "_")
            .ifBlank { "layout_pack" }
        val target = File(dir, "${safeId}_${System.currentTimeMillis()}.xml")
        target.writeText(xml)

        return parsed
    }

    fun deleteImportedPack(context: Context, packId: String): Boolean {
        if (isBuiltinPackId(packId)) {
            return false
        }

        val removed = deleteImportedPackFilesById(context, packId)
        if (!removed) {
            return false
        }

        if (KeyboardModeSettings.loadActiveLayoutPackId(context) == packId) {
            val fallback = defaultPackIdForLegacyMode(KeyboardModeSettings.loadLayoutMode(context))
            val installed = listInstalled(context)
            val target = installed.firstOrNull { it.id == fallback }?.id
                ?: installed.firstOrNull()?.id
                ?: BUILTIN_AZERTY_CLASSIC_ID
            KeyboardModeSettings.saveActiveLayoutPackId(context, target)
        }

        return true
    }

    fun isBuiltinPackId(packId: String): Boolean {
        return builtinPacks.any { it.id == packId }
    }

    private fun loadImportedPacks(context: Context): List<LayoutPack> {
        val dir = importDirectory(context)
        if (!dir.exists() || !dir.isDirectory) {
            return emptyList()
        }

        val files = dir.listFiles { file -> file.isFile && file.extension.lowercase(Locale.US) == "xml" }
            ?: return emptyList()

        return files.mapNotNull { file ->
            runCatching {
                val xml = file.readText()
                parseLayoutPackXml(xml, LayoutPackSource.IMPORTED)
            }.getOrNull()
        }
    }

    private fun deleteImportedPackFilesById(context: Context, packId: String): Boolean {
        val dir = importDirectory(context)
        if (!dir.exists() || !dir.isDirectory) {
            return false
        }

        var removed = false
        val files = dir.listFiles { file -> file.isFile && file.extension.lowercase(Locale.US) == "xml" }.orEmpty()
        files.forEach { file ->
            val parsedId = runCatching {
                parseLayoutPackXml(file.readText(), LayoutPackSource.IMPORTED).id
            }.getOrNull()
            if (parsedId == packId && file.delete()) {
                removed = true
            }
        }
        return removed
    }

    private fun importDirectory(context: Context): File {
        return File(context.filesDir, IMPORT_DIR)
    }

    private fun parseLayoutPackXml(xml: String, source: LayoutPackSource): LayoutPack {
        val root = parseXmlRoot(xml)
        if (root.tagName != "layout-pack") {
            throw LayoutPackParseException("Root tag must be <layout-pack>")
        }

        val id = root.getAttribute("id").trim()
        if (id.isBlank()) {
            throw LayoutPackParseException("Missing required attribute: id")
        }

        val declaredName = root.getAttribute("name").trim()
        val displayName = if (declaredName.isBlank()) id else declaredName

        val qwertyLikeRaw = root.getAttribute("qwertyLike").ifBlank { null }
        var isQwertyLike = qwertyLikeRaw?.trim()?.lowercase(Locale.US) == "true"

        val bottomStyle = LayoutBottomStyle.parse(root.getAttribute("bottomStyle").ifBlank { null })

        val row1 = readRowTokens(root, "row1")
        val row2 = readRowTokens(root, "row2")
        val row3 = readRowTokens(root, "row3")
        val variants = readVariants(root)

        val parsedRow1 = row1 ?: throw LayoutPackParseException("Missing <row1>")
        val parsedRow2 = row2 ?: throw LayoutPackParseException("Missing <row2>")
        val parsedRow3 = row3 ?: throw LayoutPackParseException("Missing <row3>")

        validateRow(parsedRow1, "row1")
        validateRow(parsedRow2, "row2")
        validateRow(parsedRow3, "row3")

        if (qwertyLikeRaw == null) {
            isQwertyLike = parsedRow1.firstOrNull()?.equals("q", ignoreCase = true) == true
        }

        return LayoutPack(
            id = id,
            displayName = displayName,
            row1 = parsedRow1,
            row2 = parsedRow2,
            row3 = parsedRow3,
            isQwertyLike = isQwertyLike,
            bottomStyle = bottomStyle,
            source = source,
            variants = variants
        )
    }

    internal fun parseLayoutPackXmlForTest(xml: String, source: LayoutPackSource = LayoutPackSource.IMPORTED): LayoutPack {
        return parseLayoutPackXml(xml, source)
    }

    private fun parseXmlRoot(xml: String): Element {
        try {
            val factory = DocumentBuilderFactory.newInstance()
            setFactoryFlagIfSupported { factory.isNamespaceAware = false }
            setFactoryFlagIfSupported { factory.isXIncludeAware = false }
            setFactoryFlagIfSupported { factory.isExpandEntityReferences = false }
            disableFeatureIfSupported(factory, "http://xml.org/sax/features/external-general-entities")
            disableFeatureIfSupported(factory, "http://xml.org/sax/features/external-parameter-entities")
            disableFeatureIfSupported(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd")
            val builder = factory.newDocumentBuilder()
            // Never resolve external entities from user-provided XML.
            builder.setEntityResolver { _, _ -> InputSource(StringReader("")) }
            val document = builder.parse(InputSource(StringReader(xml)))
            return document.documentElement
                ?: throw LayoutPackParseException("Root tag must be <layout-pack>")
        } catch (error: LayoutPackParseException) {
            throw error
        } catch (error: Exception) {
            val parseReason = error.message
                ?.lineSequence()
                ?.firstOrNull()
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            if (parseReason != null) {
                throw LayoutPackParseException("Could not parse layout XML: $parseReason")
            }
            throw LayoutPackParseException("Could not parse layout XML (${error::class.java.simpleName})")
        }
    }

    private fun setFactoryFlagIfSupported(setter: () -> Unit) {
        try {
            setter()
        } catch (_: Throwable) {
            // Optional hardening flags vary across parser implementations.
        }
    }

    private fun disableFeatureIfSupported(factory: DocumentBuilderFactory, feature: String) {
        try {
            factory.setFeature(feature, false)
        } catch (_: Exception) {
            // Android XML parsers vary by API level/vendor; best-effort hardening only.
        }
    }

    private fun readRowTokens(root: Element, tagName: String): List<String>? {
        val nodes = root.getElementsByTagName(tagName)
        if (nodes.length <= 0) {
            return null
        }
        val raw = nodes.item(0)?.textContent?.trim().orEmpty()
        if (raw.isBlank()) {
            return emptyList()
        }
        return raw
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun readVariants(root: Element): Map<String, List<String>> {
        val variantsNodes = root.getElementsByTagName("variants")
        if (variantsNodes.length <= 0) {
            return emptyMap()
        }
        val variantsElement = variantsNodes.item(0) as? Element ?: return emptyMap()
        val keyNodes = variantsElement.getElementsByTagName("key")
        if (keyNodes.length <= 0) {
            return emptyMap()
        }

        val result = linkedMapOf<String, MutableList<String>>()
        for (index in 0 until keyNodes.length) {
            val keyElement = keyNodes.item(index) as? Element ?: continue
            val baseRaw = keyElement
                .getAttribute("value")
                .ifBlank { keyElement.getAttribute("base") }
                .trim()
            if (baseRaw.isBlank()) {
                throw LayoutPackParseException("<key> in <variants> must define 'value'")
            }
            if (baseRaw.length > 4) {
                throw LayoutPackParseException("Variant base key '$baseRaw' is too long")
            }

            val tokens = keyElement.textContent
                .trim()
                .split(Regex("\\s+"))
                .map { it.trim() }
                .filter { it.isNotBlank() }
            if (tokens.isEmpty()) {
                throw LayoutPackParseException("Variant key '$baseRaw' must define at least one token")
            }
            val invalid = tokens.firstOrNull { token -> token.length > 4 }
            if (invalid != null) {
                throw LayoutPackParseException("Variant key '$baseRaw' has invalid token '$invalid'")
            }

            val normalizedBase = baseRaw.lowercase(Locale.US)
            val merged = result.getOrPut(normalizedBase) { mutableListOf() }
            tokens.forEach { token ->
                if (!merged.contains(token)) {
                    merged.add(token)
                }
            }
        }

        return result.mapValues { it.value.toList() }
    }

    private fun validateRow(row: List<String>, rowName: String) {
        if (row.size < 5 || row.size > 12) {
            throw LayoutPackParseException("<$rowName> must contain between 5 and 12 keys")
        }
        val invalid = row.firstOrNull { token -> token.length > 4 }
        if (invalid != null) {
            throw LayoutPackParseException("<$rowName> has invalid token '$invalid'")
        }
    }
}
