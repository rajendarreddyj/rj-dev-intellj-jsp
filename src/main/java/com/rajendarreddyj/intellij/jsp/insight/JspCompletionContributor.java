package com.rajendarreddyj.intellij.jsp.insight;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JspCompletionContributor extends CompletionContributor {
    private static final Pattern MEMBER_TARGET = Pattern.compile("([A-Za-z_][\\w]*)\\.$");
    private static final List<String> DIRECTIVES = List.of("attribute", "include", "page", "tag", "taglib", "variable");
    private static final List<String> ACTIONS = List.of(
            "attribute", "body", "element", "fallback", "forward", "getProperty", "include", "param", "params", "plugin", "setProperty", "text", "useBean"
    );
    private static final List<String> EL_IMPLICIT_OBJECTS = List.of(
            "applicationScope", "cookie", "header", "headerValues", "initParam", "pageContext", "pageScope",
            "param", "paramValues", "requestScope", "sessionScope"
    );

    public JspCompletionContributor() {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), new CompletionProvider<>() {
            @Override
            protected void addCompletions(@NotNull CompletionParameters parameters,
                                          @NotNull ProcessingContext context,
                                          @NotNull CompletionResultSet result) {
                addJspCompletions(parameters, result);
            }
        });
    }

    private static void addJspCompletions(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
        PsiFile file = parameters.getOriginalFile();
        if (!file.getLanguage().isKindOf(com.rajendarreddyj.intellij.jsp.language.JspLanguage.INSTANCE)) {
            return;
        }
        String text = file.getText();
        int offset = parameters.getOffset();
        Set<String> items = new LinkedHashSet<>();
        Kind kind = collect(file.getProject(), text, offset, JspInsightUtil.effectiveTaglibs(file),
                JspElVariables.variables(file), file, items);
        if (kind == Kind.NONE) {
            return;
        }
        // Names are matched against the text after the trigger (tag/member name), so the default matcher is reset.
        CompletionResultSet target = result.withPrefixMatcher("");
        items.stream().sorted().forEach(item -> target.addElement(LookupElementBuilder.create(item)));
    }

    static @NotNull List<String> collectCompletionItems(@NotNull Project project, @NotNull String text, int offset) {
        Set<String> items = new LinkedHashSet<>();
        collect(project, text, offset, JspInsightUtil.collectTaglibs(text), JspElVariables.collect(text, null), null, items);
        return items.stream().sorted().toList();
    }

    private enum Kind { NONE, DIRECTIVE, SCRIPTLET_MEMBER, ACTION, CUSTOM_TAG, TAG_ATTRIBUTE, EL }

    private static @NotNull Kind collect(@NotNull Project project,
                                         @NotNull String text,
                                         int offset,
                                         @NotNull Map<String, String> taglibs,
                                         @NotNull List<JspElVariables.ElVar> vars,
                                         @Nullable PsiFile file,
                                         @NotNull Set<String> items) {
        if (JspInsightUtil.isDirectiveNamePosition(text, offset)) {
            items.addAll(DIRECTIVES);
            return Kind.DIRECTIVE;
        }
        if (collectScriptletMembers(project, text, offset, items)) {
            return Kind.SCRIPTLET_MEMBER;
        }
        if (JspInsightUtil.isActionNamePosition(text, offset)) {
            items.addAll(ACTIONS);
            return Kind.ACTION;
        }
        String[] attributePosition = JspInsightUtil.findAttributePosition(text, offset);
        if (attributePosition != null) {
            JspInsightUtil.TldInfo tld = JspInsightUtil.findTldForPrefix(project, taglibs, attributePosition[0]);
            JspInsightUtil.TagInfo tag = tld == null ? null : tld.tagInfos().get(attributePosition[1]);
            if (tag != null) {
                int lt = text.lastIndexOf('<', offset - 1);
                Set<String> present = JspInsightUtil.parseAttributes(text.substring(lt, offset)).keySet();
                tag.attributes().stream().filter(name -> !present.contains(name.toLowerCase(java.util.Locale.ROOT))).forEach(items::add);
                return Kind.TAG_ATTRIBUTE;
            }
        }
        String customTagPrefix = JspInsightUtil.findCustomTagPrefixForCompletion(text, offset);
        if (customTagPrefix != null) {
            String uri = taglibs.get(customTagPrefix);
            if (uri != null) {
                Set<String> tags = new JspTagLibraryService().getTags(project, uri);
                if (tags.isEmpty()) {
                    tags = new JspTagLibraryService().getTags(project, customTagPrefix);
                }
                items.addAll(tags);
            }
            return Kind.CUSTOM_TAG;
        }
        JspInsightUtil.ElSegment elSegment = JspInsightUtil.findElSegmentAt(text, offset);
        if (elSegment != null) {
            collectElItems(project, file, vars, offset, elSegment, items);
            return Kind.EL;
        }
        return Kind.NONE;
    }

    private static boolean collectScriptletMembers(@NotNull Project project,
                                                   @NotNull String text,
                                                   int offset,
                                                   @NotNull Set<String> items) {
        int open = text.lastIndexOf("<%", offset);
        if (open < 0 || text.startsWith("<%@", open)) {
            return false;
        }
        int close = text.indexOf("%>", open);
        if (close < 0 || offset > close) {
            return false;
        }
        int contentStart = open + (text.startsWith("<%=", open) || text.startsWith("<%!", open) ? 3 : 2);
        if (contentStart > offset) {
            return false;
        }
        String scriptletBeforeCaret = text.substring(contentStart, offset);
        Matcher targetMatcher = MEMBER_TARGET.matcher(scriptletBeforeCaret);
        if (!targetMatcher.find()) {
            return false;
        }
        String variableName = targetMatcher.group(1);
        Matcher declarationMatcher = Pattern.compile("([A-Za-z_][\\w.$]*)\\s+" + Pattern.quote(variableName) + "\\s*(?:=|;)").matcher(scriptletBeforeCaret);
        if (!declarationMatcher.find()) {
            return false;
        }
        String className = declarationMatcher.group(1);
        if ("String".equals(className) || "java.lang.String".equals(className)) {
            items.add("length");
            return true;
        }
        PsiClass psiClass = JspInsightUtil.findClassByQualifiedName(project, className);
        if (psiClass == null) {
            return false;
        }
        for (PsiMethod method : psiClass.getAllMethods()) {
            if (method.getParameterList().isEmpty()) {
                items.add(method.getName());
            }
        }
        return true;
    }

    private static final Pattern EL_TRAILING_CHAIN = Pattern.compile("([A-Za-z_]\\w*(?:\\s*\\.\\s*[A-Za-z_]\\w*)*)\\s*\\.\\s*\\w*$");

    private static void collectElItems(@NotNull Project project,
                                       @Nullable PsiFile file,
                                       @NotNull List<JspElVariables.ElVar> vars,
                                       int offset,
                                       @NotNull JspInsightUtil.ElSegment elSegment,
                                       @NotNull Set<String> items) {
        String content = elSegment.content();
        int localOffset = Math.max(0, offset - elSegment.contentStart());
        String beforeCaret = content.substring(0, Math.min(localOffset, content.length()));
        Matcher chain = EL_TRAILING_CHAIN.matcher(beforeCaret);
        if (!chain.find()) {
            items.addAll(EL_IMPLICIT_OBJECTS);
            vars.forEach(var -> items.add(var.name()));
            return;
        }
        List<String> names = JspElVariables.splitChain(chain.group(1));
        PsiClass psiClass = JspElVariables.classOf(
                JspElVariables.typeOfChain(project, file, vars, names, names.size(), 0));
        if (psiClass == null) {
            return;
        }
        for (PsiMethod method : psiClass.getAllMethods()) {
            String name = method.getName();
            if (!method.getParameterList().isEmpty()) {
                continue;
            }
            if (name.startsWith("get") && name.length() > 3) {
                items.add(Character.toLowerCase(name.charAt(3)) + name.substring(4));
            }
            if (name.startsWith("is") && name.length() > 2) {
                items.add(Character.toLowerCase(name.charAt(2)) + name.substring(3));
            }
        }
    }
}

