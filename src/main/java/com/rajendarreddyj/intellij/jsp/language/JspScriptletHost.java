package com.rajendarreddyj.intellij.jsp.language;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.LiteralTextEscaper;
import com.intellij.psi.PsiLanguageInjectionHost;
import org.jetbrains.annotations.NotNull;

public final class JspScriptletHost extends JspRegionPsiElement implements PsiLanguageInjectionHost {
    public JspScriptletHost(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    public boolean isValidHost() {
        String text = getText();
        if (text.startsWith("<jsp:")) {
            return text.indexOf('>') < text.lastIndexOf("</") && text.endsWith(">");
        }
        return text.endsWith("%>") && text.startsWith("<%");
    }

    @Override
    public @NotNull PsiLanguageInjectionHost updateText(@NotNull String text) {
        return this;
    }

    @Override
    public @NotNull LiteralTextEscaper<? extends PsiLanguageInjectionHost> createLiteralTextEscaper() {
        return new LiteralTextEscaper<>(this) {
            @Override
            public boolean decode(@NotNull TextRange rangeInsideHost, @NotNull StringBuilder outChars) {
                outChars.append(rangeInsideHost.substring(myHost.getText()));
                return true;
            }

            @Override
            public int getOffsetInHost(int offsetInDecoded, @NotNull TextRange rangeInsideHost) {
                int offset = rangeInsideHost.getStartOffset() + offsetInDecoded;
                return Math.min(offset, rangeInsideHost.getEndOffset());
            }

            @Override
            public boolean isOneLine() {
                return false;
            }
        };
    }
}
