package com.rajendarreddyj.intellij.jsp.language;

import com.intellij.lang.Language;

public final class JspLanguage extends Language {
    public static final JspLanguage INSTANCE = new JspLanguage();

    private JspLanguage() {
        super("JSP");
    }
}
