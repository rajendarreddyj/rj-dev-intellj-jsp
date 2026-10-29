package com.rajendarreddyj.intellij.jsp.language;

import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class JspFileType extends LanguageFileType {
    public static final JspFileType INSTANCE = new JspFileType();

    private JspFileType() {
        super(JspLanguage.INSTANCE);
    }

    @Override
    public @NonNls @NotNull String getName() {
        return "JSP/JSPX";
    }

    @Override
    public @NotNull String getDescription() {
        return "JSP, JSPF, JSPX and tag files";
    }

    @Override
    public @NotNull String getDefaultExtension() {
        return "jsp";
    }

    @Override
    public @Nullable Icon getIcon() {
        return null;
    }
}
