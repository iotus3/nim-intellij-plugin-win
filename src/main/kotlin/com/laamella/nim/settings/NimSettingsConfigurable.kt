package com.laamella.nim.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.laamella.nim.check.NimCheckOnSave
import com.redhat.devtools.lsp4ij.LanguageServerManager
import javax.swing.JComponent
import javax.swing.JTextField
import javax.swing.event.DocumentEvent

class NimSettingsConfigurable : Configurable {
    private var sdkPath: TextFieldWithBrowseButton? = null
    private var toolchainPath: TextFieldWithBrowseButton? = null
    private var nimlangserverExe: JTextField? = null
    private var nimbleExe: JTextField? = null
    private var formatterExe: JTextField? = null

    /** True after an explicit "nim check on save" choice, so auto-fill leaves the server blank. */
    private var langServerDisabled = false

    override fun getDisplayName(): String = "Nim"

    override fun createComponent(): JComponent {
        sdkPath = directoryField(
            "Select Nim SDK Directory",
            "Nim installation root containing bin/ and lib/"
        ) { autoDetectExecutables(nonDestructive = true) }
        toolchainPath = directoryField(
            "Select Nim Toolchain Directory",
            "Nimble's bin directory holding the installed tools, usually ~/.nimble/bin"
        ) { autoDetectExecutables(nonDestructive = true) }
        nimlangserverExe = JTextField()
        nimbleExe = JTextField()
        formatterExe = JTextField()

        return panel {
            group("Paths") {
                row("Nim SDK path:") { cell(sdkPath!!).align(AlignX.FILL) }
                row("Nim toolchain path:") { cell(toolchainPath!!).align(AlignX.FILL) }
            }
            @Suppress("DialogTitleCapitalization")
            group("Executables (absolute paths, auto-filled from the paths above)") {
                row("nimlangserver or nimlsp executable name:") { cell(nimlangserverExe!!).align(AlignX.FILL) }
                row("nimble executable name:") { cell(nimbleExe!!).align(AlignX.FILL) }
                row("nph or nimpretty executable name:") { cell(formatterExe!!).align(AlignX.FILL) }
                row { button("Auto-detect executables") { autoDetectExecutables(nonDestructive = true) } }
            }
            @Suppress("DialogTitleCapitalization")
            group("Language server") {
                row {
                    @Suppress("DialogTitleCapitalization")
                    button("Set to use nimlangserver") { setLangServer("nimlangserver") }
                    @Suppress("DialogTitleCapitalization")
                    button("Set to use nimlsp") { setLangServer("nimlsp") }
                    // Blank executable disables the server; NimCheckOnSaveListener takes over.
                    @Suppress("DialogTitleCapitalization")
                    button("Set to use nim check on save") { setLangServer(null) }
                }
            }
            @Suppress("DialogTitleCapitalization")
            group("Formatter") {
                row {
                    @Suppress("DialogTitleCapitalization")
                    button("Set to use nph") { setFormatter("nph") }
                    @Suppress("DialogTitleCapitalization")
                    button("Set to use nimpretty") { setFormatter("nimpretty") }
                }
            }
            group("Reset") {
                row { button("Reset to Defaults") { resetToDefaults() } }
            }
        }
    }

    private fun directoryField(
        title: String,
        description: String,
        onBrowse: () -> Unit,
    ): TextFieldWithBrowseButton {
        val field = TextFieldWithBrowseButton()
        val descriptor = FileChooserDescriptor(false, true, false, false, false, false)
            .withTitle(title)
            .withDescription(description)
        field.addBrowseFolderListener(null, descriptor)
        // Fires after the chooser has written the selected path into the field.
        field.textField.document.addDocumentListener(object : DocumentAdapter() {
            private var pending = false
            override fun textChanged(e: DocumentEvent) {
                if (pending) return
                pending = true
                ApplicationManager.getApplication().invokeLater {
                    pending = false
                    onBrowse()
                }
            }
        })
        return field
    }

    private fun currentSdk(): String = sdkPath?.text?.trim().orEmpty()

    private fun currentToolchain(): String =
        toolchainPath?.text?.trim().orEmpty().ifBlank { NimToolchainDetector.defaultToolchainPath() }

    /** Detection reads directories and forks `where`/`which`; nothing may escape into the EDT. */
    private fun detectSafely(sdk: String = "", toolchain: String = ""): NimToolchainDetector.Detected =
        runCatching { NimToolchainDetector.detect(sdk, toolchain) }
            .getOrElse { NimToolchainDetector.Detected(sdk, toolchain, "", "", "") }

    /** With [nonDestructive] only blank fields are filled, preserving manual edits. */
    private fun autoDetectExecutables(nonDestructive: Boolean) {
        val detected = detectSafely(currentSdk(), currentToolchain())
        fill(nimbleExe, detected.nimble, nonDestructive)
        fill(formatterExe, detected.formatter, nonDestructive)
        if (!langServerDisabled) fill(nimlangserverExe, detected.langServer, nonDestructive)
        // Echo the resolved roots into still-blank fields so the user sees what was detected.
        fill(sdkPath?.textField, detected.sdkPath, nonDestructive = true)
        fill(toolchainPath?.textField, detected.toolchainPath, nonDestructive = true)
    }

