package com.rajendarreddyj.intellij.jsp.insight;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Java context for debugger Evaluate/Watches on a JSP breakpoint: the end of the translated {@code _jspService}, where
 * implicit objects ({@code request}, {@code out}, ...), page imports and scriptlet locals are all in scope.
 */
public final class JspEvaluationContext {
    private JspEvaluationContext() {
    }

    public static @Nullable PsiElement javaContextFor(@NotNull PsiFile jspFile) {
        PsiFile javaFile = CachedValuesManager.getCachedValue(jspFile, () -> CachedValueProvider.Result.create(
                PsiFileFactory.getInstance(jspFile.getProject()).createFileFromText(
                        "_jsp_evaluation.java", JavaFileType.INSTANCE, JspJavaTranslation.forFile(jspFile).codeUnitText()),
                PsiModificationTracker.MODIFICATION_COUNT));
        for (PsiMethod method : PsiTreeUtil.findChildrenOfType(javaFile, PsiMethod.class)) {
            PsiCodeBlock body = "_jspService".equals(method.getName()) ? method.getBody() : null;
            if (body != null && body.getRBrace() != null) {
                return body.getRBrace();
            }
        }
        return null;
    }
}
