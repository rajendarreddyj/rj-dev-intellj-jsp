package com.rajendarreddyj.intellij.jsp.language;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewModelBase;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiStructureViewFactory;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Outline of a JSP page: directives, declarations, scriptlets and actions in document order. */
public final class JspStructureViewFactory implements PsiStructureViewFactory {
    private static final TokenSet OUTLINE = TokenSet.create(JspElementTypes.DIRECTIVE,
            JspElementTypes.SCRIPTLET_DECLARATION, JspElementTypes.SCRIPTLET, JspElementTypes.ACTION);

    @Override
    public @Nullable StructureViewBuilder getStructureViewBuilder(@NotNull PsiFile psiFile) {
        return new TreeBasedStructureViewBuilder() {
            @Override
            public @NotNull StructureViewModel createStructureViewModel(@Nullable Editor editor) {
                return new StructureViewModelBase(psiFile, editor, new Node(psiFile)) {
                    @Override
                    public Sorter @NotNull [] getSorters() {
                        return Sorter.EMPTY_ARRAY;
                    }
                };
            }
        };
    }

    private static final class Node extends PsiTreeElementBase<PsiElement> {
        Node(PsiElement element) {
            super(element);
        }

        @Override
        public @NotNull Collection<StructureViewTreeElement> getChildrenBase() {
            PsiElement element = getElement();
            List<StructureViewTreeElement> children = new ArrayList<>();
            if (element instanceof PsiFile file) {
                for (ASTNode node = file.getNode().getFirstChildNode(); node != null; node = node.getTreeNext()) {
                    IElementType type = node.getElementType();
                    if (OUTLINE.contains(type)) {
                        children.add(new Node(node.getPsi()));
                    }
                }
            }
            return children;
        }

        @Override
        public @Nullable String getPresentableText() {
            PsiElement element = getElement();
            if (element == null) {
                return null;
            }
            if (element instanceof PsiFile file) {
                return file.getName();
            }
            String text = element.getText().replaceAll("\\s+", " ").trim();
            return text.length() > 80 ? text.substring(0, 77) + "..." : text;
        }
    }
}
