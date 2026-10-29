package com.rajendarreddyj.intellij.jsp.insight;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.rajendarreddyj.intellij.jsp.language.JspElementTypes;
import com.rajendarreddyj.intellij.jsp.language.JspLanguage;
import com.rajendarreddyj.intellij.jsp.language.JspScriptletHost;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Models the servlet Jasper generates for a JSP page so injected Java sees page imports, implicit objects,
 * declarations, and code from statically included files ({@code <%@ include file %>}), like the real translation unit.
 */
final class JspJavaTranslation {
    private static final int MAX_INCLUDE_DEPTH = 16;
    static final Pattern INCLUDE_PATTERN = Pattern.compile(
            "<(?:%@\\s*include|jsp:directive\\.include)\\b[^>]*?\\bfile\\s*=\\s*[\"']([^\"']+)[\"']");
    private static final Key<CachedValue<Map<VirtualFile, List<VirtualFile>>>> INCLUDERS_KEY =
            Key.create("jsp.java.translation.includers");

    sealed interface Item permits Text, Place {
    }

    record Text(@NotNull String text) implements Item {
    }

    record Place(@NotNull JspScriptletHost host, @NotNull TextRange range, boolean expression) implements Item {
    }

    record Unit(@NotNull String header, @NotNull List<Item> items, @NotNull String footer) {
    }

    private final Unit declarationUnit;
    private final Unit codeUnit;

    private JspJavaTranslation(@NotNull Unit declarationUnit, @NotNull Unit codeUnit) {
        this.declarationUnit = declarationUnit;
        this.codeUnit = codeUnit;
    }

    @NotNull Unit unitFor(@NotNull JspScriptletHost host) {
        return host.getNode().getElementType() == JspElementTypes.SCRIPTLET_DECLARATION ? declarationUnit : codeUnit;
    }

    /** The whole page as one Java class text (declarations first, then {@code _jspService}), for evaluation contexts. */
    @NotNull String codeUnitText() {
        StringBuilder text = new StringBuilder(codeUnit.header());
        for (Item item : codeUnit.items()) {
            String content = textOf(item);
            boolean expression = item instanceof Place place && place.expression();
            text.append('\n').append(expression ? "out.print(" + content + ");" : content);
        }
        return text.append(codeUnit.footer()).toString();
    }

    static @NotNull JspJavaTranslation forFile(@NotNull PsiFile file) {
        return CachedValuesManager.getCachedValue(file, () -> CachedValueProvider.Result.create(
                build(file),
                PsiModificationTracker.MODIFICATION_COUNT,
                DumbService.getInstance(file.getProject())));
    }

    /** Java content range inside a scriptlet host ({@code <% %>}, {@code <%! %>}, {@code <%= %>} or JSPX form). */
    static @Nullable TextRange contentRange(@NotNull String text, @NotNull IElementType type) {
        int start;
        int end;
        if (text.startsWith("<jsp:")) {
            start = text.indexOf('>') + 1;
            end = text.lastIndexOf("</");
        } else {
            start = type == JspElementTypes.SCRIPTLET ? 2 : 3;
            end = text.length() - 2;
        }
        if (start <= 0 || start >= end || end > text.length() || text.substring(start, end).isBlank()) {
            return null;
        }
        return TextRange.create(start, end);
    }

    private static final class Model {
        final Set<String> imports = new LinkedHashSet<>();
        final List<Item> declarations = new ArrayList<>();
        final List<Item> code = new ArrayList<>();
        boolean acceptCode = true;
    }

    private static @NotNull JspJavaTranslation build(@NotNull PsiFile file) {
        Model model = new Model();
        VirtualFile vf = file.getVirtualFile();
        Set<VirtualFile> visiting = new HashSet<>();
        if (vf != null) {
            visiting.add(vf);
            // A fragment edited on its own borrows the code of the page(s) that statically include it.
            List<PsiFile> chain = includerChain(file);
            for (int i = 0; i < chain.size(); i++) {
                PsiFile includer = chain.get(i);
                VirtualFile stopAt = i + 1 < chain.size() ? chain.get(i + 1).getVirtualFile() : vf;
                model.acceptCode = true;
                collect(includer, model, false, stopAt, visiting, 0);
            }
            model.acceptCode = true;
        }
        collect(file, model, true, null, visiting, 0);

        String ns = servletNamespace(file);
        StringBuilder imports = new StringBuilder();
        imports.append("import ").append(ns).append(".servlet.*;\n")
                .append("import ").append(ns).append(".servlet.http.*;\n")
                .append("import ").append(ns).append(".servlet.jsp.*;\n");
        for (String imported : model.imports) {
            imports.append("import ").append(imported).append(";\n");
        }
        String classOpen = "abstract class _jsp extends " + ns + ".servlet.http.HttpServlet {\n";

        StringBuilder memberText = new StringBuilder();
        for (Item item : model.declarations) {
            memberText.append('\n').append(textOf(item)).append('\n');
        }
        String serviceOpen = "\npublic void _jspService(final " + ns + ".servlet.http.HttpServletRequest request, final "
                + ns + ".servlet.http.HttpServletResponse response) throws java.lang.Throwable {\n"
                + "final " + ns + ".servlet.jsp.PageContext pageContext = null;\n"
                + ns + ".servlet.http.HttpSession session = null;\n"
                + "final " + ns + ".servlet.ServletContext application = null;\n"
                + "final " + ns + ".servlet.ServletConfig config = null;\n"
                + ns + ".servlet.jsp.JspWriter out = null;\n"
                + "final java.lang.Object page = this;\n"
                + "java.lang.Throwable exception = null;\n";

        Unit declarationUnit = new Unit(imports + classOpen, model.declarations, "\n}\n");
        Unit codeUnit = new Unit(imports + classOpen + memberText + serviceOpen, model.code, "\n}\n}\n");
        return new JspJavaTranslation(declarationUnit, codeUnit);
    }

