package com.rajendarreddyj.intellij.jsp.insight;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;

public final class JspTagLibraryService {
    public @NotNull Set<String> getTags(@NotNull Project project, @NotNull String resolvedUriOrPrefix) {
        JspInsightUtil.TldInfo tldInfo = JspInsightUtil.findTldByIdentifier(project, resolvedUriOrPrefix);
        if (tldInfo == null) {
            return Set.of();
        }
        return new LinkedHashSet<>(tldInfo.tags());
    }

    public @Nullable PsiFile resolveTagLibrary(@NotNull Project project, @NotNull String uriOrPrefix) {
        JspInsightUtil.TldInfo info = JspInsightUtil.findTldByIdentifier(project, uriOrPrefix);
        if (info == null) {
            return null;
        }
        return com.intellij.psi.PsiManager.getInstance(project).findFile(info.file());
    }

    public @Nullable PsiFile resolveTagLibrary(@NotNull Project project, @NotNull PsiFile contextFile, @NotNull String uriOrPrefix) {
        if (uriOrPrefix.startsWith("/")) {
            JspInsightUtil.TldInfo pathMatch = JspInsightUtil.findTldByWebPath(project, contextFile, uriOrPrefix);
            if (pathMatch != null) {
                PsiFile file = com.intellij.psi.PsiManager.getInstance(project).findFile(pathMatch.file());
                if (file != null) {
                    return file;
                }
            }
        }
        return resolveTagLibrary(project, uriOrPrefix);
    }
}
