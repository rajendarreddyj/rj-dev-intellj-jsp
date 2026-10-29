package com.rajendarreddyj.intellij.jsp.insight;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.rajendarreddyj.intellij.jsp.language.JspLanguage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Variables an EL expression can name: {@code jsp:useBean} ids, {@code c:set}/{@code c:forEach} vars, tag-file
 * {@code attribute} directives and {@code setAttribute("name", new Type())} calls, with best-effort type inference so
 * property chains like {@code ${order.customer.name}} resolve through getter return types.
 */
final class JspElVariables {
    private static final int MAX_DEPTH = 8;
    private static final Pattern VAR_TAG = Pattern.compile(
            "<([A-Za-z_][\\w.-]*):(set|forEach|forTokens)\\b((?:[^>\"']|\"[^\"]*\"|'[^']*')*)>");
    private static final Pattern VAR_ATTR = Pattern.compile("\\b(var|varStatus)\\s*=\\s*([\"'])([^\"']+)\\2");
    private static final Pattern EL_WHOLE = Pattern.compile("^\\s*[$#]\\{(.*)}\\s*$", Pattern.DOTALL);
    private static final Pattern SET_ATTRIBUTE = Pattern.compile(
            "\\.\\s*setAttribute\\s*\\(\\s*\"([^\"]+)\"\\s*,\\s*(?:new\\s+([\\w.$]+)\\s*[(<])?");
    private static final Pattern ATTRIBUTE_DIRECTIVE = Pattern.compile("<%@\\s*attribute\\b([^%]*)%>");
    private static final Pattern PAGE_IMPORT = Pattern.compile("<%@\\s*page\\b[^%]*?\\bimport\\s*=\\s*\"([^\"]*)\"");
    private static final Pattern CHAIN = Pattern.compile("(?<![\\w.$'\"])[A-Za-z_]\\w*(?:\\s*\\.\\s*[A-Za-z_]\\w*)*");

    /** One EL-visible name; {@code file} and offsets locate its declaration (null for text-only analysis). */
    record ElVar(@NotNull String name, @Nullable String className, @Nullable String valueExpression,
                 @Nullable String itemsExpression, @Nullable PsiFile file, int start, int end) {
    }

    private JspElVariables() {
    }

    static @NotNull List<ElVar> variables(@NotNull PsiFile file) {
        return CachedValuesManager.getCachedValue(file, () -> {
            List<ElVar> result = new ArrayList<>();
            gather(file, true, new HashSet<>(), result, 0);
            return com.intellij.psi.util.CachedValueProvider.Result.create(Collections.unmodifiableList(result),
                    PsiModificationTracker.getInstance(file.getProject()).forLanguage(JspLanguage.INSTANCE),
                    VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS);
        });
    }

    private static void gather(PsiFile file, boolean upward, Set<VirtualFile> visited, List<ElVar> out, int depth) {
        VirtualFile vf = file.getVirtualFile();
        if (depth > 16 || (vf != null && !visited.add(vf))) {
            return;
        }
        String text = file.getViewProvider().getContents().toString();
        out.addAll(collect(text, file));
        Matcher include = JspJavaTranslation.INCLUDE_PATTERN.matcher(text);
        while (include.find()) {
            PsiFile target = JspInsightUtil.resolveWebPath(file.getProject(), file, include.group(1).trim());
            if (target != null && target.getLanguage().isKindOf(JspLanguage.INSTANCE)) {
                gather(target, false, visited, out, depth + 1);
            }
        }
        if (upward && vf != null) {
            for (PsiFile includer : JspJavaTranslation.includersOf(file)) {
                gather(includer, true, visited, out, depth + 1);
            }
        }
    }

    static @NotNull List<ElVar> collect(@NotNull String text, @Nullable PsiFile file) {
        List<ElVar> vars = new ArrayList<>();
        for (JspInsightUtil.UseBeanDecl bean : JspInsightUtil.collectUseBeans(text)) {
            vars.add(new ElVar(bean.id(), bean.className(), null, null, file, bean.idStart(), bean.idEnd()));
        }
        Matcher tag = VAR_TAG.matcher(text);
        while (tag.find()) {
            String attributes = tag.group(3);
            java.util.Map<String, String> parsed = JspInsightUtil.parseAttributes(attributes);
            String value = parsed.get("value");
            String items = parsed.get("items");
            Matcher var = VAR_ATTR.matcher(attributes);
            while (var.find()) {
                boolean status = "varStatus".equals(var.group(1));
                int start = tag.start(3) + var.start(3);
                vars.add(new ElVar(var.group(3), null, status ? null : elBody(value), status ? null : elBody(items),
                        file, start, start + var.group(3).length()));
            }
        }
        Matcher attribute = SET_ATTRIBUTE.matcher(text);
        while (attribute.find()) {
            vars.add(new ElVar(attribute.group(1), attribute.group(2), null, null, file,
                    attribute.start(1), attribute.end(1)));
        }
        Matcher directive = ATTRIBUTE_DIRECTIVE.matcher(text);
        while (directive.find()) {
            java.util.Map<String, String> parsed = JspInsightUtil.parseAttributes(directive.group(1));
            String name = parsed.get("name");
            if (name != null) {
                int start = directive.start(1) + Math.max(0, directive.group(1).indexOf(name));
                String type = parsed.getOrDefault("type", "java.lang.String");
                vars.add(new ElVar(name, type, null, null, file, start, start + name.length()));
            }
        }
        return vars;
    }

