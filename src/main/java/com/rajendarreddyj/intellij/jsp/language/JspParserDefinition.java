package com.rajendarreddyj.intellij.jsp.language;

import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

public final class JspParserDefinition implements ParserDefinition {
    private static final TokenSet WHITESPACE = TokenSet.create(TokenType.WHITE_SPACE);
    private static final TokenSet COMMENTS = TokenSet.create(JspTokenTypes.COMMENT);

    @Override
    public @NotNull Lexer createLexer(Project project) {
        return new JspLexer();
    }

    @Override
    public @NotNull PsiParser createParser(Project project) {
        return new JspParser();
    }

    @Override
    public @NotNull IFileElementType getFileNodeType() {
        return JspElementTypes.FILE;
    }

    @Override
    public @NotNull TokenSet getWhitespaceTokens() {
        return WHITESPACE;
    }

    @Override
    public @NotNull TokenSet getCommentTokens() {
        return COMMENTS;
    }

    @Override
    public @NotNull TokenSet getStringLiteralElements() {
        return TokenSet.EMPTY;
    }

    @Override
    public @NotNull PsiElement createElement(ASTNode node) {
        if (node.getElementType() == JspElementTypes.SCRIPTLET
                || node.getElementType() == JspElementTypes.SCRIPTLET_DECLARATION
                || node.getElementType() == JspElementTypes.SCRIPTLET_EXPRESSION) {
            return new JspScriptletHost(node);
        }
        return new JspRegionPsiElement(node);
    }

    @Override
    public @NotNull PsiFile createFile(@NotNull FileViewProvider viewProvider) {
        return new JspPsiFile(viewProvider);
    }

    @Override
    public @NotNull SpaceRequirements spaceExistenceTypeBetweenTokens(ASTNode left, ASTNode right) {
        return SpaceRequirements.MAY;
    }
}
