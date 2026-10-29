package com.rajendarreddyj.intellij.jsp.language;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import org.jetbrains.annotations.NotNull;

public final class JspPsiFile extends PsiFileBase {
    public JspPsiFile(@NotNull FileViewProvider viewProvider) {
        super(viewProvider, JspLanguage.INSTANCE);
    }

    @Override
    public @NotNull FileType getFileType() {
        return JspFileType.INSTANCE;
    }

    @Override
    public @NotNull String toString() {
        return "JSP PSI file";
    }
}
