package com.umain.branchchanges

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ProjectViewNodeDecorator
import com.intellij.openapi.vcs.FileStatus
import com.intellij.psi.PsiElement

/**
 * In every Project tool window view, shows files that are new on this branch
 * (added, copied or untracked since the merge-base) with a green name and a grey "new" label,
 * even after they've been committed.
 */
class NewFileDecorator : ProjectViewNodeDecorator {

    override fun decorate(node: ProjectViewNode<*>, data: PresentationData) {
        val project = node.project ?: return
        if (project.isDisposed) return
        // Kotlin/Java show a single-class file as a class node, which reports no virtualFile,
        // so fall back to the file containing the node's PSI element.
        val file = node.virtualFile
            ?: (node.value as? PsiElement)?.takeIf { it.isValid }?.containingFile?.virtualFile
            ?: return
        if (file.isDirectory || !BranchChangesService.getInstance(project).isAdded(file)) return

        // The IDE's own "added" colour, so it follows the current theme.
        FileStatus.ADDED.color?.let { data.forcedTextForeground = it }

        val existing = data.locationString
        data.locationString = if (existing.isNullOrBlank()) LABEL else "$LABEL · $existing"
    }

    private companion object {
        const val LABEL = "new"
    }
}
