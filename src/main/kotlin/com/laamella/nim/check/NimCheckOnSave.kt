package com.laamella.nim.check

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.laamella.nim.settings.NimSettings
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

enum class NimCheckSeverity { ERROR, WARNING, HINT }

data class NimCheckProblem(
    val filePath: String,
    val line: Int,   // 1-based
    val col: Int,    // 1-based
    val severity: NimCheckSeverity,
    val message: String,
)

private val PROBLEM_LINE = Regex("""^(.+?)\((\d+), (\d+)\) (Error|Warning|Hint): (.*)$""")
private val GENERATED_ID = Regex("""\btmp_\d+\b""")

/** Strips the numeric suffix off compiler-generated temp names (e.g. `tmp_587203598` → `tmp`),
 *  since it's freshly generated per compilation and carries no useful information. */
private fun stripGeneratedIds(message: String) = message.replace(GENERATED_ID, "tmp")

/**
 * Parses `nim check` output into problems. Lines that don't carry a position
 * (config hints, dot progress lines) are dropped; indented lines continue the
 * previous problem's message (e.g. type mismatch candidate lists). Generated temp
 * variable IDs (`tmp_NNN`) are stripped from messages, since nim reprints the same
 * diagnostic once per generic instantiation / import path with a freshly generated
 * name each time; stripping them first lets the plain duplicate collapse below merge
 * those repeats down to one.
 */
internal fun parseNimCheckOutput(output: String): List<NimCheckProblem> {
    val problems = mutableListOf<NimCheckProblem>()
    for (line in output.lineSequence()) {
        val m = PROBLEM_LINE.matchEntire(line)
        if (m != null) {
            val (path, ln, col, sev, msg) = m.destructured
            problems += NimCheckProblem(
                path, ln.toInt(), col.toInt(), NimCheckSeverity.valueOf(sev.uppercase()), stripGeneratedIds(msg)
            )
        } else if (problems.isNotEmpty() && line.isNotBlank() && line.first().isWhitespace()) {
            val last = problems.removeLast()
            problems += last.copy(message = last.message + "\n" + stripGeneratedIds(line.trim()))
        }
    }
    return problems.distinct()
}

/** Text range to underline for a problem: the identifier at (line, col), or a single character. */
internal fun problemRange(document: Document, problem: NimCheckProblem): TextRange? {
    if (problem.line < 1 || problem.line > document.lineCount) return null
    val lineStart = document.getLineStartOffset(problem.line - 1)
    val lineEnd = document.getLineEndOffset(problem.line - 1)
    if (lineStart == lineEnd) return null // empty line
    val start = (lineStart + problem.col - 1).coerceIn(lineStart, lineEnd - 1)
    val text = document.charsSequence
    var end = start
    while (end < lineEnd && (text[end].isLetterOrDigit() || text[end] == '_')) end++
    if (end == start) end = start + 1
    return TextRange(start, end)
}

/**
 * Runs `nim check` on every saved `.nim` file and marks the reported problems in the editor.
 * Active only when no language server executable is configured ([NimSettings.nimlangserverExe]
 * blank) — otherwise diagnostics come from the LSP server.
 */
class NimCheckOnSaveListener(private val project: Project) : BulkFileListener {
    override fun after(events: List<VFileEvent>) {
        if (NimSettings.getInstance().nimlangserverExe.isNotBlank()) return
        events.asSequence()
            .filter { it is VFileContentChangeEvent && it.isFromSave }
            .mapNotNull { it.file }
            .filter { it.extension == "nim" }
            .distinctBy { it.path }
            .forEach { NimCheckOnSave.runNimCheck(project, it) }
    }
}

/**
 * Runs `nim check` as soon as a `.nim` file is opened, so problems show up without
 * waiting for the first save. Same [NimSettings.nimlangserverExe]-blank gate as
 * [NimCheckOnSaveListener].
 */
class NimCheckOnOpenListener(private val project: Project) : FileEditorManagerListener {
    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        if (NimSettings.getInstance().nimlangserverExe.isNotBlank()) return
        if (file.extension != "nim") return
        NimCheckOnSave.runNimCheck(project, file)
    }
}

object NimCheckOnSave {
    private val problemsCache = ConcurrentHashMap<String, List<NimCheckProblem>>()
    private val runningChecks = ConcurrentHashMap<String, Process>()
    private val warnedMissingNim = AtomicBoolean(false)

    /** Cached results for [NimCheckExternalAnnotator], which turns them into real editor annotations. */
    fun problemsFor(path: String): List<NimCheckProblem> = problemsCache[path] ?: emptyList()

    /** Re-arms the "nim not found" balloon, e.g. after the user fixed the toolchain path. */
    fun resetMissingNimWarning() = warnedMissingNim.set(false)

    fun runNimCheck(project: Project, file: VirtualFile) {
        val settings = NimSettings.getInstance()
        ApplicationManager.getApplication().executeOnPooledThread {
            runningChecks.remove(file.path)?.destroy()
            val pb = ProcessBuilder(settings.nim(), "check", file.path)
                .directory(File(project.guessProjectDir()?.path ?: "."))
                .redirectErrorStream(true)
            pb.environment()["PATH"] = settings.pathWithToolchain()
            val process = try {
                pb.start()
            } catch (_: IOException) {
                if (warnedMissingNim.compareAndSet(false, true)) {
                    NotificationGroupManager.getInstance()
                        .getNotificationGroup("Nim")
                        .createNotification(
                            "nim not found — set the Nim SDK path in Settings → Languages & Frameworks → Nim",
                            NotificationType.WARNING
                        )
                        .notify(project)
                }
                return@executeOnPooledThread
            }
            runningChecks[file.path] = process
            val output = process.inputStream.bufferedReader().readText() // non-zero exit just means problems found
            process.waitFor()
            if (runningChecks.remove(file.path, process)) {
                val wanted = Path.of(file.path).normalize()
                val problems = parseNimCheckOutput(output)
                    .filter { runCatching { Path.of(it.filePath).normalize() == wanted }.getOrDefault(false) }
                ApplicationManager.getApplication().invokeLater { applyProblems(project, file, problems) }
            }
        }
    }

    private fun applyProblems(project: Project, file: VirtualFile, problems: List<NimCheckProblem>) {
        if (project.isDisposed || !file.isValid) return
        problemsCache[file.path] = problems
        // Triggers a new highlighting pass so NimCheckExternalAnnotator picks up the fresh results.
        DaemonCodeAnalyzer.getInstance(project).restart(file)
    }
}
