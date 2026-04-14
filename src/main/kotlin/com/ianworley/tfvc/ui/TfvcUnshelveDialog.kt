package com.ianworley.tfvc.ui

import com.ianworley.tfvc.tf.ShelvesetSummary
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBTextField
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

data class TfvcUnshelveRequest(
    val shelveset: ShelvesetSummary,
    val ownerFilter: String,
    val removeAfterUnshelve: Boolean,
)

class TfvcUnshelveDialog(
    project: Project,
    initialOwner: String,
    private val loadShelvesets: (String) -> List<ShelvesetSummary>,
) : DialogWrapper(project) {
    private val ownerField = JBTextField(initialOwner)
    private val removeAfterCheckBox = JBCheckBox("Remove shelveset after unshelve")
    private val listModel = CollectionListModel<ShelvesetSummary>()
    private val shelvesetList = JBList(listModel)

    init {
        title = "TFVC Unshelve"
        reloadShelvesets(initialOwner)
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(0, 8))
        val top = JPanel()
        top.layout = javax.swing.BoxLayout(top, javax.swing.BoxLayout.Y_AXIS)
        top.add(JLabel("Owner filter"))
        top.add(ownerField)
        top.add(removeAfterCheckBox)

        val listPanel = ToolbarDecorator.createDecorator(shelvesetList)
            .disableAddAction()
            .disableRemoveAction()
            .setEditAction {
                reloadShelvesets(ownerField.text.trim().ifBlank { "*" })
            }
            .createPanel()

        panel.add(top, BorderLayout.NORTH)
        panel.add(listPanel, BorderLayout.CENTER)
        return panel
    }

    override fun doValidate(): ValidationInfo? {
        return if (shelvesetList.selectedValue == null) {
            ValidationInfo("Select a shelveset to unshelve.", shelvesetList)
        } else {
            null
        }
    }

    fun request(): TfvcUnshelveRequest = TfvcUnshelveRequest(
        shelveset = shelvesetList.selectedValue,
        ownerFilter = ownerField.text.trim().ifBlank { "*" },
        removeAfterUnshelve = removeAfterCheckBox.isSelected,
    )

    private fun reloadShelvesets(owner: String) {
        val loaded = ProgressManager.getInstance().runProcessWithProgressSynchronously<List<ShelvesetSummary>, RuntimeException>(
            {
                loadShelvesets(owner)
            },
            "Loading TFVC Shelvesets",
            true,
            project,
        )
        listModel.replaceAll(loaded)
        if (loaded.isNotEmpty()) {
            shelvesetList.selectedIndex = 0
        }
    }
}
