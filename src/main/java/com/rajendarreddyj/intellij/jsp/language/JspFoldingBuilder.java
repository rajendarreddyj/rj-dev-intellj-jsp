package com.rajendarreddyj.intellij.jsp.language;

import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilderEx;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Folds multi-line scriptlets, declarations and JSP comments. */
public final class JspFoldingBuilder extends FoldingBuilderEx {
    @Override
    public FoldingDescriptor @NotNull [] buildFoldRegions(@NotNull PsiElement root, @NotNull Document document, boolean quick) {
        List<FoldingDescriptor> descriptors = new ArrayList<>();
        for (ASTNode node = root.getNode().getFirstChildNode(); node != null; node = node.getTreeNext()) {
            String placeholder = placeholder(node.getElementType());
            TextRange range = node.getTextRange();
            if (placeholder != null && range.getLength() > placeholder.length()
                    && document.getLineNumber(range.getStartOffset()) != document.getLineNumber(range.getEndOffset())) {
                descriptors.add(new FoldingDescriptor(node, range));
            }
        }
        return descriptors.toArray(FoldingDescriptor.EMPTY_ARRAY);
    }

    @Override
    public @Nullable String getPlaceholderText(@NotNull ASTNode node) {
        String placeholder = placeholder(node.getElementType());
        return placeholder == null ? "..." : placeholder;
    }

    @Override
    public boolean isCollapsedByDefault(@NotNull ASTNode node) {
        return false;
    }

    private static @Nullable String placeholder(IElementType type) {
        if (type == JspElementTypes.SCRIPTLET) {
            return "<% ... %>";
        }
        if (type == JspElementTypes.SCRIPTLET_DECLARATION) {
            return "<%! ... %>";
        }
        if (type == JspElementTypes.COMMENT) {
            return "<%-- ... --%>";
        }
        return null;
    }
}
