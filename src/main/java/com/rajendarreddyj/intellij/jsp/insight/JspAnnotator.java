package com.rajendarreddyj.intellij.jsp.insight;

import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.AnnotationBuilder;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.rajendarreddyj.intellij.jsp.language.JspElementTypes;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class JspAnnotator implements Annotator {
    private static final Set<String> RESERVED_PREFIXES = Set.of("jsp", "jspx", "xml", "xmlns");
    private static final java.util.regex.Pattern PAGE_DIRECTIVE = java.util.regex.Pattern.compile("^<(%@|jsp:directive\\.)\\s*page\\b");

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (element.getNode().getElementType() == JspElementTypes.ERROR) {
            holder.newAnnotation(HighlightSeverity.ERROR, "Malformed JSP region").range(element).create();
            return;
        }

        if (!(element instanceof PsiFile file)) {
            return;
        }

        Map<String, String> declaredPrefixes = JspInsightUtil.effectiveTaglibs(file);
        if (!com.intellij.openapi.project.DumbService.isDumb(file.getProject())) {
            for (ASTNode node = file.getNode().getFirstChildNode(); node != null; node = node.getTreeNext()) {
                if (node.getElementType() == JspElementTypes.DIRECTIVE) {
                    validateImports(file, node, holder);
                }
            }
        }
        // Only markup regions are scanned: tags inside comments, scriptlets and directives are not custom tags.
        for (ASTNode node = file.getNode().getFirstChildNode(); node != null; node = node.getTreeNext()) {
            IElementType type = node.getElementType();
            if (type != JspElementTypes.TEMPLATE_TEXT && type != JspElementTypes.ACTION) {
                continue;
            }
            int base = node.getStartOffset();
            for (JspInsightUtil.OpenTag open : JspInsightUtil.collectOpenTags(node.getText())) {
                validateAttributes(file, declaredPrefixes, open, base, holder);
            }
            List<JspInsightUtil.TagUse> customTags = JspInsightUtil.collectCustomTags(node.getText());
            for (JspInsightUtil.TagUse tagUse : customTags) {
                String prefix = tagUse.prefix();
                if (RESERVED_PREFIXES.contains(prefix) || declaredPrefixes.containsKey(prefix)) {
                    continue;
                }
                AnnotationBuilder builder = holder.newAnnotation(HighlightSeverity.WARNING,
                                "Undeclared JSP tag prefix '" + prefix + "'")
                        .range(TextRange.create(base + tagUse.start(), base + tagUse.end()));
                JspInsightUtil.TldInfo tld = JspInsightUtil.findTldByIdentifier(file.getProject(), prefix);
                if (tld != null && tld.uri() != null) {
                    builder = builder.withFix(new AddTaglibDirectiveFix(prefix, tld.uri()));
                }
                builder.create();
            }
        }
    }

    private static void validateImports(PsiFile file, ASTNode node, AnnotationHolder holder) {
        String text = node.getText();
        if (!PAGE_DIRECTIVE.matcher(text).find()) {
            return;
        }
        TextRange importRange = JspInsightUtil.findAttributeValueRange(text, "import");
        if (importRange == null) {
            return;
        }
        String value = importRange.substring(text);
        int base = node.getStartOffset() + importRange.getStartOffset();
        int entryStart = 0;
        while (entryStart <= value.length()) {
            int comma = value.indexOf(',', entryStart);
            int entryEnd = comma < 0 ? value.length() : comma;
            int from = entryStart;
            int to = entryEnd;
            while (from < to && Character.isWhitespace(value.charAt(from))) {
                from++;
            }
            while (to > from && Character.isWhitespace(value.charAt(to - 1))) {
                to--;
            }
            String entry = value.substring(from, to);
            if (!entry.isEmpty()) {
                String problem = importProblem(file, entry);
                if (problem != null) {
                    int removeFrom = from;
                    int removeTo = comma < 0 ? to : comma + 1;
                    if (comma < 0 && entryStart > 0) {
                        removeFrom = entryStart - 1;
                    }
                    holder.newAnnotation(HighlightSeverity.WARNING, problem)
                            .range(TextRange.create(base + from, base + to))
                            .withFix(new RemoveUnresolvedImportFix(entry, TextRange.create(base + removeFrom, base + removeTo)))
                            .create();
                }
            }
            if (comma < 0) {
                break;
            }
            entryStart = comma + 1;
        }
    }

    private static String importProblem(PsiFile file, String entry) {
        com.intellij.openapi.project.Project project = file.getProject();
        if (entry.endsWith(".*")) {
            String packageName = entry.substring(0, entry.length() - 2);
            return com.intellij.psi.JavaPsiFacade.getInstance(project).findPackage(packageName) == null
                    ? "Cannot resolve package '" + packageName + "'" : null;
        }
        return JspInsightUtil.findClassByQualifiedName(project, entry) == null
                ? "Cannot resolve class '" + entry + "'" : null;
    }

    private static void validateAttributes(PsiFile file, Map<String, String> declaredPrefixes,
                                           JspInsightUtil.OpenTag open, int base, AnnotationHolder holder) {
        JspInsightUtil.TldInfo tld = JspInsightUtil.findTldForPrefix(file.getProject(), declaredPrefixes, open.prefix());
        JspInsightUtil.TagInfo tag = tld == null ? null : tld.tagInfos().get(open.name());
        if (tag == null) {
            return;
        }
        Set<String> present = JspInsightUtil.parseAttributes(open.attributeText()).keySet();
        if (!tag.dynamicAttributes()) {
            for (String name : present) {
                if (tag.attributes().stream().noneMatch(declared -> declared.equalsIgnoreCase(name))) {
                    int from = open.attributesStart() + open.attributeText().toLowerCase(java.util.Locale.ROOT).indexOf(name);
                    holder.newAnnotation(HighlightSeverity.WARNING, "Unknown attribute '" + name + "' for tag '"
                                    + open.prefix() + ":" + open.name() + "'")
                            .range(TextRange.create(base + from, base + from + name.length())).create();
                }
            }
        }
        for (String required : tag.required()) {
            if (!present.contains(required.toLowerCase(java.util.Locale.ROOT))) {
                holder.newAnnotation(HighlightSeverity.ERROR, "Missing required attribute '" + required + "'")
                        .range(TextRange.create(base + open.start() + 1, base + open.start() + 1
                                + open.prefix().length() + 1 + open.name().length())).create();
            }
        }
    }
}