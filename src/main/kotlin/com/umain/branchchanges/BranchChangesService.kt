package com.umain.branchchanges

import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.scopeView.ScopeViewPane
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.AppExecutorUtil
import git4idea.repo.GitRepositoryManager
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Keeps the set of files added/modified on the current branch, and refreshes the
 * "Branch Changes" Project view scope when that set changes.
 */
@Service(Service.Level.PROJECT)
class BranchChangesService(private val project: Project) : Disposable {

    @Volatile
    private var paths: Set<String> = emptySet()

    @Volatile
    private var addedPaths: Set<String> = emptySet()

    @Volatile
    var baseRefs: List<String> = emptyList()
        private set

    @Volatile
    var errors: List<String> = emptyList()
        private set

    private val executor = AppExecutorUtil.createBoundedScheduledExecutorService("Branch Changes scope", 1)
    private var pending: ScheduledFuture<*>? = null

    var preferredBase: String?
        get() = PropertiesComponent.getInstance(project).getValue(BASE_REF_KEY)
        set(value) {
            val props = PropertiesComponent.getInstance(project)
            if (value.isNullOrBlank()) props.unsetValue(BASE_REF_KEY) else props.setValue(BASE_REF_KEY, value.trim())
            scheduleRefresh(0)
        }

    fun contains(file: VirtualFile): Boolean = file.path in paths

    /** True if the file didn't exist at the merge-base, i.e. it's new on this branch. */
    fun isAdded(file: VirtualFile): Boolean = file.path in addedPaths

    /** Debounced: many Git/VFS events arrive in bursts. */
    @Synchronized
    fun scheduleRefresh(delayMs: Long = 400) {
        if (project.isDisposed) return
        pending?.cancel(false)
        pending = executor.schedule({ refreshNow() }, delayMs, TimeUnit.MILLISECONDS)
    }

    private fun refreshNow() {
        if (project.isDisposed) return
        try {
            val base = preferredBase
            val results = GitRepositoryManager.getInstance(project).repositories.map { repo ->
                BranchChangesCollector.collect(project, repo, base)
            }
            val newPaths = results.flatMapTo(HashSet()) { it.paths }
            val newAdded = results.flatMapTo(HashSet()) { it.addedPaths }
            baseRefs = results.mapNotNull { it.baseRef }.distinct()
            errors = results.mapNotNull { r -> r.error?.let { "${r.repo.root.name}: $it" } }
            errors.forEach { thisLogger().info("Branch Changes: $it") }

            val scopeChanged = newPaths != paths
            val highlightChanged = newAdded != addedPaths
            paths = newPaths
            addedPaths = newAdded
            if (scopeChanged || highlightChanged) refreshProjectView(scopeChanged)
        } catch (e: Exception) {
            if (e is com.intellij.openapi.progress.ProcessCanceledException) throw e
            thisLogger().warn("Branch Changes: failed to compute changed files", e)
        }
    }

    /**
     * Re-filters the Branch Changes scope if it's showing and the file set changed;
     * otherwise just redraws the current view so the "new" highlighting updates.
     */
    private fun refreshProjectView(scopeChanged: Boolean) {
        ApplicationManager.getApplication().invokeLater({
            val view = project.getServiceIfCreated(ProjectView::class.java) ?: return@invokeLater
            val scopePane = view.getProjectViewPaneById(ScopeViewPane.ID) as? ScopeViewPane
            if (scopeChanged && scopePane != null && scopePane.selectedScope?.scopeId == BranchChangesScope.ID) {
                scopePane.updateSelectedScope()
            } else {
                view.refresh()
            }
        }, project.disposed)
    }

    override fun dispose() {
        synchronized(this) { pending?.cancel(false) }
    }

    companion object {
        private const val BASE_REF_KEY = "branchChanges.baseRef"
        fun getInstance(project: Project): BranchChangesService = project.service()
    }
}
