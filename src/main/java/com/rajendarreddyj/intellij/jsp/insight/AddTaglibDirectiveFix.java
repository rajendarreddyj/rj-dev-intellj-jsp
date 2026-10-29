package com.rajendarreddyj.intellij.jsp.insight;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/** Inserts {@code <%@ taglib prefix="..." uri="..." %>} at the top of the page. */
final class AddTaglibDirectiveFix implements IntentionAction {
    private final String prefix;
    private final String uri;

    AddTaglibDirectiveFix(@NotNull String prefix, @NotNull String uri) {
        this.prefix = prefix;
        this.uri = uri;
    }

    @Override
    public @NotNull String getText() {
        return "Add taglib directive for '" + prefix + "'";
    }

    @Override
    public @NotNull String getFamilyName() {
        return "Add taglib directive";
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
        return file.isValid();
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
        Document document = PsiDocumentManager.getInstance(project).getDocument(file);
        if (document == null) {
            return;
        }
        document.insertString(0, "<%@ taglib prefix=\"" + prefix + "\" uri=\"" + uri + "\" %>\n");
        PsiDocumentManager.getInstance(project).commitDocument(document);
    }

    @Override
    public boolean startInWriteAction() {
        return true;
    }
}
