package com.umain.branchchanges

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitCommandResult
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepository
import java.io.ByteArrayOutputStream

/** What was found in one Git repository of the project. */
data class RepoChanges(
    val repo: GitRepository,
    val baseRef: String?,
    /** Absolute, '/'-separated paths (same form as [VirtualFile.getPath]). */
    val paths: Set<String>,
    val error: String?,
    /** Subset of [paths] that didn't exist at the merge-base (added, copied, or untracked). */
    val addedPaths: Set<String> = emptySet(),
)

/**
 * Finds the files added or modified on the current branch, relative to the
 * merge-base with a base branch. Runs `git` through the IDE's Git integration,
 * so it uses the same executable and settings as the rest of the IDE.
 * Must be called off the UI thread.
 */
object BranchChangesCollector {

    private val FALLBACK_BASES = listOf(
        "origin/main", "origin/master", "origin/develop",
        "upstream/main", "upstream/master",
        "main", "master", "develop",
    )

    fun collect(project: Project, repo: GitRepository, preferredBase: String?): RepoChanges {
        val root = repo.root

        val preferredFound = preferredBase?.takeIf { refExists(project, root, it) }
        val base = preferredFound
            ?: detectBaseRef(project, root)
            ?: return RepoChanges(repo, null, emptySet(), "no base branch found (tried origin/HEAD, main, master, develop)")
        val note = if (preferredBase != null && preferredFound == null) "'$preferredBase' not found, used $base instead" else null

        val mergeBaseResult = run(project, root, GitCommand.MERGE_BASE, "HEAD", base)
        val mergeBase = mergeBaseResult.output.firstOrNull()?.trim()
        if (!mergeBaseResult.success() || mergeBase.isNullOrEmpty()) {
            return RepoChanges(repo, base, emptySet(), "could not find a merge-base with $base")
        }

        // Merge-base vs. working tree: committed + staged + unstaged changes to tracked files.
        // A = added, C = copied, M = modified, R = renamed, T = type changed. Deletions are skipped.
        // Output lines: "M<TAB>path", or "R100<TAB>old<TAB>new" for renames/copies.
        val tracked = run(
            project, root, GitCommand.DIFF,
            "--name-status", "--diff-filter=ACMRT", "--no-ext-diff", "--no-color", mergeBase, "--",
        )
        if (!tracked.success()) {
            return RepoChanges(repo, base, emptySet(), tracked.errorOutputAsJoinedString.ifBlank { "git diff failed" })
        }

        // Brand-new files that haven't been `git add`-ed yet.
        val untracked = run(project, root, GitCommand.LS_FILES, "--others", "--exclude-standard")

        val rootPath = root.path.trimEnd('/')
        fun abs(rel: String) = "$rootPath/${unquote(rel)}"

        val paths = HashSet<String>()
        val added = HashSet<String>()
        for (line in tracked.output) {
            val fields = line.trimEnd('\r').split('\t')
            if (fields.size < 2) continue
            val status = fields[0].firstOrNull() ?: continue
            val path = abs(fields.last()) // new path for renames/copies
            paths += path
            if (status == 'A' || status == 'C') added += path
        }
        if (untracked.success()) {
            untracked.output.map { it.trimEnd('\r') }.filter { it.isNotBlank() }.forEach {
                val path = abs(it)
                paths += path
                added += path
            }
        }
        return RepoChanges(repo, base, paths, note, added)
    }

    /** origin/HEAD if the remote has one, otherwise the first common base branch name that exists. */
    fun detectBaseRef(project: Project, root: VirtualFile): String? {
        val originHead = run(project, root, GitCommand.REV_PARSE, "--abbrev-ref", "origin/HEAD")
        if (originHead.success()) {
            val ref = originHead.output.firstOrNull()?.trim()
            if (!ref.isNullOrEmpty() && ref != "origin/HEAD") return ref
        }
        return FALLBACK_BASES.firstOrNull { refExists(project, root, it) }
    }

    fun refExists(project: Project, root: VirtualFile, ref: String): Boolean =
        run(project, root, GitCommand.REV_PARSE, "--verify", "--quiet", "$ref^{commit}").success()

    private fun run(project: Project, root: VirtualFile, command: GitCommand, vararg params: String): GitCommandResult {
        val handler = GitLineHandler(project, root, command)
        handler.setSilent(true)
        // Read-only: don't let git rewrite .git/index, which would fire another repo-change event and loop.
        handler.addCustomEnvironmentVariable("GIT_OPTIONAL_LOCKS", "0")
        handler.addParameters(*params)
        return Git.getInstance().runCommand(handler)
    }

    /**
     * Git C-quotes paths containing unusual characters (e.g. `"caf\303\251.kt"`)
     * unless core.quotePath is off. Undo that so the path can be resolved.
     */
    internal fun unquote(path: String): String {
        if (path.length < 2 || !path.startsWith('"') || !path.endsWith('"')) return path
        val body = path.substring(1, path.length - 1)
        val bytes = ByteArrayOutputStream()
        var i = 0
        while (i < body.length) {
            val c = body[i]
            if (c != '\\' || i + 1 >= body.length) {
                bytes.write(c.toString().toByteArray(Charsets.UTF_8))
                i++
                continue
            }
            val next = body[i + 1]
            when {
                i + 3 < body.length && body.substring(i + 1, i + 4).all { it in '0'..'7' } -> {
                    bytes.write(body.substring(i + 1, i + 4).toInt(8))
                    i += 4
                }
                else -> {
                    val replacement = when (next) {
                        'n' -> '\n'; 't' -> '\t'; 'r' -> '\r'; 'b' -> '\b'
                        'f' -> '\u000C'; 'a' -> '\u0007'; 'v' -> '\u000B'
                        else -> next // \\ and \"
                    }
                    bytes.write(replacement.toString().toByteArray(Charsets.UTF_8))
                    i += 2
                }
            }
        }
        return bytes.toString(Charsets.UTF_8)
    }
}
