package com.ianworley.tfvc.parsing

import com.ianworley.tfvc.tf.WorkspaceContext
import com.ianworley.tfvc.tf.WorkspaceType
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.nio.file.Paths
import javax.xml.parsers.DocumentBuilderFactory

object TfWorkspacesXmlParser {
    fun parse(output: String): List<WorkspaceContext> {
        val trimmed = output.trim()
        if (trimmed.isEmpty()) {
            return emptyList()
        }

        val documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val document = documentBuilder.parse(ByteArrayInputStream(trimmed.toByteArray()))
        val workspaceNodes = document.getElementsByTagName("Workspace")

        return buildList {
            for (index in 0 until workspaceNodes.length) {
                val element = workspaceNodes.item(index) as? Element ?: continue
                val name = element.getAttribute("name").ifBlank { element.getAttribute("Name") }
                val owner = element.getAttribute("owner").ifBlank { element.getAttribute("Owner") }
                val computer = element.getAttribute("computer").ifBlank { element.getAttribute("Computer") }
                val location = element.getAttribute("location")
                    .ifBlank { element.getAttribute("Location") }
                    .ifBlank { element.getAttribute("type") }
                val collectionUrl = childText(element, "Collection")
                    ?: childText(element, "Repository")
                    ?: element.getAttribute("collection")
                val folders = element.getElementsByTagName("WorkingFolder")

                for (folderIndex in 0 until folders.length) {
                    val folder = folders.item(folderIndex) as? Element ?: continue
                    val localItem = folder.getAttribute("localItem")
                        .ifBlank { folder.getAttribute("LocalItem") }
                    val serverItem = folder.getAttribute("serverItem")
                        .ifBlank { folder.getAttribute("ServerItem") }
                    if (localItem.isBlank() || serverItem.isBlank()) {
                        continue
                    }

                    add(
                        WorkspaceContext(
                            localRoot = Paths.get(localItem).normalize(),
                            serverPath = serverItem,
                            workspaceName = name,
                            owner = owner.ifBlank { computer },
                            collectionUrl = collectionUrl.orEmpty(),
                            workspaceType = when {
                                location.contains("local", ignoreCase = true) -> WorkspaceType.LOCAL
                                location.contains("server", ignoreCase = true) -> WorkspaceType.SERVER
                                else -> WorkspaceType.UNKNOWN
                            },
                        ),
                    )
                }
            }
        }
    }

    private fun childText(element: Element, name: String): String? {
        val children = element.getElementsByTagName(name)
        if (children.length == 0) {
            return null
        }
        return children.item(0)?.textContent?.trim()
    }
}
