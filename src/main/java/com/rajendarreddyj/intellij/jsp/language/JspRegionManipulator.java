package com.rajendarreddyj.intellij.jsp.language;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/** Lets reference rename and move rewrite text inside a JSP region, which has no replaceable child PSI. */
public final class JspRegionManipulator extends AbstractElementManipulator<JspRegionPsiElement> {
    @Override
    public JspRegionPsiElement handleContentChange(@NotNull JspRegionPsiElement element, @NotNull TextRange range,
                                                   String newContent) throws IncorrectOperationException {
        PsiFile file = element.getContainingFile();
        PsiDocumentManager documents = PsiDocumentManager.getInstance(element.getProject());
        Document document = documents.getDocument(file);
        if (document == null) {
            throw new IncorrectOperationException("No document for " + file.getName());
        }
        int start = element.getTextRange().getStartOffset() + range.getStartOffset();
        documents.doPostponedOperationsAndUnblockDocument(document);
        document.replaceString(start, start + range.getLength(), newContent);
        documents.commitDocument(document);
        JspRegionPsiElement updated = PsiTreeUtil.getParentOfType(file.findElementAt(start), JspRegionPsiElement.class, false);
        return updated != null ? updated : element;
    }
}
