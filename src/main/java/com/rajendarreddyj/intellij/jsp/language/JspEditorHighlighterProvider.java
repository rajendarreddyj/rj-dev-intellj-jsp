package com.rajendarreddyj.intellij.jsp.language;

import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.util.LayerDescriptor;
import com.intellij.openapi.editor.ex.util.LayeredLexerEditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileTypes.EditorHighlighterProvider;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Highlights template text as HTML (with embedded CSS/JavaScript when those plugins are available). The layered
 * highlighter keeps the HTML lexer state across JSP regions, so a {@code <script>} or an attribute value
 * interrupted by a scriptlet or EL expression stays highlighted correctly.
 */
public final class JspEditorHighlighterProvider implements EditorHighlighterProvider {
    @Override
    public EditorHighlighter getEditorHighlighter(@Nullable Project project, @NotNull FileType fileType,
                                                  @Nullable VirtualFile virtualFile, @NotNull EditorColorsScheme colors) {
        LayeredLexerEditorHighlighter highlighter =
                new LayeredLexerEditorHighlighter(new JspSyntaxHighlighter(), colors);
        SyntaxHighlighter html = SyntaxHighlighterFactory.getSyntaxHighlighter(HtmlFileType.INSTANCE, project, virtualFile);
        if (html != null) {
            highlighter.registerLayer(JspTokenTypes.TEMPLATE_TEXT, new LayerDescriptor(html, ""));
        }
        return highlighter;
    }
}