    private static @NotNull String textOf(@NotNull Item item) {
        if (item instanceof Text text) {
            return text.text();
        }
        Place place = (Place) item;
        return place.range().substring(place.host().getText());
    }

    /** Once {@code stopAt} is included, code collection stops; imports and declarations are still collected. */
    private static void collect(@NotNull PsiFile file,
                                @NotNull Model model,
                                boolean root,
                                @Nullable VirtualFile stopAt,
                                @NotNull Set<VirtualFile> visiting,
                                int depth) {
        for (ASTNode node = file.getNode().getFirstChildNode(); node != null; node = node.getTreeNext()) {
            IElementType type = node.getElementType();
            String text = node.getText();
            if (type == JspElementTypes.DIRECTIVE) {
                String name = directiveName(text);
                Map<String, String> attributes = JspInsightUtil.parseAttributes(text);
                if ("page".equals(name)) {
                    String imports = attributes.get("import");
                    if (imports != null) {
                        for (String imported : imports.split(",")) {
                            String trimmed = imported.trim();
                            if (!trimmed.isEmpty()) {
                                model.imports.add(trimmed);
                            }
                        }
                    }
                } else if ("include".equals(name)) {
                    String path = attributes.get("file");
                    PsiFile target = path == null ? null : JspInsightUtil.resolveWebPath(file.getProject(), file, path.trim());
                    VirtualFile targetVf = target == null ? null : target.getVirtualFile();
                    if (targetVf == null) {
                        continue;
                    }
                    if (targetVf.equals(stopAt)) {
                        model.acceptCode = false;
                        continue;
                    }
                    if (depth < MAX_INCLUDE_DEPTH && target.getLanguage().isKindOf(JspLanguage.INSTANCE)
                            && visiting.add(targetVf)) {
                        collect(target, model, false, null, visiting, depth + 1);
                        visiting.remove(targetVf);
                    }
                }
                continue;
            }
            boolean declaration = type == JspElementTypes.SCRIPTLET_DECLARATION;
            boolean expression = type == JspElementTypes.SCRIPTLET_EXPRESSION;
            if (!declaration && !expression && type != JspElementTypes.SCRIPTLET) {
                continue;
            }
            TextRange range = contentRange(text, type);
            if (range == null || (!declaration && !model.acceptCode)) {
                continue;
            }
            List<Item> target = declaration ? model.declarations : model.code;
            if (root && node.getPsi() instanceof JspScriptletHost host) {
                target.add(new Place(host, range, expression));
            } else {
                String content = range.substring(text);
                target.add(new Text(expression ? "out.print(" + content + ");" : content));
            }
        }
    }

    private static @Nullable String directiveName(@NotNull String text) {
        int start;
        if (text.startsWith("<%@")) {
            start = 3;
            while (start < text.length() && Character.isWhitespace(text.charAt(start))) {
                start++;
            }
        } else if (text.startsWith("<jsp:directive.")) {
            start = "<jsp:directive.".length();
        } else {
            return null;
        }
        int end = start;
        while (end < text.length() && Character.isLetter(text.charAt(end))) {
            end++;
        }
        return end > start ? text.substring(start, end) : null;
    }

    private static @NotNull String servletNamespace(@NotNull PsiFile file) {
        try {
            JavaPsiFacade facade = JavaPsiFacade.getInstance(file.getProject());
            GlobalSearchScope scope = file.getResolveScope();
            if (facade.findClass("jakarta.servlet.http.HttpServletRequest", scope) != null) {
                return "jakarta";
            }
        } catch (IndexNotReadyException ignored) {
            // falls back to javax until indexing completes (the cache depends on DumbService)
        }
        return "javax";
    }

