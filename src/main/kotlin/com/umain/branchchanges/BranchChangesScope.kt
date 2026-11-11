package com.umain.branchchanges

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.scope.packageSet.CustomScopesProvider
import com.intellij.psi.search.scope.packageSet.FilteredPackageSet
import com.intellij.psi.search.scope.packageSet.NamedScope

/** The "Branch Changes" entry in the Project tool window's view dropdown (and anywhere scopes are listed). */
class BranchChangesScope : NamedScope(ID, { ID }, AllIcons.Vcs.Branch, BranchChangesPackageSet()) {
    companion object {
        const val ID = "Branch Changes"
    }
}

private class BranchChangesPackageSet : FilteredPackageSet(BranchChangesScope.ID) {
    override fun contains(file: VirtualFile, project: Project): Boolean =
        BranchChangesService.getInstance(project).contains(file)
}

class BranchChangesScopesProvider : CustomScopesProvider {
    private val scopes = listOf<NamedScope>(BranchChangesScope())
    override fun getCustomScopes(): List<NamedScope> = scopes
}
