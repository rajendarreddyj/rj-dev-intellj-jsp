package com.rajendarreddyj.intellij.jsp.debugger;

import com.intellij.debugger.engine.SourcesFinder;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.rajendarreddyj.intellij.jsp.language.JspFileType;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class TomcatJspSourceFinder implements SourcesFinder<Project> {
    @Override
    public @Nullable PsiFile findSourceFile(String relPath, Project project, Project scope) {
        if (project == null || project.isDisposed() || relPath == null || relPath.isBlank()) {
            return null;
        }
        // JSR45PositionManager calls this from debugger threads without holding a read lock.
        return ReadAction.compute(() -> project.isDisposed() ? null : doFindSourceFile(relPath, project, scope));
    }

    private static @Nullable PsiFile doFindSourceFile(String relPath, Project project, @Nullable Project scope) {
        List<JspSourceRootMapping> mappings = new ArrayList<>();
        Project effectiveScope = scope != null ? scope : project;
        mappings.addAll(JspSourceRootDiscovery.discover(effectiveScope));

        JspSourceRootSettings settings = JspSourceRootSettings.getInstance(project);
        if (settings != null) {
            mappings.addAll(settings.getMappings());
        }

        // Packaged copies (target/<war>, build/...) duplicate every JSP and would make each page ambiguous.
        List<JspSourceRootMapping> sourceMappings = new ArrayList<>();
        for (JspSourceRootMapping mapping : mappings) {
            String root = mapping.getLocalSourceRoot();
            if (root != null && !isBuildOutput(root.replace('\\', '/') + "/")) {
                sourceMappings.add(mapping);
            }
        }
        if (!sourceMappings.isEmpty()) {
            mappings = sourceMappings;
        }

        JspSourceResolver.Resolution resolution = JspSourceResolver.resolve(relPath, mappings);
        if (resolution.status() == JspSourceResolver.Status.RESOLVED && resolution.path() != null) {
            VirtualFile sourceFile = findVirtualFile(resolution.path());
            if (sourceFile != null && !sourceFile.isDirectory()) {
                PsiFile psiFile = findJspPsiFile(project, sourceFile);
                if (psiFile != null) {
                    return psiFile;
                }
            }
        } else if (resolution.status() == JspSourceResolver.Status.AMBIGUOUS) {
            return null;
        }

        List<PsiFile> psiMatches = findMatchesViaVirtualRoots(relPath, project, mappings);
        if (psiMatches.size() == 1) {
            return psiMatches.get(0);
        }
        if (psiMatches.isEmpty()) {
            return findBySuffix(relPath, project);
        }
        return null;
    }

    /**
     * Fallback for non-conventional webapp roots (for example {@code RolloutManager/JSP/WEB-INF}): finds the
     * project JSP whose path ends with the SMAP-relative path, ignoring build output copies.
     */
    private static @Nullable PsiFile findBySuffix(String relPath, Project project) {
        String normalized = normalizeRelativePath(relPath);
        if (normalized == null) {
            return null;
        }
        String name = normalized.substring(normalized.lastIndexOf('/') + 1);
        String suffix = "/" + normalized;
        List<VirtualFile> candidates = new ArrayList<>();
        for (VirtualFile file : FilenameIndex.getVirtualFilesByName(name, GlobalSearchScope.projectScope(project))) {
            String path = file.getPath();
            if (!file.isDirectory() && path.endsWith(suffix) && !isBuildOutput(path)) {
                candidates.add(file);
            }
        }
        if (candidates.size() != 1) {
            return null;
        }
        return findJspPsiFile(project, candidates.get(0));
    }

    private static boolean isBuildOutput(String path) {
        return path.contains("/target/") || path.contains("/build/") || path.contains("/out/")
                || path.contains("/node_modules/");
    }

    private static @Nullable PsiFile findJspPsiFile(Project project, VirtualFile sourceFile) {
        PsiFile psiFile = PsiManager.getInstance(project).findFile(sourceFile);
        if (psiFile == null) {
            return null;
        }
        FileType fileType = psiFile.getFileType();
        return fileType == JspFileType.INSTANCE ? psiFile : null;
    }

    private static List<PsiFile> findMatchesViaVirtualRoots(
            String relPath,
            Project project,
            List<JspSourceRootMapping> mappings
    ) {
        String normalizedSmapPath = normalizeRelativePath(relPath);
        if (normalizedSmapPath == null) {
            return List.of();
        }

        Set<PsiFile> matches = new LinkedHashSet<>();
        for (JspSourceRootMapping mapping : mappings) {
            if (mapping == null) {
                continue;
            }
            String remainder = JspSourceResolver.stripPrefix(normalizedSmapPath, mapping.getSmapPrefix());
            if (remainder == null || remainder.isBlank()) {
                continue;
            }
            String normalizedRemainder = normalizeRelativePath(remainder);
            if (normalizedRemainder == null) {
                continue;
            }

            VirtualFile root = findVirtualRoot(mapping.getLocalSourceRoot());
            if (root == null || !root.isDirectory()) {
                continue;
            }
            VirtualFile candidate = root.findFileByRelativePath(normalizedRemainder);
            if (candidate == null || candidate.isDirectory()) {
                continue;
            }

            PsiFile psiFile = findJspPsiFile(project, candidate);
            if (psiFile != null) {
                matches.add(psiFile);
            }
        }
        return new ArrayList<>(matches);
    }

    private static @Nullable VirtualFile findVirtualRoot(String rootPath) {
        if (rootPath == null || rootPath.isBlank()) {
            return null;
        }
        LocalFileSystem localFileSystem = LocalFileSystem.getInstance();
        VirtualFile local = localFileSystem.findFileByPath(rootPath);
        if (local != null) {
            return local;
        }

        String normalizedPath = rootPath.replace('\\', '/');
        String tempUrl = normalizedPath.startsWith("/") ? "temp://" + normalizedPath : "temp:///" + normalizedPath;
        return VirtualFileManager.getInstance().findFileByUrl(tempUrl);
    }

    private static @Nullable VirtualFile findVirtualFile(Path path) {
        return LocalFileSystem.getInstance().findFileByNioFile(path);
    }

    private static @Nullable String normalizeRelativePath(String path) {
        if (path == null) {
            return null;
        }
        String normalized = path.replace('\\', '/').trim();
        if (normalized.isEmpty()) {
            return null;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("//") || lower.matches("^[a-z]:.*")) {
            return null;
        }
        String[] segments = normalized.split("/");
        List<String> cleanedSegments = new ArrayList<>();
        for (String segment : segments) {
            if (segment.isEmpty() || ".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment)) {
                return null;
            }
            cleanedSegments.add(segment);
        }
        if (cleanedSegments.isEmpty()) {
            return null;
        }
        return String.join("/", cleanedSegments);
    }
}