    /** Outermost-first chain of pages statically including {@code file}; only computed for .jspf fragments. */
    private static @NotNull List<PsiFile> includerChain(@NotNull PsiFile file) {
        List<PsiFile> chain = new ArrayList<>();
        VirtualFile current = file.getVirtualFile();
        if (current == null || !"jspf".equalsIgnoreCase(current.getExtension())) {
            return chain;
        }
        Project project = file.getProject();
        Map<VirtualFile, List<VirtualFile>> includers = includersIndex(project);
        Set<VirtualFile> seen = new HashSet<>();
        seen.add(current);
        PsiManager psiManager = PsiManager.getInstance(project);
        while (chain.size() < MAX_INCLUDE_DEPTH) {
            List<VirtualFile> candidates = includers.get(current);
            if (candidates == null || candidates.isEmpty()) {
                break;
            }
            VirtualFile includer = bestIncluder(current, candidates);
            PsiFile includerPsi = seen.add(includer) ? psiManager.findFile(includer) : null;
            if (includerPsi == null) {
                break;
            }
            chain.add(0, includerPsi);
            if (!"jspf".equalsIgnoreCase(includer.getExtension())) {
                break;
            }
            current = includer;
        }
        return chain;
    }

    /** Picks the includer nearest the fragment (most shared path segments), preferring full pages over other fragments. */
    static @NotNull VirtualFile bestIncluder(@NotNull VirtualFile fragment, @NotNull List<VirtualFile> candidates) {
        String[] fragmentSegments = fragment.getPath().split("/");
        VirtualFile best = candidates.get(0);
        int bestScore = -1;
        for (VirtualFile candidate : candidates) {
            String[] segments = candidate.getPath().split("/");
            int shared = 0;
            while (shared < segments.length - 1 && shared < fragmentSegments.length - 1
                    && segments[shared].equals(fragmentSegments[shared])) {
                shared++;
            }
            int score = shared * 2 + ("jspf".equalsIgnoreCase(candidate.getExtension()) ? 0 : 1);
            if (score > bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    /** All pages that statically include {@code file}, in path order. */
    static @NotNull List<PsiFile> includersOf(@NotNull PsiFile file) {
        VirtualFile vf = file.getVirtualFile();
        if (vf == null) {
            return List.of();
        }
        List<VirtualFile> includers = includersIndex(file.getProject()).get(vf);
        if (includers == null) {
            return List.of();
        }
        PsiManager psiManager = PsiManager.getInstance(file.getProject());
        List<PsiFile> result = new ArrayList<>();
        for (VirtualFile includer : includers) {
            PsiFile psi = psiManager.findFile(includer);
            if (psi != null) {
                result.add(psi);
            }
        }
        return result;
    }

    private static @NotNull Map<VirtualFile, List<VirtualFile>> includersIndex(@NotNull Project project) {
        CachedValue<Map<VirtualFile, List<VirtualFile>>> cached = project.getUserData(INCLUDERS_KEY);
        if (cached == null) {
            cached = CachedValuesManager.getManager(project).createCachedValue(() -> CachedValueProvider.Result.create(
                    buildIncludersIndex(project),
                    PsiModificationTracker.getInstance(project).forLanguage(JspLanguage.INSTANCE),
                    VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS,
                    ProjectRootManager.getInstance(project),
                    DumbService.getInstance(project)), false);
            project.putUserData(INCLUDERS_KEY, cached);
        }
        return cached.getValue();
    }

    private static @NotNull Map<VirtualFile, List<VirtualFile>> buildIncludersIndex(@NotNull Project project) {
        Map<VirtualFile, List<VirtualFile>> result = new HashMap<>();
        List<VirtualFile> files = new ArrayList<>();
        try {
            GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
            for (String extension : List.of("jsp", "jspf", "jspx")) {
                files.addAll(FilenameIndex.getAllFilesByExt(project, extension, scope));
            }
        } catch (IndexNotReadyException e) {
            return result;
        }
        files.sort(Comparator.comparing(VirtualFile::getPath));
        PsiManager psiManager = PsiManager.getInstance(project);
        for (VirtualFile vf : files) {
            PsiFile psi = psiManager.findFile(vf);
            if (psi == null) {
                continue;
            }
            for (VirtualFile targetVf : includeTargets(psi)) {
                List<VirtualFile> list = result.computeIfAbsent(targetVf, key -> new ArrayList<>());
                if (!list.contains(vf)) {
                    list.add(vf);
                }
            }
        }
        return result;
    }

    /** Static include targets of one file, cached on that file so an index rebuild re-scans only edited files. */
    private static @NotNull List<VirtualFile> includeTargets(@NotNull PsiFile psi) {
        return CachedValuesManager.getCachedValue(psi, () -> {
            Project project = psi.getProject();
            VirtualFile self = psi.getVirtualFile();
            List<VirtualFile> targets = new ArrayList<>();
            Matcher matcher = INCLUDE_PATTERN.matcher(psi.getViewProvider().getContents());
            while (matcher.find()) {
                PsiFile target = JspInsightUtil.resolveWebPath(project, psi, matcher.group(1).trim());
                VirtualFile targetVf = target == null ? null : target.getVirtualFile();
                if (targetVf != null && !targetVf.equals(self) && !targets.contains(targetVf)) {
                    targets.add(targetVf);
                }
            }
            return CachedValueProvider.Result.create(List.copyOf(targets), psi,
                    VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS,
                    ProjectRootManager.getInstance(project));
        });
    }
}
