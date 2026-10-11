package com.laamella.nim.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import java.io.File
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Persisted plugin settings.
 *
 * [nimSdkPath] (Nim install root) and [nimbleToolchainPath] (Nimble's bin directory) are the single
 * source of truth; the executable fields hold absolute paths derived from them and stay
 * user-overridable. A blank [nimlangserverExe] selects the "nim check on save" mode.
 * Defaults come from [NimSettingsDefaults].
 */
@State(name = "NimSettings", storages = [Storage("nim-plugin.xml")])
class NimSettings : PersistentStateComponent<NimSettings> {
    var nimSdkPath: String = NimSettingsDefaults.nimSdkPath()
    var nimbleToolchainPath: String = NimSettingsDefaults.nimbleToolchainPath()
    var nimlangserverExe: String = NimSettingsDefaults.nimlangserverExe()
    var nimbleExe: String = NimSettingsDefaults.nimbleExe()
    var formatterExe: String = NimSettingsDefaults.formatterExe()
    var nimsuggestExe: String = NimSettingsDefaults.nimsuggestExe()

    /** Migration-only placeholder for the pre-0.6.0 single-directory setting; cleared after load. */
    var nimbleBinPath: String = ""

    private val healed = AtomicBoolean(false)

    /** Set when a legacy bare language server name could not be resolved; see [healIfNeeded]. */
    @Volatile
    private var pendingLangServerDetection = false

    /** True once a persisted state was loaded, so a fresh install can keep the LSP enabled. */
    @Volatile
    private var loadedFromState = false

    fun effectiveToolchainPath(): String =
        nimbleToolchainPath.trim().ifBlank { NimToolchainDetector.defaultToolchainPath() }

    fun pkgs2Dir(): Path = NimToolPaths.pkgs2Dir(effectiveToolchainPath())

    /** nim always lives in the SDK; it has no dedicated setting. */
    fun nim(): String {
        val name = exeName("nim")
        return NimToolPaths.pathOrNull(nimSdkPath)?.resolve("bin")?.resolve(name)?.toString() ?: name
    }

    fun nimlangserver(): String = nimlangserverExe.trim().ifBlank { exeName("nimlangserver") }
    fun nimble(): String = nimbleExe.trim().ifBlank { exeName("nimble") }
    fun formatter(): String = formatterExe.trim().ifBlank { exeName("nimpretty") }
    fun nimsuggest(): String = nimsuggestExe.trim().ifBlank { exeName("nimsuggest") }

    private fun exeName(base: String): String = NimSettingsDefaults.exeName(base)

    /** PATH for subprocesses: toolchain bin, then SDK bin, then the inherited PATH. */
    fun pathWithToolchain(): String {
        val extra = buildList {
            add(effectiveToolchainPath())
            NimToolPaths.pathOrNull(nimSdkPath)?.let { add(it.resolve("bin").toString()) }
        }
        return (extra + (System.getenv("PATH") ?: ""))
            .filter { it.isNotBlank() }
            .joinToString(File.pathSeparator)
    }

    /**
     * Overwrites the fields with a detection result. [overwriteLangServer] stays false for
     * non-destructive fills so an explicitly chosen "nim check on save" is preserved.
     */
    fun applyDetected(detected: NimToolchainDetector.Detected, overwriteLangServer: Boolean = true) {
        nimSdkPath = detected.sdkPath
        nimbleToolchainPath = detected.toolchainPath
        nimbleExe = detected.nimble
        formatterExe = detected.formatter
        nimsuggestExe = detected.nimsuggest
        if (overwriteLangServer) nimlangserverExe = detected.langServer
    }

    /**
     * One-shot repair on startup, mainly for settings written by older plugin versions.
     * The language server is only touched for a fresh install or an unresolvable legacy name,
     * so an explicit "nim check on save" survives.
     */
    fun healIfNeeded() {
        if (!healed.compareAndSet(false, true)) return
        val langServerNeeded = pendingLangServerDetection || !loadedFromState
        if (!langServerNeeded && nimSdkPath.isNotBlank() && nimbleExe.isNotBlank() && nimsuggestExe.isNotBlank()) return
        runCatching {
            val detected = NimToolchainDetector.detect(nimSdkPath, nimbleToolchainPath)
            if (nimSdkPath.isBlank()) nimSdkPath = detected.sdkPath
            if (nimbleExe.isBlank()) nimbleExe = detected.nimble
            if (formatterExe.isBlank()) formatterExe = detected.formatter
            if (nimsuggestExe.isBlank()) nimsuggestExe = detected.nimsuggest
            if (langServerNeeded && nimlangserverExe.isBlank()) {
                // The expected path of a not-yet-installed server still enables the LSP and its
                // auto-install, matching the pre-0.6.0 default.
                nimlangserverExe = detected.langServer.ifBlank {
                    NimToolPaths.expectedPath(
                        NimTool.LANG_SERVER, "nimlangserver", nimSdkPath, effectiveToolchainPath()
                    )
                }
            }
        }
        pendingLangServerDetection = false
    }

    override fun getState(): NimSettings = this

    override fun loadState(state: NimSettings) {
        XmlSerializerUtil.copyBean(state, this)
        loadedFromState = true
        migrateLegacy()
    }

    /** Rebuilds the absolute-path model from the pre-0.6.0 `nimbleBinPath` + bare exe names. */
    private fun migrateLegacy() {
        val legacyBin = nimbleBinPath.trim()
        if (legacyBin.isNotBlank()) {
            if (nimbleToolchainPath.isBlank()) nimbleToolchainPath = legacyBin
            nimbleBinPath = ""
        }
        // A bare language server name meant "LSP was on": resolve it instead of silently dropping
        // into the blank = "nim check on save" mode.
        val legacyLangServer = nimlangserverExe.trim()
        if (legacyLangServer.isNotBlank() && legacyLangServer.absoluteOrBlank().isEmpty()) {
            val candidate = NimToolPaths.pathOrNull(effectiveToolchainPath())?.resolve(legacyLangServer)
            if (candidate != null && NimToolPaths.isRunnable(candidate)) {
                nimlangserverExe = candidate.toString()
            } else {
                nimlangserverExe = ""
                pendingLangServerDetection = true
            }
        }
        // Other bare names are not usable as absolute paths; healIfNeeded() refills them.
        nimbleExe = nimbleExe.absoluteOrBlank()
        formatterExe = formatterExe.absoluteOrBlank()
        nimsuggestExe = nimsuggestExe.absoluteOrBlank()
    }

    /** Keeps absolute paths as-is; a legacy bare file name becomes blank for [healIfNeeded] to refill. */
    private fun String.absoluteOrBlank(): String =
        if (runCatching { Path.of(this).isAbsolute }.getOrDefault(false)) this else ""

    companion object {
        fun getInstance(): NimSettings =
            ApplicationManager.getApplication().getService(NimSettings::class.java)
    }
}
