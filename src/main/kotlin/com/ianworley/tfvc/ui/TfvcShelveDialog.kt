package com.ianworley.tfvc.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import java.awt.BorderLayout
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

data class TfvcShelveRequest(
    val name: String,
    val comment: String,
    val replaceExisting: Boolean,
    val removePendingChangesAfterShelve: Boolean,
)

class TfvcShelveDialog(project: Project) : DialogWrapper(project) {
    private val nameField = JBTextField(defaultShelvesetName())
    private val commentArea = JBTextArea(4, 40)
    private val replaceCheckBox = JBCheckBox("Replace existing shelveset")
    private val moveCheckBox = JBCheckBox("Remove pending changes after shelve")

    init {
        title = "TFVC Shelve"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(0, 8))
        val form = JPanel()
        form.layout = javax.swing.BoxLayout(form, javax.swing.BoxLayout.Y_AXIS)

        form.add(JLabel("Shelveset name"))
        form.add(nameField)
        form.add(JLabel("Comment"))
        form.add(JBScrollPane(commentArea))
        form.add(replaceCheckBox)
        form.add(moveCheckBox)

        panel.add(form, BorderLayout.CENTER)
        return panel
    }

    fun request(): TfvcShelveRequest = TfvcShelveRequest(
        name = nameField.text.trim(),
        comment = commentArea.text.trim(),
        replaceExisting = replaceCheckBox.isSelected,
        removePendingChangesAfterShelve = moveCheckBox.isSelected,
    )

    override fun doValidate(): ValidationInfo? {
        return if (nameField.text.trim().isEmpty()) {
            ValidationInfo("Shelveset name is required.", nameField)
        } else {
            null
        }
    }

    private fun defaultShelvesetName(): String =
        "shelveset-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))}"
}
