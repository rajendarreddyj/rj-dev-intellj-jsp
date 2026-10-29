package com.rajendarreddyj.intellij.jsp.debugger;

import com.intellij.debugger.ui.breakpoints.JavaLineBreakpointType;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.XSourcePosition;
import com.rajendarreddyj.intellij.jsp.language.JspFileType;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Line breakpoints in JSP/JSPF/JSPX files. They are resolved to the generated servlet by the JSR-45 position
 * manager ({@link TomcatJspPositionManager}), so the Java breakpoint machinery only needs to accept the file.
 */
public final class JspLineBreakpointType extends JavaLineBreakpointType {
    public JspLineBreakpointType() {
        super("jsp-line", "JSP Line Breakpoints");
    }

    @Override
    public boolean canPutAt(@NotNull VirtualFile file, int line, @NotNull Project project) {
        if (!(file.getFileType() instanceof JspFileType)) {
            return false;
        }
        Document document = FileDocumentManager.getInstance().getDocument(file);
        if (document == null || line < 0 || line >= document.getLineCount()) {
            return false;
        }
        return !document.getText(
                new com.intellij.openapi.util.TextRange(document.getLineStartOffset(line),
                        document.getLineEndOffset(line))).isBlank();
    }

    @Override
    public @NotNull List<JavaBreakpointVariant> computeVariants(@NotNull Project project,
                                                                @NotNull XSourcePosition position) {
        return List.of();
    }

    @Override
    public int getPriority() {
        return 100;
    }

    @Override
    public boolean canBeHitInOtherPlaces() {
        return false;
    }
}
