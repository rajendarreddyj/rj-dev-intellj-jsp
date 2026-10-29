package com.rajendarreddyj.intellij.jsp.debugger;

import com.intellij.debugger.engine.evaluation.CodeFragmentFactory;
import com.intellij.debugger.engine.evaluation.DefaultCodeFragmentFactory;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilder;
import com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilderImpl;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaCodeFragment;
import com.intellij.psi.PsiElement;
import com.rajendarreddyj.intellij.jsp.insight.JspEvaluationContext;
import com.rajendarreddyj.intellij.jsp.language.JspLanguage;
import org.jetbrains.annotations.NotNull;

/**
 * Makes Evaluate Expression and Watches work while stopped in a JSP: the expression is compiled against the translated
 * servlet (implicit objects, page imports, scriptlet locals) and evaluated by the normal Java evaluator in the live frame.
 */
public final class JspCodeFragmentFactory extends CodeFragmentFactory {
    private final DefaultCodeFragmentFactory delegate = DefaultCodeFragmentFactory.getInstance();

    @Override
    public JavaCodeFragment createCodeFragment(@NotNull TextWithImports item, PsiElement context, @NotNull Project project) {
        return delegate.createCodeFragment(item, javaContext(context), project);
    }

    @Override
    public JavaCodeFragment createPresentationCodeFragment(@NotNull TextWithImports item, PsiElement context, @NotNull Project project) {
        return delegate.createPresentationCodeFragment(item, javaContext(context), project);
    }

    @Override
    public boolean isContextAccepted(PsiElement contextElement) {
        return contextElement != null && contextElement.isValid()
                && contextElement.getContainingFile() != null
                && contextElement.getContainingFile().getLanguage().isKindOf(JspLanguage.INSTANCE);
    }

    @Override
    public LanguageFileType getFileType() {
        return delegate.getFileType();
    }

    @Override
    public EvaluatorBuilder getEvaluatorBuilder() {
        return EvaluatorBuilderImpl.getInstance();
    }

    private static PsiElement javaContext(PsiElement context) {
        if (context == null || context.getContainingFile() == null) {
            return context;
        }
        PsiElement java = JspEvaluationContext.javaContextFor(context.getContainingFile());
        return java != null ? java : context;
    }
}
