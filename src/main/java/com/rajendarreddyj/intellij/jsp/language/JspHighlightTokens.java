package com.rajendarreddyj.intellij.jsp.language;

import com.intellij.psi.tree.IElementType;

/** Token types produced only by {@link JspTagLexer}, used for highlighting inside JSP regions. */
public final class JspHighlightTokens {
    public static final IElementType DELIMITER = new IElementType("JSP_DELIMITER", JspLanguage.INSTANCE);
    public static final IElementType TAG_NAME = new IElementType("JSP_TAG_NAME", JspLanguage.INSTANCE);
    public static final IElementType ATTR_NAME = new IElementType("JSP_ATTR_NAME", JspLanguage.INSTANCE);
    public static final IElementType ATTR_EQ = new IElementType("JSP_ATTR_EQ", JspLanguage.INSTANCE);
    public static final IElementType ATTR_VALUE = new IElementType("JSP_ATTR_VALUE", JspLanguage.INSTANCE);
    public static final IElementType EL_BODY = new IElementType("JSP_EL_BODY", JspLanguage.INSTANCE);
    public static final IElementType BODY = new IElementType("JSP_BODY", JspLanguage.INSTANCE);

    private JspHighlightTokens() {
    }
}