    private static @Nullable String elBody(@Nullable String attributeValue) {
        if (attributeValue == null) {
            return null;
        }
        Matcher matcher = EL_WHOLE.matcher(attributeValue);
        return matcher.matches() ? matcher.group(1).trim() : null;
    }

    static @Nullable ElVar find(@NotNull List<ElVar> vars, @NotNull String name) {
        for (ElVar var : vars) {
            if (var.name().equals(name)) {
                return var;
            }
        }
        return null;
    }

    /** Declaration target for navigation; falls back to the file itself when the offset is unknown. */
    static @Nullable PsiElement declaration(@NotNull ElVar var, @NotNull PsiFile fallback) {
        PsiFile file = var.file() != null ? var.file() : fallback;
        if (var.start() < 0 || var.end() <= var.start() || var.end() > file.getTextLength()) {
            return file;
        }
        PsiElement target = file.findElementAt(var.start());
        return target != null ? target : file;
    }

    /** Type of the property chain {@code names[0..count)}; names[0] must be a known variable. */
    static @Nullable PsiType typeOfChain(@NotNull Project project, @Nullable PsiFile context, @NotNull List<ElVar> vars,
                                         @NotNull List<String> names, int count, int depth) {
        if (count <= 0 || depth > MAX_DEPTH) {
            return null;
        }
        ElVar var = find(vars, names.get(0));
        PsiType type = var == null ? null : typeOf(project, context, vars, var, depth + 1);
        for (int i = 1; i < Math.min(count, names.size()) && type != null; i++) {
            PsiMethod getter = property(type, names.get(i));
            type = getter == null ? null : returnType(getter, type);
        }
        return type;
    }

    private static @Nullable PsiType typeOf(Project project, PsiFile context, List<ElVar> vars, ElVar var, int depth) {
        if (var.className() != null) {
            return classType(project, context, var.className());
        }
        if (var.valueExpression() != null) {
            return typeOfExpression(project, context, vars, var.valueExpression(), depth);
        }
        if (var.itemsExpression() != null) {
            PsiType collection = typeOfExpression(project, context, vars, var.itemsExpression(), depth);
            if (collection instanceof PsiArrayType array) {
                return array.getComponentType();
            }
            return collection == null ? null : PsiUtil.extractIterableTypeParameter(collection, false);
        }
        return null;
    }

    private static @Nullable PsiType typeOfExpression(Project project, PsiFile context, List<ElVar> vars,
                                                      String expression, int depth) {
        Matcher matcher = CHAIN.matcher(expression);
        if (!matcher.matches()) {
            return null;
        }
        return typeOfChain(project, context, vars, splitChain(matcher.group()), Integer.MAX_VALUE, depth);
    }

    static @NotNull List<String> splitChain(@NotNull String chain) {
        List<String> names = new ArrayList<>();
        for (String part : chain.split("\\.")) {
            names.add(part.trim());
        }
        return names;
    }

    static @NotNull Pattern chainPattern() {
        return CHAIN;
    }

    static @Nullable PsiClass classOf(@Nullable PsiType type) {
        return type instanceof PsiClassType classType ? classType.resolve() : null;
    }

    static @Nullable PsiMethod property(@NotNull PsiType type, @NotNull String name) {
        PsiClass psiClass = classOf(type);
        if (psiClass == null || name.isEmpty()) {
            return null;
        }
        String capitalized = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        for (String prefix : new String[] {"get", "is"}) {
            for (PsiMethod method : psiClass.findMethodsByName(prefix + capitalized, true)) {
                if (method.getParameterList().isEmpty()) {
                    return method;
                }
            }
        }
        return null;
    }

    private static @Nullable PsiType returnType(PsiMethod getter, PsiType ownerType) {
        PsiType returned = getter.getReturnType();
        PsiClass owner = getter.getContainingClass();
        if (returned == null || owner == null || !(ownerType instanceof PsiClassType classType)) {
            return returned;
        }
        PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(owner, classType);
        return substitutor.substitute(returned);
    }

    static @Nullable PsiType classType(@NotNull Project project, @Nullable PsiFile context, @NotNull String name) {
        PsiClass psiClass = resolveClass(project, context, name);
        return psiClass == null ? null : JavaPsiFacade.getElementFactory(project).createType(psiClass);
    }

    /** Resolves a fully qualified name, then a simple name through the page's {@code import} entries and java.lang. */
    static @Nullable PsiClass resolveClass(@NotNull Project project, @Nullable PsiFile context, @NotNull String name) {
        PsiClass direct = JspInsightUtil.findClassByQualifiedName(project, name);
        if (direct != null || context == null || name.indexOf('.') >= 0) {
            return direct;
        }
        Matcher imports = PAGE_IMPORT.matcher(context.getViewProvider().getContents());
        while (imports.find()) {
            for (String entry : imports.group(1).split(",")) {
                String trimmed = entry.trim();
                String candidate = trimmed.endsWith(".*") ? trimmed.substring(0, trimmed.length() - 1) + name
                        : trimmed.endsWith("." + name) ? trimmed : null;
                PsiClass found = candidate == null ? null : JspInsightUtil.findClassByQualifiedName(project, candidate);
                if (found != null) {
                    return found;
                }
            }
        }
        return JspInsightUtil.findClassByQualifiedName(project, "java.lang." + name);
    }
}
