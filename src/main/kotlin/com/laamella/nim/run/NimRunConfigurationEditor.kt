package com.laamella.nim.run

import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

class NimRunConfigurationEditor : SettingsEditor<NimRunConfiguration>() {
    private val filePathField = TextFieldWithBrowseButton()
    private val workingDirectoryField = TextFieldWithBrowseButton()

    init {
        filePathField.addBrowseFolderListener(
            null,
            FileChooserDescriptor(true, false, false, false, false, false)
                .withFileFilter { isNimSourceFile(it.extension) }
                .withTitle("Select Nim File")
        )
        workingDirectoryField.addBrowseFolderListener(
            null,
            FileChooserDescriptor(false, true, false, false, false, false)
                .withTitle("Select Working Directory")
        )
    }

    override fun resetEditorFrom(config: NimRunConfiguration) {
        filePathField.text = config.filePath
        workingDirectoryField.text = config.workingDirectory
    }

    override fun applyEditorTo(config: NimRunConfiguration) {
        config.filePath = filePathField.text
        config.workingDirectory = workingDirectoryField.text
    }

    override fun createEditor(): JComponent = panel {
        row("Nim file:") {
            cell(filePathField).align(AlignX.FILL).comment("Run with nim r")
        }
        row("Working directory:") {
            cell(workingDirectoryField).align(AlignX.FILL)
        }
    }
}
