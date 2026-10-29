package com.rajendarreddyj.intellij.jsp.language;

import com.intellij.openapi.editor.XmlHighlighterColors;
import com.intellij.lexer.LayeredLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public final class JspSyntaxHighlighter extends SyntaxHighlighterBase {
    public static final TextAttributesKey JSP_DELIMITER =
            TextAttributesKey.createTextAttributesKey("JSP.DELIMITER", DefaultLanguageHighlighterColors.KEYWORD);
    public static final TextAttributesKey JSP_TAG_NAME =
            TextAttributesKey.createTextAttributesKey("JSP.TAG_NAME", XmlHighlighterColors.XML_TAG_NAME);
    public static final TextAttributesKey JSP_ATTRIBUTE_NAME =
            TextAttributesKey.createTextAttributesKey("JSP.ATTRIBUTE_NAME", XmlHighlighterColors.XML_ATTRIBUTE_NAME);
    public static final TextAttributesKey JSP_ATTRIBUTE_VALUE =
            TextAttributesKey.createTextAttributesKey("JSP.ATTRIBUTE_VALUE", XmlHighlighterColors.XML_ATTRIBUTE_VALUE);
    public static final TextAttributesKey JSP_EQUALS =
            TextAttributesKey.createTextAttributesKey("JSP.EQUALS", XmlHighlighterColors.XML_TAG_NAME);
    public static final TextAttributesKey JSP_COMMENT =
            TextAttributesKey.createTextAttributesKey("JSP.COMMENT", DefaultLanguageHighlighterColors.BLOCK_COMMENT);
    public static final TextAttributesKey JSP_EL =
            TextAttributesKey.createTextAttributesKey("JSP.EL_EXPRESSION", DefaultLanguageHighlighterColors.INSTANCE_FIELD);
    public static final TextAttributesKey JSP_BAD =
            TextAttributesKey.createTextAttributesKey("JSP.BAD_REGION", HighlighterColors.BAD_CHARACTER);

    private static final TextAttributesKey[] EMPTY_KEYS = new TextAttributesKey[0];

    private static final IElementType[] TAG_LAYER_TYPES = {
            JspTokenTypes.DIRECTIVE, JspTokenTypes.ACTION, JspTokenTypes.SCRIPTLET,
            JspTokenTypes.SCRIPTLET_DECLARATION, JspTokenTypes.SCRIPTLET_EXPRESSION, JspTokenTypes.EL_EXPRESSION};

    @Override
    public @NotNull Lexer getHighlightingLexer() {
        LayeredLexer lexer = new LayeredLexer(new JspLexer());
        for (IElementType type : TAG_LAYER_TYPES) {
            lexer.registerLayer(new JspTagLexer(), type);
        }
        return lexer;
    }

    @Override
    public @NotNull TextAttributesKey @NotNull [] getTokenHighlights(IElementType tokenType) {
        if (tokenType == JspHighlightTokens.DELIMITER) {
            return pack(JSP_DELIMITER);
        }
        if (tokenType == JspHighlightTokens.TAG_NAME) {
            return pack(JSP_TAG_NAME);
        }
        if (tokenType == JspHighlightTokens.ATTR_NAME) {
            return pack(JSP_ATTRIBUTE_NAME);
        }
        if (tokenType == JspHighlightTokens.ATTR_EQ) {
            return pack(JSP_EQUALS);
        }
        if (tokenType == JspHighlightTokens.ATTR_VALUE) {
            return pack(JSP_ATTRIBUTE_VALUE);
        }
        if (tokenType == JspHighlightTokens.EL_BODY) {
            return pack(JSP_EL);
        }
        if (tokenType == JspTokenTypes.COMMENT) {
            return pack(JSP_COMMENT);
        }
        if (tokenType == JspTokenTypes.BAD_REGION) {
            return pack(JSP_BAD);
        }
        // scriptlet bodies stay unstyled so the injected Java highlighting shows through
        return EMPTY_KEYS;
    }
}
