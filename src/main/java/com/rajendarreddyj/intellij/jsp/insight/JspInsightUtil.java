package com.rajendarreddyj.intellij.jsp.insight;

import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.rajendarreddyj.intellij.jsp.debugger.JspSourceRootDiscovery;
import com.rajendarreddyj.intellij.jsp.debugger.JspSourceRootMapping;
import com.rajendarreddyj.intellij.jsp.debugger.JspSourceRootSettings;
import com.rajendarreddyj.intellij.jsp.language.JspLanguage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class JspInsightUtil {
    private static final Pattern TAGLIB_DIRECTIVE_PATTERN = Pattern.compile(
            "<%@\\s*taglib\\b([^%]*)%>|<jsp:directive\\.taglib\\b([^>]*)/?>",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern USE_BEAN_PATTERN = Pattern.compile("<jsp:useBean\\b([^>]*)/?>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTR_PATTERN = Pattern.compile("([:\\w-]+)\\s*=\\s*\"([^\"]*)\"|([:\\w-]+)\\s*=\\s*'([^']*)'");
    private static final Pattern EL_PATTERN = Pattern.compile("(\\$|#)\\{([^}]*)}");
    private static final Pattern CUSTOM_TAG_PATTERN = Pattern.compile("<\\s*/?\\s*([A-Za-z_][\\w.-]*):([A-Za-z_][\\w.-]*)");
    private static final Pattern URI_PATTERN = Pattern.compile("<uri>\\s*([^<]+?)\\s*</uri>", Pattern.CASE_INSENSITIVE);
    private static final Pattern SHORT_NAME_PATTERN = Pattern.compile("<short-name>\\s*([^<]+?)\\s*</short-name>", Pattern.CASE_INSENSITIVE);
    private static final Pattern TAG_NAME_PATTERN = Pattern.compile(
            "<tag\\b[^>]*>.*?<name>\\s*([^<\\s]+)\\s*</name>.*?</tag>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern XMLNS_PATTERN = Pattern.compile("xmlns:([A-Za-z_][\\w.-]*)\\s*=\\s*[\"']([^\"']*)[\"']");
    private static final Pattern DIRECTIVE_NAME_TAIL = Pattern.compile("<%@\\s*[A-Za-z]*$");
    private static final Pattern ACTION_NAME_TAIL = Pattern.compile("<jsp:[A-Za-z]*$");
    private static final Pattern TAG_PREFIX = Pattern.compile("[A-Za-z_][\\w.-]*");
    private static final Map<String, Pattern> ATTRIBUTE_VALUE_PATTERNS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Pattern TAG_BLOCK_PATTERN = Pattern.compile("<tag\\b[^>]*>(.*?)</tag>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ATTRIBUTE_BLOCK_PATTERN = Pattern.compile("<attribute\\b[^>]*>(.*?)</attribute>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern NAME_PATTERN = Pattern.compile("<name>\\s*([^<\\s]+)\\s*</name>", Pattern.CASE_INSENSITIVE);
    private static final Pattern REQUIRED_PATTERN = Pattern.compile("<required>\\s*true\\s*</required>", Pattern.CASE_INSENSITIVE);
    private static final Pattern DYNAMIC_PATTERN = Pattern.compile("<dynamic-attributes>\\s*true\\s*</dynamic-attributes>", Pattern.CASE_INSENSITIVE);
    private static final Pattern OPEN_TAG_PATTERN = Pattern.compile("<([A-Za-z_][\\w.-]*):([A-Za-z_][\\w.-]*)\\b((?:[^>\"']|\"[^\"]*\"|'[^']*')*)>");
    private static final Pattern ATTRIBUTE_POSITION = Pattern.compile("^<([A-Za-z_][\\w.-]*):([A-Za-z_][\\w.-]*)\\s(?:[^>\"']|\"[^\"]*\"|'[^']*')*$");
    private static final Key<CachedValue<TldIndex>> TLD_INDEX_KEY = Key.create("jsp.insight.tld.index");

    private JspInsightUtil() {
    }

    static @NotNull Map<String, String> collectTaglibs(@NotNull String text) {
        Map<String, String> mappings = new LinkedHashMap<>();
        Matcher matcher = TAGLIB_DIRECTIVE_PATTERN.matcher(text);
        while (matcher.find()) {
            String attrs = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            Map<String, String> parsed = parseAttributes(attrs == null ? "" : attrs);
            String prefix = parsed.get("prefix");
            String uri = parsed.get("uri") != null ? parsed.get("uri") : parsed.get("tagdir");
            if (prefix != null && uri != null) {
                mappings.put(prefix, uri);
            }
        }
        Matcher xmlns = XMLNS_PATTERN.matcher(text);
        while (xmlns.find()) {
            mappings.putIfAbsent(xmlns.group(1), xmlns.group(2));
        }
        return mappings;
    }

    /**
     * Prefixes visible in {@code file}: its own taglib directives, those of statically included files, and, for
     * fragments, those of the pages that include it (the fragment is compiled inside them).
     */
    static @NotNull Map<String, String> effectiveTaglibs(@NotNull PsiFile file) {
        return CachedValuesManager.getCachedValue(file, () -> {
            Map<String, String> result = new LinkedHashMap<>();
            gatherTaglibs(file, true, new HashSet<>(), result, 0);
            return CachedValueProvider.Result.create(Collections.unmodifiableMap(result),
                    PsiModificationTracker.getInstance(file.getProject()).forLanguage(JspLanguage.INSTANCE),
                    VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS);
        });
    }

    private static void gatherTaglibs(@NotNull PsiFile file, boolean upward, @NotNull Set<VirtualFile> visited,
                                      @NotNull Map<String, String> out, int depth) {
        VirtualFile vf = file.getVirtualFile();
        if (depth > 16 || (vf != null && !visited.add(vf))) {
            return;
        }
        String text = file.getViewProvider().getContents().toString();
        collectTaglibs(text).forEach(out::putIfAbsent);
        Matcher include = JspJavaTranslation.INCLUDE_PATTERN.matcher(text);
        while (include.find()) {
            PsiFile target = resolveWebPath(file.getProject(), file, include.group(1).trim());
            if (target != null && target.getLanguage().isKindOf(JspLanguage.INSTANCE)) {
                gatherTaglibs(target, false, visited, out, depth + 1);
            }
        }
        if (upward && vf != null) {
            for (PsiFile includer : JspJavaTranslation.includersOf(file)) {
                gatherTaglibs(includer, true, visited, out, depth + 1);
            }
        }
    }

    static @NotNull List<UseBeanDecl> useBeans(@NotNull PsiFile file) {
        return CachedValuesManager.getCachedValue(file, () -> CachedValueProvider.Result.create(
                List.copyOf(collectUseBeans(file.getViewProvider().getContents().toString())), file));
    }

    static @NotNull List<TagUse> collectCustomTags(@NotNull String text) {
        List<TagUse> tags = new ArrayList<>();
        Matcher matcher = CUSTOM_TAG_PATTERN.matcher(text);
        while (matcher.find()) {
            tags.add(new TagUse(matcher.group(1), matcher.start(1), matcher.end(1)));
        }
        return tags;
    }

    static @NotNull List<UseBeanDecl> collectUseBeans(@NotNull String text) {
        List<UseBeanDecl> beans = new ArrayList<>();
        Matcher matcher = USE_BEAN_PATTERN.matcher(text);
        while (matcher.find()) {
            String attrs = matcher.group(1) == null ? "" : matcher.group(1);
            Map<String, String> parsed = parseAttributes(attrs);
            String id = parsed.get("id");
            String className = parsed.get("class");
            if (id == null || className == null) {
                continue;
            }
            int idStart = locateAttributeValueStart(matcher.group(), "id", matcher.start());
            int idEnd = idStart >= 0 ? idStart + id.length() : -1;
            beans.add(new UseBeanDecl(id, className, idStart, idEnd));
        }
        return beans;
    }

    static @NotNull Map<String, String> parseAttributes(@NotNull String attrs) {
        Map<String, String> values = new LinkedHashMap<>();
        Matcher matcher = ATTR_PATTERN.matcher(attrs);
        while (matcher.find()) {
            String name = matcher.group(1) != null ? matcher.group(1) : matcher.group(3);
            String value = matcher.group(2) != null ? matcher.group(2) : matcher.group(4);
            if (name != null && value != null) {
                values.put(name.toLowerCase(Locale.ROOT), value);
            }
        }
        return values;
    }

    static @Nullable ElSegment findElSegmentAt(@NotNull String text, int offset) {
        Matcher matcher = EL_PATTERN.matcher(text);
        while (matcher.find()) {
            if (offset >= matcher.start(2) && offset <= matcher.end(2)) {
                return new ElSegment(matcher.group(2), matcher.start(2), matcher.end(2));
            }
        }
        return null;
    }

    static boolean isDirectiveNamePosition(@NotNull String text, int offset) {
        int start = Math.max(0, offset - 32);
        return DIRECTIVE_NAME_TAIL.matcher(text.substring(start, offset)).find();
    }

    static boolean isActionNamePosition(@NotNull String text, int offset) {
        int start = Math.max(0, offset - 64);
        return ACTION_NAME_TAIL.matcher(text.substring(start, offset)).find();
    }

    static @Nullable String findCustomTagPrefixForCompletion(@NotNull String text, int offset) {
        if (offset <= 0 || offset > text.length()) {
            return null;
        }
        int lt = text.lastIndexOf('<', offset - 1);
        if (lt < 0) {
            return null;
        }
        int gt = text.lastIndexOf('>', offset - 1);
        if (gt > lt) {
            return null;
        }
        String segment = text.substring(lt, offset);
        int colon = segment.indexOf(':');
        if (colon <= 1) {
            return null;
        }
        String prefix = segment.substring(1, colon).trim();
        if (prefix.isEmpty() || prefix.startsWith("/") || !TAG_PREFIX.matcher(prefix).matches()) {
            return null;
        }
        return prefix;
    }

    static @Nullable PsiFile resolveWebPath(@NotNull Project project, @NotNull PsiFile contextFile, @NotNull String path) {
        if (path.isBlank()) {
            return null;
        }

        String normalized = path.startsWith("/") ? path.substring(1) : path;
        normalized = normalized.replace('\\', '/');
        if (normalized.isBlank()) {
            return null;
        }

        PsiManager psiManager = PsiManager.getInstance(project);
        List<VirtualFile> webRoots = collectWebRoots(project);
        VirtualFile contextVf = contextFile.getVirtualFile();
        VirtualFile selectedWebRoot = findSelectedWebRoot(contextVf, webRoots);

        if (!path.startsWith("/") && contextVf != null) {
            VirtualFile parent = contextVf.getParent();
            VirtualFile candidate = parent == null ? null : VfsUtilCore.findRelativeFile(normalized, parent);
            if (candidate != null && selectedWebRoot != null && VfsUtilCore.isAncestor(selectedWebRoot, candidate, false)) {
                PsiFile psi = psiManager.findFile(candidate);
                if (psi != null) {
                    return psi;
                }
            }
        }

        if (selectedWebRoot != null) {
            VirtualFile candidate = VfsUtilCore.findRelativeFile(normalized, selectedWebRoot);
            if (candidate != null && VfsUtilCore.isAncestor(selectedWebRoot, candidate, false)) {
                PsiFile psi = psiManager.findFile(candidate);
                if (psi != null) {
                    return psi;
                }
            }
        }

        for (VirtualFile root : webRoots) {
            VirtualFile candidate = VfsUtilCore.findRelativeFile(normalized, root);
            if (candidate == null || !VfsUtilCore.isAncestor(root, candidate, false)) {
                continue;
            }
            PsiFile psi = psiManager.findFile(candidate);
            if (psi != null) {
                return psi;
            }
        }
        return null;
    }

    /** Inverse of {@link #resolveWebPath}: the include path for {@code target}, absolute from the web root or relative to the context. */
    static @Nullable String webPathFor(@NotNull PsiFile contextFile, @NotNull PsiFile target, boolean absolute) {
        VirtualFile targetVf = target.getVirtualFile();
        VirtualFile contextVf = contextFile.getVirtualFile();
        if (targetVf == null || contextVf == null) {
            return null;
        }
        VirtualFile root = findSelectedWebRoot(contextVf, collectWebRoots(contextFile.getProject()));
        if (root == null || !VfsUtilCore.isAncestor(root, targetVf, false)) {
            return null;
        }
        if (!absolute && contextVf.getParent() != null) {
            String relative = VfsUtilCore.findRelativePath(contextVf.getParent(), targetVf, '/');
            if (relative != null) {
                return relative;
            }
        }
        String fromRoot = VfsUtilCore.findRelativePath(root, targetVf, '/');
        return fromRoot == null ? null : "/" + fromRoot;
    }

    static @NotNull TldIndex getTldIndex(@NotNull Project project) {
        CachedValuesManager cacheManager = CachedValuesManager.getManager(project);
        CachedValue<TldIndex> cachedValue = project.getUserData(TLD_INDEX_KEY);
        if (cachedValue == null) {
            cachedValue = cacheManager.createCachedValue(() -> CachedValueProvider.Result.create(
                    buildTldIndex(project),
                    PsiModificationTracker.getInstance(project).forLanguage(com.intellij.lang.xml.XMLLanguage.INSTANCE),
                    VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS,
                    ProjectRootManager.getInstance(project),
                    com.intellij.openapi.project.DumbService.getInstance(project)
            ), false);
            project.putUserData(TLD_INDEX_KEY, cachedValue);
        }
        TldIndex value = cachedValue.getValue();
        return value == null ? TldIndex.empty() : value;
    }

    static @NotNull Set<String> collectTldTags(@NotNull Project project, @NotNull String identifier) {
        TldInfo info = getTldIndex(project).byIdentifier().get(identifier);
        return info == null ? Set.of() : info.tags();
    }

    static @Nullable TldInfo findTldByIdentifier(@NotNull Project project, @NotNull String identifier) {
        return getTldIndex(project).byIdentifier().get(identifier);
    }

    static @Nullable TldInfo findTldByWebPath(@NotNull Project project, @NotNull PsiFile contextFile, @NotNull String path) {
        PsiFile psiFile = resolveWebPath(project, contextFile, path);
        VirtualFile vf = psiFile == null ? null : psiFile.getVirtualFile();
        if (vf == null) {
            return null;
        }
        return getTldIndex(project).byFile().get(vf);
    }

    record OpenTag(String prefix, String name, String attributeText, int start, int attributesStart) {
    }

    static @NotNull List<OpenTag> collectOpenTags(@NotNull String text) {
        List<OpenTag> tags = new ArrayList<>();
        Matcher matcher = OPEN_TAG_PATTERN.matcher(text);
        while (matcher.find()) {
            tags.add(new OpenTag(matcher.group(1), matcher.group(2), matcher.group(3), matcher.start(), matcher.start(3)));
        }
        return tags;
    }

    /** {@code [prefix, tagName]} when {@code offset} is where an attribute name is typed inside a custom tag. */
    static @Nullable String[] findAttributePosition(@NotNull String text, int offset) {
        int lt = text.lastIndexOf('<', offset - 1);
        if (lt < 0 || offset > text.length()) {
            return null;
        }
        Matcher matcher = ATTRIBUTE_POSITION.matcher(text.substring(lt, offset));
        return matcher.matches() ? new String[]{matcher.group(1), matcher.group(2)} : null;
    }

    static @Nullable TldInfo findTldForPrefix(@NotNull Project project, @NotNull Map<String, String> taglibs, @NotNull String prefix) {
        String uri = taglibs.get(prefix);
        if (uri == null) {
            return null;
        }
        TldInfo info = findTldByIdentifier(project, uri);
        return info != null ? info : findTldByIdentifier(project, prefix);
    }

    static @Nullable PsiClass findClassByQualifiedName(@NotNull Project project, @NotNull String qualifiedName) {
        return JavaPsiFacade.getInstance(project).findClass(qualifiedName, GlobalSearchScope.allScope(project));
    }

    static @Nullable TextRange findAttributeValueRange(@NotNull String text, @NotNull String attrName) {
        Matcher matcher = attributeValuePattern(attrName).matcher(text);
        if (!matcher.find()) {
            return null;
        }
        int start = matcher.start(1) >= 0 ? matcher.start(1) : matcher.start(2);
        int end = matcher.end(1) >= 0 ? matcher.end(1) : matcher.end(2);
        return TextRange.create(start, end);
    }

    private static @NotNull TldIndex buildTldIndex(@NotNull Project project) {
        List<TldInfo> all = new ArrayList<>();
        Map<String, TldInfo> byIdentifier = new HashMap<>();
        Map<VirtualFile, TldInfo> byFile = new HashMap<>();
        PsiManager psiManager = PsiManager.getInstance(project);

        List<VirtualFile> tldFiles;
        try {
            // allScope: JSTL (javax 1.2 / jakarta 3.0) ships its TLDs inside library jars (META-INF/*.tld).
            tldFiles = new ArrayList<>(FilenameIndex.getAllFilesByExt(project, "tld", GlobalSearchScope.allScope(project)));
        } catch (IndexNotReadyException ignored) {
            return TldIndex.empty();
        }
        tldFiles.sort(Comparator.comparing(VirtualFile::getPath));

        for (VirtualFile file : tldFiles) {
            PsiFile psiFile = psiManager.findFile(file);
            if (psiFile == null) {
                continue;
            }
            // Per-file cache: an unrelated PSI edit only re-assembles the index, without re-parsing each TLD.
            TldInfo info = CachedValuesManager.getCachedValue(psiFile, () -> CachedValueProvider.Result.create(
                    createTldInfo(file, psiFile.getText()), psiFile));
            all.add(info);
            byFile.put(file, info);
            if (info.uri() != null && !info.uri().isBlank()) {
                byIdentifier.putIfAbsent(info.uri(), info);
            }
            if (info.shortName() != null && !info.shortName().isBlank()) {
                byIdentifier.putIfAbsent(info.shortName(), info);
            }
        }

        return new TldIndex(
                Collections.unmodifiableList(all),
                Collections.unmodifiableMap(byIdentifier),
                Collections.unmodifiableMap(byFile)
        );
    }

    private static @NotNull TldInfo createTldInfo(@NotNull VirtualFile file, @NotNull String text) {
        Set<String> tags = new LinkedHashSet<>();
        Matcher tagMatcher = TAG_NAME_PATTERN.matcher(text);
        while (tagMatcher.find()) {
            tags.add(tagMatcher.group(1));
        }
        Map<String, TagInfo> tagInfos = new LinkedHashMap<>();
        Matcher blocks = TAG_BLOCK_PATTERN.matcher(text);
        while (blocks.find()) {
            String body = blocks.group(1);
            String tagName = matchSingle(body, NAME_PATTERN);
            if (tagName == null) {
                continue;
            }
            Set<String> attributes = new LinkedHashSet<>();
            Set<String> required = new LinkedHashSet<>();
            Matcher attrs = ATTRIBUTE_BLOCK_PATTERN.matcher(body);
            while (attrs.find()) {
                String attrName = matchSingle(attrs.group(1), NAME_PATTERN);
                if (attrName != null) {
                    attributes.add(attrName);
                    if (REQUIRED_PATTERN.matcher(attrs.group(1)).find()) {
                        required.add(attrName);
                    }
                }
            }
            tagInfos.put(tagName, new TagInfo(Collections.unmodifiableSet(attributes),
                    Collections.unmodifiableSet(required), DYNAMIC_PATTERN.matcher(body).find()));
        }
        return new TldInfo(
                file,
                matchSingle(text, URI_PATTERN),
                matchSingle(text, SHORT_NAME_PATTERN),
                Collections.unmodifiableSet(tags),
                Collections.unmodifiableMap(tagInfos)
        );
    }

    private static @Nullable String matchSingle(@NotNull String text, @NotNull Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    /**
     * Web roots, most specific first: discovered roots (conventional folders, any directory holding WEB-INF, and
     * the user's configured mappings), then content roots as a last resort.
     */
    private static final Key<CachedValue<List<VirtualFile>>> WEB_ROOTS_KEY = Key.create("jsp.web.roots");

    /** Cached: path resolution calls this for every reference, so it must not hit the file system each time. */
    private static @NotNull List<VirtualFile> collectWebRoots(@NotNull Project project) {
        CachedValue<List<VirtualFile>> cached = project.getUserData(WEB_ROOTS_KEY);
        if (cached == null) {
            cached = CachedValuesManager.getManager(project).createCachedValue(() -> CachedValueProvider.Result.create(
                    computeWebRoots(project),
                    VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS,
                    ProjectRootManager.getInstance(project),
                    (com.intellij.openapi.util.ModificationTracker) () -> mappingsStamp(project),
                    (com.intellij.openapi.util.ModificationTracker) () -> System.currentTimeMillis() / 5_000), false);
            project.putUserData(WEB_ROOTS_KEY, cached);
        }
        return cached.getValue();
    }

    private static long mappingsStamp(@NotNull Project project) {
        JspSourceRootSettings settings = JspSourceRootSettings.getInstance(project);
        long stamp = 0;
        if (settings != null) {
            for (JspSourceRootMapping mapping : settings.getMappings()) {
                stamp = stamp * 31 + String.valueOf(mapping.getLocalSourceRoot()).hashCode();
            }
        }
        return stamp;
    }

    private static @NotNull List<VirtualFile> computeWebRoots(@NotNull Project project) {
        List<VirtualFile> roots = new ArrayList<>();
        Set<VirtualFile> seen = new HashSet<>();
        for (VirtualFile root : JspSourceRootDiscovery.discoverRoots(project)) {
            addRoot(roots, seen, root);
        }
        JspSourceRootSettings settings = JspSourceRootSettings.getInstance(project);
        if (settings != null) {
            for (JspSourceRootMapping mapping : settings.getMappings()) {
                String path = mapping.getLocalSourceRoot();
                VirtualFile root = path == null || path.isBlank() ? null
                        : com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(path);
                addRoot(roots, seen, root != null && root.isDirectory() ? root : null);
            }
        }
        for (VirtualFile contentRoot : ProjectRootManager.getInstance(project).getContentRoots()) {
            addRoot(roots, seen, contentRoot);
        }
        return roots;
    }

    private static void addRoot(@NotNull List<VirtualFile> roots, @NotNull Set<VirtualFile> seen, @Nullable VirtualFile root) {
        if (root != null && seen.add(root)) {
            roots.add(root);
        }
    }

    private static @Nullable VirtualFile findSelectedWebRoot(@Nullable VirtualFile contextVf, @NotNull List<VirtualFile> webRoots) {
        if (contextVf == null) {
            return null;
        }
        for (VirtualFile root : webRoots) {
            if (VfsUtilCore.isAncestor(root, contextVf, false)) {
                return root;
            }
        }
        return null;
    }

    private static @NotNull Pattern attributeValuePattern(@NotNull String attrName) {
        return ATTRIBUTE_VALUE_PATTERNS.computeIfAbsent(attrName, name -> Pattern.compile(
                name + "\\s*=\\s*\"([^\"]*)\"|" + name + "\\s*=\\s*'([^']*)'", Pattern.CASE_INSENSITIVE));
    }

    private static int locateAttributeValueStart(String fullTagText, String attrName, int absoluteTagStart) {
        Matcher matcher = attributeValuePattern(attrName).matcher(fullTagText);
        if (!matcher.find()) {
            return -1;
        }
        int relativeStart = matcher.start(1) >= 0 ? matcher.start(1) : matcher.start(2);
        return absoluteTagStart + relativeStart;
    }

    record ElSegment(String content, int contentStart, int contentEnd) {
    }

    record UseBeanDecl(String id, String className, int idStart, int idEnd) {
    }

    record TagUse(String prefix, int start, int end) {
    }

    record TagInfo(@NotNull Set<String> attributes, @NotNull Set<String> required, boolean dynamicAttributes) {
    }

    record TldInfo(@NotNull VirtualFile file, @Nullable String uri, @Nullable String shortName, @NotNull Set<String> tags,
                   @NotNull Map<String, TagInfo> tagInfos) {
    }

    record TldIndex(@NotNull List<TldInfo> all, @NotNull Map<String, TldInfo> byIdentifier,
                    @NotNull Map<VirtualFile, TldInfo> byFile) {
        static @NotNull TldIndex empty() {
            return new TldIndex(List.of(), Map.of(), Map.of());
        }
    }
}
