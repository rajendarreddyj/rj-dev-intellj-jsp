package com.rajendarreddyj.intellij.jsp.language;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

public class JspRegionPsiElement extends ASTWrapperPsiElement {
    public JspRegionPsiElement(@NotNull ASTNode node) {
        super(node);
    }

    // ASTWrapperPsiElement ignores the reference-contributor registry, which breaks Ctrl+Click and Ctrl+B.
    @Override
    public com.intellij.psi.PsiReference @NotNull [] getReferences() {
        return com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry.getReferencesFromProviders(this);
    }

    @Override
    public com.intellij.psi.PsiReference getReference() {
        com.intellij.psi.PsiReference[] references = getReferences();
        return references.length == 0 ? null : references[0];
    }
}
