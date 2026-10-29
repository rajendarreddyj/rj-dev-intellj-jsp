package com.rajendarreddyj.intellij.jsp.language;

import com.intellij.lang.cacheBuilder.SimpleWordsScanner;
import com.intellij.lang.cacheBuilder.WordsScanner;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Indexes JSP words so Find Usages on an included fragment finds the {@code include} directives that name it. */
public final class JspFindUsagesProvider implements FindUsagesProvider {
    @Override
    public @Nullable WordsScanner getWordsScanner() {
        return new SimpleWordsScanner();
    }

    @Override
    public boolean canFindUsagesFor(@NotNull PsiElement element) {
        return element instanceof PsiFile;
    }

    @Override
    public @Nullable String getHelpId(@NotNull PsiElement element) {
        return null;
    }

    @Override
    public @NotNull String getType(@NotNull PsiElement element) {
        return "file";
    }

    @Override
    public @NotNull String getDescriptiveName(@NotNull PsiElement element) {
        return element instanceof PsiNamedElement named && named.getName() != null ? named.getName() : "";
    }

    @Override
    public @NotNull String getNodeText(@NotNull PsiElement element, boolean useFullName) {
        return getDescriptiveName(element);
    }
}
