# Branch Changes

A plugin for Android Studio, IntelliJ IDEA and other JetBrains IDEs. It adds a **Branch Changes** view to the Project tool window's dropdown, next to "All Changed Files". The view shows only the files added or modified on your current Git branch, in the normal folder tree.

Made by Karthik Umashankar at Umain.

## What it shows

It finds the merge-base between `HEAD` and the base branch, then includes:

- files changed in commits on your branch
- staged and unstaged changes to tracked files
- new untracked files (respecting `.gitignore`)

Deleted files are left out. Renamed files show under their new path. Projects with several Git roots are handled per repository.

The list updates on its own, a moment after you edit, commit, switch branches or fetch.

**New files are highlighted.** In every Project panel view (Project, Android, Branch Changes…), files that are new on the branch get a green name and a grey "new" label. This covers files that were added, copied or are still untracked. Unlike the IDE's own Git colours, the highlight stays after you commit. Modified files keep their usual colour.

**Base branch:** `origin/HEAD` if the remote has one, otherwise the first that exists of `origin/main`, `origin/master`, `origin/develop`, `upstream/main`, `upstream/master`, `main`, `master`, `develop`.

## Usage

1. Open the **Project** tool window.
2. Click the view dropdown at the top (where it says "Project" or "Android") → **Branch Changes**.

To compare against a different branch, go to **Git → Branch Changes: Set Base Branch…** and type e.g. `origin/develop` or a commit SHA. It's remembered per project. Leave the field empty to go back to auto-detect.

Because it's a regular IDE scope, **Branch Changes** also shows up anywhere you pick a scope, such as Find in Files (⇧⌘F) → Scope.

## Build

No Gradle or internet needed. The script uses the JDK and Kotlin compiler that come with the IDE and compiles against the IDE's own jars:

```bash
./build.sh
```

Output: `build/distributions/branch-changes-<version>.zip`. By default it builds against `/Applications/Android Studio.app`. To build against another IDE, set `STUDIO`, e.g. `STUDIO="/Applications/IntelliJ IDEA.app" ./build.sh`.

## Install locally

Settings → Plugins → ⚙️ → **Install Plugin from Disk…** → choose the zip → restart.

If you installed an earlier development build (plugin ID `dev.openbranchchanges`), uninstall it first. Otherwise you'll get two "Branch Changes" entries.

## Publishing to JetBrains Marketplace

Already done:

- Plugin ID `com.umain.branchchanges`. This is permanent once published.
- Vendor: Umain, with Karthik Umashankar credited in the description
- Description, change notes, light and dark icons
- Compiles against Android Studio 2026.2 (build 262) and IntelliJ IDEA 2026.3 (build 263)
- MIT license (`LICENSE`, also packaged in the plugin jar)

Before the first upload:

1. **Check the vendor URL** in `plugin.xml` (`https://www.umain.com`). Optionally add a contact email: `<vendor url="…" email="…">Umain</vendor>`.
2. **Test the release zip** in Android Studio: install it, open the Branch Changes view, and check the "new" highlighting.
3. **Check compatibility.** `since-build` is 242 (2024.2), but only 262 and 263 have been compiled against. The Marketplace runs the Plugin Verifier on upload and reports problems per IDE version. If older versions fail, raise `since-build`.

Upload:

1. Sign in at plugins.jetbrains.com with the account that should own the plugin. Ideally use a Umain organisation (vendor) profile rather than a personal one, so others at Umain can maintain it.
2. Go to **Upload plugin** → choose `build/distributions/branch-changes-1.0.0.zip`.
3. Choose license **MIT**, category (e.g. *Version Controls*), tags (*Git*, *VCS*) and, optionally, a source code URL. The form needs a license URL. If the source isn't public, `https://opensource.org/license/mit` works.
4. JetBrains reviews the first upload by hand, usually within a couple of working days.

For updates, bump `<version>` in `plugin.xml`, add a `<change-notes>` entry, run `./build.sh` and upload the new zip on the plugin's Marketplace page.

## Source

All in `src/main/kotlin/com/umain/branchchanges/`:

- `BranchChangesScope.kt`: the scope and the provider that adds it to the dropdown
- `BranchChangesService.kt`: keeps the set of changed and new files, recomputes it in the background (debounced), and refreshes the Project view when it changes
- `BranchChangesCollector.kt`: base detection and the read-only git queries, run through the IDE's Git integration
- `NewFileDecorator.kt`: the green name and "new" label for files added on the branch
- `BranchChangesListeners.kt`: recomputes on project open, on Git repository changes and after the IDE's own VCS status refreshes
- `SetBaseBranchAction.kt`: the Git menu item for overriding the base branch

The plugin descriptor and icons are in `src/main/resources/META-INF/`.
