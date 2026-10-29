package com.rajendarreddyj.intellij.jsp.language;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class JspParser implements PsiParser {
    @Override
    public @NotNull ASTNode parse(@NotNull IElementType root, @NotNull PsiBuilder builder) {
        PsiBuilder.Marker rootMarker = builder.mark();
        while (!builder.eof()) {
            IElementType tokenType = builder.getTokenType();
            if (tokenType == null) {
                break;
            }

            PsiBuilder.Marker marker = builder.mark();
            builder.advanceLexer();
            marker.done(mapTokenToElement(tokenType));
        }
        rootMarker.done(root);
        return builder.getTreeBuilt();
    }

    public @NotNull List<Region> parseRegions(@NotNull CharSequence source) {
        Lexer lexer = new JspLexer();
        lexer.start(source);

        List<Region> regions = new ArrayList<>();
        while (lexer.getTokenType() != null) {
            IElementType tokenType = lexer.getTokenType();
            regions.add(new Region(
                    mapTokenToElement(tokenType),
                    source.subSequence(lexer.getTokenStart(), lexer.getTokenEnd()).toString()
            ));
            lexer.advance();
        }
        return regions;
    }

    private static IElementType mapTokenToElement(IElementType tokenType) {
        if (tokenType == JspTokenTypes.DIRECTIVE) {
            return JspElementTypes.DIRECTIVE;
        }
        if (tokenType == JspTokenTypes.ACTION) {
            return JspElementTypes.ACTION;
        }
        if (tokenType == JspTokenTypes.COMMENT) {
            return JspElementTypes.COMMENT;
        }
        if (tokenType == JspTokenTypes.SCRIPTLET_DECLARATION) {
            return JspElementTypes.SCRIPTLET_DECLARATION;
        }
        if (tokenType == JspTokenTypes.SCRIPTLET_EXPRESSION) {
            return JspElementTypes.SCRIPTLET_EXPRESSION;
        }
        if (tokenType == JspTokenTypes.SCRIPTLET) {
            return JspElementTypes.SCRIPTLET;
        }
        if (tokenType == JspTokenTypes.EL_EXPRESSION) {
            return JspElementTypes.EL_EXPRESSION;
        }
        if (tokenType == JspTokenTypes.BAD_REGION) {
            return JspElementTypes.ERROR;
        }
        return JspElementTypes.TEMPLATE_TEXT;
    }

    public record Region(IElementType type, String text) {
    }
}
