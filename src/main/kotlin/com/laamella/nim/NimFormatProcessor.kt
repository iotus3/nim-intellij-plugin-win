package com.laamella.nim

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.ExternalFormatProcessor
import com.laamella.nim.settings.NimSettings
import java.io.IOException
import java.nio.file.Files

/**
 * Delegates Reformat Code (Ctrl+Alt+L) to nph (or nimpretty) by writing the file to a temp file,
 * running the formatter on it, and replacing the document contents.
 * [indent] is a no-op stub — Enter-key indentation is handled by [NimLineIndentProvider],
 * not by ExternalFormatProcessor (which is only called during explicit reformat operations).
 */
class NimFormatProcessor : ExternalFormatProcessor {
    override fun getId() = "nimpretty"

    override fun activeForFile(source: PsiFile) = source is NimFile

    override fun format(
        source: PsiFile,
        range: TextRange,
        canChangeWhiteSpacesOnly: Boolean,
        keepLineBreaks: Boolean,
        enableBulkUpdate: Boolean,
        cursorOffset: Int
    ): TextRange? {
        val original = source.text
        val tempFile = Files.createTempFile("nim_fmt_", ".nim").toFile()
        try {
            tempFile.writeText(original)
            val process = try {
                ProcessBuilder(NimSettings.getInstance().formatter(), tempFile.absolutePath).redirectErrorStream(true).start()
            } catch (_: IOException) {
                NotificationGroupManager.getInstance()
                    .getNotificationGroup("Nim")
                    .createNotification("nph/nimpretty not found — install with: nimble install nph", NotificationType.WARNING)
                    .notify(source.project)
                return null
            }
            process.waitFor()
            if (process.exitValue() != 0) return null
            val formatted = tempFile.readText()
            if (formatted == original) return range
            val document = PsiDocumentManager.getInstance(source.project).getDocument(source) ?: return null
            document.setText(formatted)
            return TextRange(0, formatted.length)
        } catch (_: Exception) {
            return null
        } finally {
            tempFile.delete()
        }
    }

    override fun indent(source: PsiFile, lineStartOffset: Int): String? = null
}
