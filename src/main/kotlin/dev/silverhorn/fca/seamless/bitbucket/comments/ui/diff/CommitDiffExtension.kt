package dev.silverhorn.fca.seamless.bitbucket.comments.ui.diff

import com.intellij.diff.DiffContext
import com.intellij.diff.DiffExtension
import com.intellij.diff.FrameDiffTool
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.tools.util.side.TwosideTextDiffViewer
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import dev.silverhorn.fca.seamless.bitbucket.comments.domain.CommitComment
import dev.silverhorn.fca.seamless.bitbucket.comments.domain.FileType
import dev.silverhorn.fca.seamless.bitbucket.comments.platform.CommitCommentService
import git4idea.repo.GitRepositoryManager
import java.io.File

/**
 * [DiffExtension] that injects Bitbucket commit-comment inlays and gutter icons
 * into every [TwosideTextDiffViewer] opened inside IntelliJ.
 *
 * Lifecycle:
 * 1. [onViewerCreated] is called by the framework on the EDT.
 * 2. A [Task.Backgroundable] fetches comments from [CommitCommentService] on `IO`.
 * 3. Back on the EDT (via `invokeLater`) [CommentInlayRenderer] and
 *    [GutterCommentIconRenderer] are installed on the two editor panels.
 * 4. All registered inlays and listeners are tied to the viewer's lifecycle via
 *    the viewer component's disposable.
 *
 * Registered in `plugin.xml` as `<diff.DiffExtension>`.
 */
class CommitDiffExtension : DiffExtension() {

    private val log = logger<CommitDiffExtension>()

    /**
     * Called by the framework on the EDT each time a diff viewer is constructed.
     *
     * Only processes [TwosideTextDiffViewer] instances; all other viewer types
     * (image diffs, directory diffs, ?) are silently ignored.
     *
     * @param viewer  the freshly created diff viewer
     * @param request the diff request describing what is being compared
     * @param context additional diff context provided by the framework
     */
    override fun onViewerCreated(
        viewer: FrameDiffTool.DiffViewer,
        context: DiffContext,
        request: DiffRequest
    ) {
        if (viewer !is TwosideTextDiffViewer) return

        val project = context.project ?: return

        val title = request.title ?: return

        // Resolve commit hash from multiple sources; many diff titles do not include hashes.
        val hashes = resolveHashes(viewer, title)
        val toHash = hashes.second ?: run {
            log.debug("Skipping non-commit diff (no hash resolved): '$title'")
            return
        }
        val fromHash = hashes.first ?: ""

        // Best-effort file path extraction from title and repo roots.
        val filePath = extractPathFromTitle(title, project)
        if (filePath.isBlank()) return

        log.debug("CommitDiffExtension activated for commit=$toHash file=$filePath")

        object : Task.Backgroundable(project, "Loading Bitbucket comments?", false) {
            override fun run(indicator: ProgressIndicator) {
                val comments = CommitCommentService.getInstance(project).fetchAllComments(toHash)
                ApplicationManager.getApplication().invokeLater {
                    installUi(viewer, project, comments, filePath, fromHash, toHash)
                }
            }
        }.queue()
    }

    // ?? UI installation (EDT only) ?????????????????????????????????????????

    /**
     * Installs inlays and gutter icons into both editors of the diff viewer.
     *
     * Must be called on the EDT.
     *
     * @param viewer    the two-sided diff viewer
     * @param project   current project
     * @param comments  all comments fetched for the commit
     * @param filePath  repository-relative path of the file shown in the diff
     * @param fromHash  SHA-1 of the base commit
     * @param toHash    SHA-1 of the head commit
     */
    private fun installUi(
        viewer: TwosideTextDiffViewer,
        project: Project,
        comments: List<CommitComment>,
        filePath: String,
        fromHash: String,
        toHash: String
    ) {
        val leftEditor = viewer.editor1
        val rightEditor = viewer.editor2

        // Use the project lifecycle as fallback Disposable parent.
        val disposable = project

        // Filter inline comments for this specific file. Fallback to suffix match when titles are shortened.
        val fileComments = comments.filter { comment ->
            val anchorPath = comment.anchor?.path ?: return@filter false
            pathMatches(anchorPath, filePath)
        }

        // Install block inlays for existing comments.
        fileComments.forEach { comment ->
            val anchor = comment.anchor ?: return@forEach
            val line = (anchor.line ?: 1) - 1  // convert to 0-based
            val editor = if (anchor.fileType == FileType.FROM) leftEditor else rightEditor
            CommentInlayRenderer.install(editor, line, comment, project, toHash, disposable)
        }

        // Install gutter icons for adding new comments on all visible lines.
        GutterCommentIconRenderer.installOnAllLines(
            leftEditor, rightEditor, project, filePath, fromHash, toHash, disposable
        )
    }

    // ?? Helpers ????????????????????????????????????????????????????????????

    /**
     * Attempts to extract (fromHash, toHash) from a diff request title string.
     *
     * Returns (null, null) when no recognizable hash pattern is found.
     * This is a best-effort heuristic; proper extraction requires internal diff APIs.
     *
     * @param title the DiffRequest title string
     * @return pair of (base hash, head hash), either element may be null
     */
    private fun extractHashesFromTitle(title: String): Pair<String?, String?> {
        // Git UI often shows short hashes, so we accept 7..40 chars.
        val sha1Pattern = Regex("[0-9a-fA-F]{7,40}")
        val matches = sha1Pattern.findAll(title).map { it.value.lowercase() }.toList()
        return when {
            matches.size >= 2 -> Pair(matches[0], matches[1])
            matches.size == 1 -> Pair(null, matches[0])
            else -> Pair(null, null)
        }
    }

    /**
     * Resolves hashes from request title and both editor backing file paths.
     */
    private fun resolveHashes(viewer: TwosideTextDiffViewer, title: String): Pair<String?, String?> {
//        viewer.content1.document.
//        return viewer.contents.map { it.getUserData(it.) }
//        Pair((viewer.content1 as DiffContentFactoryImpl.ContextReferentDocumentContent).get().let { it.get(it.keys.get(1)) }
//        , (viewer as SimpleDiffViewer).diffChanges)

        return viewer.request.contentTitles.let { Pair(it.first(), it.last())}
    }

    /**
     * Tries to extract a repository-relative path from the diff title.
     * Handles common formats like "path/to/File.kt (...)".
     */
    private fun extractPathFromTitle(title: String, project: Project): String {
        val fileName = title.substringBefore(" (").trim().removePrefix("/")
        if (fileName.isBlank()) return ""

        // Pattern: "File.java (C:\...\folder)" -> reconstruct full path and relativize to Git root.
        val dirPart = title.substringAfter("(", missingDelimiterValue = "")
            .substringBeforeLast(")", missingDelimiterValue = "")
            .trim()
        if (dirPart.isBlank()) return fileName

        val absolute = File(dirPart, fileName).path.replace('\\', '/')
        val repos = GitRepositoryManager.getInstance(project).repositories
        for (repo in repos) {
            val root = repo.root.path.replace('\\', '/')
            if (absolute.startsWith("$root/")) {
                return absolute.removePrefix("$root/")
            }
        }

        return fileName
    }

    /**
     * Matches exact paths and basename/title fallbacks.
     */
    private fun pathMatches(anchorPath: String, requestedPath: String): Boolean {
        if (anchorPath == requestedPath) return true
        if (anchorPath.endsWith("/$requestedPath")) return true
        val fileName = requestedPath.substringAfterLast('/')
        return fileName.isNotBlank() && anchorPath.endsWith("/$fileName")
    }
}

