package com.rajendarreddyj.intellij.jsp.language;

import com.intellij.psi.tree.IElementType;

public final class JspTokenTypes {
    public static final IElementType TEMPLATE_TEXT = type("TEMPLATE_TEXT");
    public static final IElementType DIRECTIVE = type("DIRECTIVE");
    public static final IElementType ACTION = type("ACTION");
    public static final IElementType COMMENT = type("COMMENT");
    public static final IElementType SCRIPTLET_DECLARATION = type("SCRIPTLET_DECLARATION");
    public static final IElementType SCRIPTLET_EXPRESSION = type("SCRIPTLET_EXPRESSION");
    public static final IElementType SCRIPTLET = type("SCRIPTLET");
    public static final IElementType EL_EXPRESSION = type("EL_EXPRESSION");
    public static final IElementType BAD_REGION = type("BAD_REGION");

    private JspTokenTypes() {
    }

    private static IElementType type(String name) {
        return new IElementType(name, JspLanguage.INSTANCE);
    }
}
