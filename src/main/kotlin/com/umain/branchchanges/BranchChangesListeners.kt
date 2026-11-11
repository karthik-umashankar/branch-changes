package com.umain.branchchanges

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vcs.changes.ChangeListListener
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener

/** Initial computation once the project has opened. */
class BranchChangesStartup : ProjectActivity {
    override suspend fun execute(project: Project) {
        BranchChangesService.getInstance(project).scheduleRefresh(0)
    }
}

/** Branch switch, commit, rebase, fetch (base branch moved), etc. */
class BranchChangesGitListener(private val project: Project) : GitRepositoryChangeListener {
    override fun repositoryChanged(repository: GitRepository) {
        BranchChangesService.getInstance(project).scheduleRefresh()
    }
}

/** Uncommitted edits, new files and reverts, after the IDE's own VCS status refresh. */
class BranchChangesChangeListListener(private val project: Project) : ChangeListListener {
    override fun changeListUpdateDone() {
        BranchChangesService.getInstance(project).scheduleRefresh()
    }
}