    /** Writes only real changes: assigning an identical value re-fires the document listener. */
    private fun fill(field: JTextField?, value: String, nonDestructive: Boolean) {
        if (field == null || value.isBlank() || field.text == value) return
        if (nonDestructive && field.text.isNotBlank()) return
        field.text = value
    }

    private fun setLangServer(variant: String?) {
        langServerDisabled = variant == null
        nimlangserverExe?.text = if (variant == null) "" else resolveOrExpected(NimTool.LANG_SERVER, variant)
    }

    private fun setFormatter(variant: String) {
        formatterExe?.text = resolveOrExpected(NimTool.FORMATTER, variant)
    }

    private fun resolveOrExpected(tool: NimTool, variant: String): String {
        val sdk = currentSdk()
        val toolchain = currentToolchain()
        return runCatching { NimToolPaths.resolveVariant(tool, variant, sdk, toolchain) }.getOrNull()?.toString()
            ?: NimToolPaths.expectedPath(tool, variant, sdk, toolchain)
    }

    private fun resetToDefaults() {
        val detected = detectSafely()
        sdkPath?.text = detected.sdkPath
        toolchainPath?.text = detected.toolchainPath.ifBlank { NimToolchainDetector.defaultToolchainPath() }
        nimlangserverExe?.text = detected.langServer
        nimbleExe?.text = detected.nimble
        formatterExe?.text = detected.formatter
        langServerDisabled = detected.langServer.isBlank()
    }

    override fun isModified(): Boolean {
        val s = NimSettings.getInstance()
        // A blank toolchain field and the default ~/.nimble/bin are the same value.
        val uiToolchain = toolchainPath?.text?.trim().orEmpty().ifBlank { NimToolchainDetector.defaultToolchainPath() }
        return sdkPath?.text?.trim() != s.nimSdkPath
            || uiToolchain != s.effectiveToolchainPath()
            || nimlangserverExe?.text?.trim() != s.nimlangserverExe
            || nimbleExe?.text?.trim() != s.nimbleExe
            || formatterExe?.text?.trim() != s.formatterExe
    }

    override fun apply() {
        val s = NimSettings.getInstance()
        val beforeCommand = s.nimlangserver()
        val beforePath = s.pathWithToolchain()

        s.nimSdkPath = currentSdk()
        s.nimbleToolchainPath = toolchainPath?.text?.trim().orEmpty()
        s.nimlangserverExe = nimlangserverExe?.text?.trim().orEmpty()
        s.formatterExe = formatterExe?.text?.trim().orEmpty()

        // nimble is always needed; the server may legitimately stay blank.
        val detected by lazy { detectSafely(s.nimSdkPath, s.nimbleToolchainPath) }
        s.nimbleExe = nimbleExe?.text?.trim().orEmpty().ifBlank { detected.nimble }
        if (s.formatterExe.isBlank()) s.formatterExe = detected.formatter

        NimCheckOnSave.resetMissingNimWarning()

        // isEnabled() only blocks new starts, so a changed command or PATH needs an explicit restart.
        val afterCommand = s.nimlangserver()
        val afterPath = s.pathWithToolchain()
        if (beforeCommand != afterCommand || beforePath != afterPath) {
            ProjectManager.getInstance().openProjects.forEach { project ->
                val manager = LanguageServerManager.getInstance(project)
                runCatching { manager.stop("nim") }
                if (afterCommand.isNotBlank()) runCatching { manager.start("nim") }
            }
        }
    }

    override fun reset() {
        val s = NimSettings.getInstance()
        sdkPath?.text = s.nimSdkPath
        toolchainPath?.text = s.effectiveToolchainPath()
        nimlangserverExe?.text = s.nimlangserverExe
        nimbleExe?.text = s.nimbleExe
        formatterExe?.text = s.formatterExe
        langServerDisabled = s.nimlangserverExe.isBlank()
        // Nothing configured yet: offer detected values. The language server is included so a fresh
        // install keeps the pre-0.6.0 behaviour (LSP enabled, nimlangserver auto-installed when missing).
        if (s.nimSdkPath.isBlank() && s.nimbleExe.isBlank()) {
            langServerDisabled = false
            autoDetectExecutables(nonDestructive = true)
            // Not installed yet: fall back to the expected path, exactly the pre-0.6.0 default.
            fill(
                nimlangserverExe,
                NimToolPaths.expectedPath(NimTool.LANG_SERVER, "nimlangserver", currentSdk(), currentToolchain()),
                nonDestructive = true
            )
        }
    }

    override fun disposeUIResources() {
        sdkPath = null
        toolchainPath = null
        nimlangserverExe = null
        nimbleExe = null
        formatterExe = null
    }
}
