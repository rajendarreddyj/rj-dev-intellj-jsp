package com.rajendarreddyj.intellij.jsp.insight;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** A {@code file}/{@code page} attribute path that resolves to a web-root relative file and survives rename and move. */
final class JspPathReference extends PsiReferenceBase<PsiElement> {
    private final String path;

    JspPathReference(@NotNull PsiElement element, @NotNull TextRange range, @NotNull String path) {
        super(element, range, false);
        this.path = path;
    }

    @Override
    public @Nullable PsiElement resolve() {
        return JspInsightUtil.resolveWebPath(getElement().getProject(), getElement().getContainingFile(), path);
    }

    @Override
    public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
        int slash = path.lastIndexOf('/');
        return ElementManipulators.handleContentChange(getElement(), getRangeInElement(),
                path.substring(0, slash + 1) + newElementName);
    }

    @Override
    public PsiElement bindToElement(@NotNull PsiElement target) throws IncorrectOperationException {
        if (!(target instanceof PsiFile targetFile)) {
            throw new IncorrectOperationException("Not a file: " + target);
        }
        String newPath = JspInsightUtil.webPathFor(getElement().getContainingFile(), targetFile, path.startsWith("/"));
        if (newPath == null) {
            throw new IncorrectOperationException("File is outside every web root: " + targetFile.getName());
        }
        return ElementManipulators.handleContentChange(getElement(), getRangeInElement(), newPath);
    }
}
