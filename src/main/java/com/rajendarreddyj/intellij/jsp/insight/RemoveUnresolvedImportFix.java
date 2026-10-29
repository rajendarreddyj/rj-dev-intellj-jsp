package com.rajendarreddyj.intellij.jsp.insight;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/** Deletes one entry (and its separating comma) from a {@code page import} attribute. */
final class RemoveUnresolvedImportFix implements IntentionAction {
    private final String text;
    private final TextRange removal;

    RemoveUnresolvedImportFix(@NotNull String text, @NotNull TextRange removal) {
        this.text = text;
        this.removal = removal;
    }

    @Override
    public @NotNull String getText() {
        return "Remove unresolved import '" + text + "'";
    }

    @Override
    public @NotNull String getFamilyName() {
        return "Remove unresolved import";
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
        return file.isValid() && removal.getEndOffset() <= file.getTextLength();
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
        Document document = PsiDocumentManager.getInstance(project).getDocument(file);
        if (document == null) {
            return;
        }
        document.deleteString(removal.getStartOffset(), removal.getEndOffset());
        PsiDocumentManager.getInstance(project).commitDocument(document);
    }

    @Override
    public boolean startInWriteAction() {
        return true;
    }
}
