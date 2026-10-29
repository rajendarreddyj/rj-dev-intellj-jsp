package com.rajendarreddyj.intellij.jsp.debugger;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class JspSourceRootDiscovery {
    private static final List<String> CONVENTIONAL_PATHS = List.of("src/main/webapp", "WebContent", "web");
    private static final Key<CachedValue<List<VirtualFile>>> ROOTS_KEY = Key.create("jsp.source.roots.discovered");

    private static final Key<TimedRoots> DOC_BASE_KEY = Key.create("jsp.source.roots.docbase");
    private static final long DOC_BASE_TTL_MS = 5_000;

    private JspSourceRootDiscovery() {
    }

    public static List<JspSourceRootMapping> discover(Project project) {
        List<JspSourceRootMapping> mappings = new ArrayList<>();
        for (VirtualFile root : discoverRoots(project)) {
            mappings.add(new JspSourceRootMapping("", root.getPath()));
        }
        return mappings;
    }

    /** Web roots as virtual files; shared by debugging and editor features so both agree on the webapp layout. */
    public static List<VirtualFile> discoverRoots(Project project) {
        CachedValue<List<VirtualFile>> cached = project.getUserData(ROOTS_KEY);
        if (cached == null) {
            cached = CachedValuesManager.getManager(project).createCachedValue(() -> CachedValueProvider.Result.create(
                    computeRoots(project),
                    VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS,
                    ProjectRootManager.getInstance(project),
                    DumbService.getInstance(project)), false);
            project.putUserData(ROOTS_KEY, cached);
        }
        List<VirtualFile> roots = new ArrayList<>();
        for (VirtualFile docBase : docBaseRoots(project)) {
            if (docBase.isValid() && !roots.contains(docBase)) {
                roots.add(docBase);
            }
        }
        for (VirtualFile root : cached.getValue()) {
            if (root.isValid() && root.isDirectory() && !roots.contains(root)) {
                roots.add(root);
            }
        }
        return roots;
    }

    private record TimedRoots(long loadedAt, List<VirtualFile> roots) {
    }

    /** SmartTomcat doc bases come from reflective run-configuration reads, so they are re-read at most every few seconds. */
    private static List<VirtualFile> docBaseRoots(Project project) {
        TimedRoots timed = project.getUserData(DOC_BASE_KEY);
        long now = System.currentTimeMillis();
        if (timed == null || now - timed.loadedAt() > DOC_BASE_TTL_MS) {
            timed = new TimedRoots(now, List.copyOf(SmartTomcatSupport.docBaseRoots(project)));
            project.putUserData(DOC_BASE_KEY, timed);
        }
        return timed.roots();
    }

    private static List<VirtualFile> computeRoots(Project project) {
        Set<VirtualFile> discoveredRoots = new LinkedHashSet<>();
        for (Module module : ModuleManager.getInstance(project).getModules()) {
            for (VirtualFile contentRoot : ModuleRootManager.getInstance(module).getContentRoots()) {
                addConventionalRoots(contentRoot, discoveredRoots);
                addWebInfContentRoot(contentRoot, discoveredRoots);
            }
        }
        addWebInfParents(project, discoveredRoots);
        return List.copyOf(discoveredRoots);
    }

    /** Non-conventional layouts (e.g. {@code Module/JSP/WEB-INF}): any WEB-INF directory marks its parent as a web root. */
    private static void addWebInfParents(Project project, Collection<VirtualFile> roots) {
        try {
            for (VirtualFile webInf : FilenameIndex.getVirtualFilesByName("WEB-INF", GlobalSearchScope.projectScope(project))) {
                VirtualFile parent = webInf.getParent();
                if (webInf.isDirectory() && parent != null && !isBuildOutput(parent.getPath())) {
                    roots.add(parent);
                }
            }
        } catch (IndexNotReadyException ignored) {
            // recomputed once indexing finishes (the cache depends on DumbService)
        }
    }
    private static boolean isBuildOutput(String path) {
        return path.contains("/target/") || path.contains("/build/") || path.contains("/out/")
                || path.contains("/node_modules/");
    }

    private static void addConventionalRoots(VirtualFile contentRoot, Collection<VirtualFile> roots) {
        for (String relativePath : CONVENTIONAL_PATHS) {
            VirtualFile candidate = contentRoot.findFileByRelativePath(relativePath);
            if (candidate != null && candidate.isDirectory()) {
                roots.add(candidate);
            }
        }
    }

    private static void addWebInfContentRoot(VirtualFile contentRoot, Collection<VirtualFile> roots) {
        VirtualFile webInf = contentRoot.findChild("WEB-INF");
        if (webInf != null && webInf.isDirectory()) {
            roots.add(contentRoot);
        }
    }
}
