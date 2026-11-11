package com.umain.branchchanges

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import git4idea.repo.GitRepositoryManager

/** Git > Branch Changes: Set Base Branch… — overrides the auto-detected base for this project. */
class SetBaseBranchAction : DumbAwareAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible =
            project != null && GitRepositoryManager.getInstance(project).repositories.isNotEmpty()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = BranchChangesService.getInstance(project)
        val detected = service.baseRefs.joinToString(", ").ifEmpty { "none found" }

        val input = Messages.showInputDialog(
            project,
            "Branch or commit the \"Branch Changes\" view compares against (e.g. origin/develop).\n" +
                "Leave empty to auto-detect. Currently using: $detected",
            "Branch Changes: Base Branch",
            null,
            service.preferredBase ?: "",
            null,
        ) ?: return // cancelled

        service.preferredBase = input.trim().ifEmpty { null }
    }
}
