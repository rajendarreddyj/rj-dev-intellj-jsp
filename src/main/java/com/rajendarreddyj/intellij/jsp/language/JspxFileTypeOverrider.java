package com.rajendarreddyj.intellij.jsp.language;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.impl.FileTypeOverrider;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** The bundled XML file type claims {@code *.jspx} in IntelliJ Community; route JSP documents to this plugin. */
public final class JspxFileTypeOverrider implements FileTypeOverrider {
    @Override
    public @Nullable FileType getOverriddenFileType(@NotNull VirtualFile file) {
        return !file.isDirectory() && ("jspx".equalsIgnoreCase(file.getExtension()) || "tagx".equalsIgnoreCase(file.getExtension())) ? JspFileType.INSTANCE : null;
    }
}
