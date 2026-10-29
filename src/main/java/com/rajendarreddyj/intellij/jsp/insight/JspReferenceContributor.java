package com.rajendarreddyj.intellij.jsp.insight;

import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class JspReferenceContributor extends PsiReferenceContributor {
    private static final java.util.regex.Pattern PAGE_DIRECTIVE = java.util.regex.Pattern.compile("^<(%@|jsp:directive\\.)\\s*page\\b");
    private static final java.util.regex.Pattern IDENTIFIER = java.util.regex.Pattern.compile("[A-Za-z_]\\w*");
    private static final java.util.regex.Pattern EL_BODY = java.util.regex.Pattern.compile("(\\$|#)\\{([^}]*)}");

    @Override
    public void registerReferenceProviders(@NotNull com.intellij.psi.PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(PlatformPatterns.psiElement(com.rajendarreddyj.intellij.jsp.language.JspRegionPsiElement.class), new PsiReferenceProvider() {
            @Override
            public @NotNull PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
                return referencesFor(element);
            }
        });
        registrar.registerReferenceProvider(PlatformPatterns.psiFile().withLanguage(com.rajendarreddyj.intellij.jsp.language.JspLanguage.INSTANCE), new PsiReferenceProvider() {
            @Override
            public @NotNull PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
                return referencesFor(element);
            }
        });
    }

    static @NotNull PsiReference[] referencesFor(@NotNull PsiElement element) {
        PsiElement host = findJspRegionOrFile(element);
        if (host == null) {
            return PsiReference.EMPTY_ARRAY;
        }

        String text = host.getText();
        List<PsiReference> references = new ArrayList<>();

        if (text.startsWith("<%@") || text.startsWith("<jsp:directive.")) {
            addDirectiveReferences(host, text, references);
        } else if (text.startsWith("<jsp:include")) {
            addActionReferences(host, text, references);
        }

        if (text.contains("${") || text.contains("#{")) {
            addElReferences(host, text, references);
        }

        return references.toArray(PsiReference.EMPTY_ARRAY);
    }

    private static @Nullable PsiElement findJspRegionOrFile(@NotNull PsiElement element) {
        PsiElement current = element;
        while (current != null) {
            if (current instanceof com.rajendarreddyj.intellij.jsp.language.JspRegionPsiElement || current instanceof PsiFile) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    private static void addDirectiveReferences(@NotNull PsiElement element, @NotNull String text, @NotNull List<PsiReference> references) {
        if (text.contains("include")) {
            TextRange fileRange = JspInsightUtil.findAttributeValueRange(text, "file");
            if (fileRange != null) {
                String includePath = fileRange.substring(text);
                references.add(new JspPathReference(element, fileRange, includePath));
            }
        }

        if (text.contains("page")) {
            addImportReferences(element, text, references);
        }

        if (text.contains("taglib")) {
            TextRange uriRange = JspInsightUtil.findAttributeValueRange(text, "uri");
            if (uriRange != null) {
                String uri = uriRange.substring(text);
                references.add(new PsiReferenceBase<>(element, uriRange, false) {
                    @Override
                    public @Nullable PsiElement resolve() {
                        return new JspTagLibraryService().resolveTagLibrary(element.getProject(), element.getContainingFile(), uri);
                    }
                });
            }
        }
    }

    private static void addImportReferences(@NotNull PsiElement element, @NotNull String text, @NotNull List<PsiReference> references) {
        if (!PAGE_DIRECTIVE.matcher(text).find()) {
            return;
        }
        TextRange importRange = JspInsightUtil.findAttributeValueRange(text, "import");
        if (importRange == null) {
            return;
        }
        String value = importRange.substring(text);
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
            String qualifiedName = value.substring(from, to);
            if (!qualifiedName.isEmpty() && !qualifiedName.endsWith("*")) {
                TextRange range = TextRange.create(importRange.getStartOffset() + from, importRange.getStartOffset() + to);
                references.add(new PsiReferenceBase<>(element, range, false) {
                    @Override
                    public @Nullable PsiElement resolve() {
                        return JspInsightUtil.findClassByQualifiedName(element.getProject(), qualifiedName);
                    }
                });
            }
            if (comma < 0) {
                break;
            }
            entryStart = comma + 1;
        }
    }

    private static void addActionReferences(@NotNull PsiElement element, @NotNull String text, @NotNull List<PsiReference> references) {
        TextRange pageRange = JspInsightUtil.findAttributeValueRange(text, "page");
        if (pageRange == null) {
            return;
        }
        String includePath = pageRange.substring(text);
        references.add(new JspPathReference(element, pageRange, includePath));
    }

    private static void addElReferences(@NotNull PsiElement element, @NotNull String text, @NotNull List<PsiReference> references) {
        PsiFile file = element.getContainingFile();
        List<JspElVariables.ElVar> vars = JspElVariables.variables(file);
        if (vars.isEmpty()) {
            return;
        }

        java.util.regex.Matcher el = EL_BODY.matcher(text);
        while (el.find()) {
            int bodyStart = el.start(2);
            java.util.regex.Matcher chain = JspElVariables.chainPattern().matcher(el.group(2));
            while (chain.find()) {
                List<String> names = new ArrayList<>();
                List<TextRange> ranges = new ArrayList<>();
                java.util.regex.Matcher part = IDENTIFIER.matcher(chain.group());
                while (part.find()) {
                    names.add(part.group());
                    ranges.add(TextRange.create(bodyStart + chain.start() + part.start(), bodyStart + chain.start() + part.end()));
                }
                JspElVariables.ElVar var = JspElVariables.find(vars, names.get(0));
                if (var == null) {
                    continue;
                }
                references.add(new PsiReferenceBase<>(element, ranges.get(0), false) {
                    @Override
                    public @Nullable PsiElement resolve() {
                        return JspElVariables.declaration(var, file);
                    }
                });
                for (int i = 1; i < names.size(); i++) {
                    int index = i;
                    references.add(new PsiReferenceBase<>(element, ranges.get(i), false) {
                        @Override
                        public @Nullable PsiElement resolve() {
                            PsiType owner = JspElVariables.typeOfChain(element.getProject(), file, vars, names, index, 0);
                            return owner == null ? null : JspElVariables.property(owner, names.get(index));
                        }
                    });
                }
            }
        }
    }
}
