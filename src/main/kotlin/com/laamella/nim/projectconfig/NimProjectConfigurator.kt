package com.laamella.nim.projectconfig

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.laamella.nim.settings.NimSettings

enum class NimDirKind { SRC, TEST, EXCLUDE }

/**
 * Evaluates the Nim project's .nimble project file on project startup
 * and whenever the .nimble file is created or modified.
 */
class NimProjectConfigurator : ProjectActivity {
    override suspend fun execute(project: Project) {
        // One-shot: repair settings left incomplete by older plugin versions.
        NimSettings.getInstance().healIfNeeded()
        ApplicationManager.getApplication().invokeLater { configureNimProject(project) }
    }
}

/** Reacts to .nimble file changes and re-runs project configuration. Registered via plugin.xml projectListeners. */
class NimNimbleFileListener(private val project: Project) : BulkFileListener {
    override fun after(events: List<VFileEvent>) {
        val projectDir = project.guessProjectDir() ?: return
        val affected = events.any { e -> e.file?.parent?.url == projectDir.url && e.file?.extension == "nimble" }
        if (affected) ApplicationManager.getApplication().invokeLater { configureNimProject(project) }
    }
}

fun configureNimProject(project: Project) {
    val projectDir = project.guessProjectDir() ?: return
    val nimbleFile = projectDir.children.find { it.extension == "nimble" } ?: return

    val module = configureNimModule(project)
    configureNimDirectories(projectDir, module, nimbleFile)
    configureNimLibraries(project)
    configureNimStdlib(project)

    NotificationGroupManager.getInstance()
        .getNotificationGroup("Nim")
        .createNotification("Nimble project refreshed", NotificationType.INFORMATION)
        .notify(project)
}
