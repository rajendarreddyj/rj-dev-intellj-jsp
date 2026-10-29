package com.rajendarreddyj.intellij.jsp.language;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;

public final class JspElementTypes {
    public static final IFileElementType FILE = new IFileElementType(JspLanguage.INSTANCE);
    public static final IElementType TEMPLATE_TEXT = type("TEMPLATE_TEXT");
    public static final IElementType DIRECTIVE = type("DIRECTIVE");
    public static final IElementType ACTION = type("ACTION");
    public static final IElementType COMMENT = type("COMMENT");
    public static final IElementType SCRIPTLET_DECLARATION = type("SCRIPTLET_DECLARATION");
    public static final IElementType SCRIPTLET_EXPRESSION = type("SCRIPTLET_EXPRESSION");
    public static final IElementType SCRIPTLET = type("SCRIPTLET");
    public static final IElementType EL_EXPRESSION = type("EL_EXPRESSION");
    public static final IElementType ERROR = type("ERROR");

    private JspElementTypes() {
    }

    private static IElementType type(String name) {
        return new IElementType(name, JspLanguage.INSTANCE);
    }
}
