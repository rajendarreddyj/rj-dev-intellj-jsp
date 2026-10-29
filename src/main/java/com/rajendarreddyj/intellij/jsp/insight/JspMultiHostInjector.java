package com.rajendarreddyj.intellij.jsp.insight;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.rajendarreddyj.intellij.jsp.language.JspLanguage;
import com.rajendarreddyj.intellij.jsp.language.JspScriptletHost;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Injects one Java file per page: all declarations form one class body; all scriptlets and expressions form one
 * {@code _jspService} body (see {@link JspJavaTranslation}), so locals, imports, and included code are shared.
 */
public final class JspMultiHostInjector implements MultiHostInjector {
    @Override
    public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {
        if (!(context instanceof JspScriptletHost host) || !host.isValidHost()) {
            return;
        }
        PsiFile file = host.getContainingFile();
        if (file == null || !file.getLanguage().isKindOf(JspLanguage.INSTANCE)) {
            return;
        }
        JspJavaTranslation.Unit unit = JspJavaTranslation.forFile(file).unitFor(host);
        List<JspJavaTranslation.Place> places = unit.items().stream()
                .filter(JspJavaTranslation.Place.class::isInstance)
                .map(JspJavaTranslation.Place.class::cast)
                .toList();
        if (places.stream().noneMatch(place -> place.host() == host)) {
            return;
        }

        registrar.startInjecting(JavaLanguage.INSTANCE);
        JspJavaTranslation.Place last = places.get(places.size() - 1);
        StringBuilder pending = new StringBuilder(unit.header());
        boolean afterLast = false;
        StringBuilder suffix = new StringBuilder();
        for (JspJavaTranslation.Item item : unit.items()) {
            if (item instanceof JspJavaTranslation.Text text) {
                (afterLast ? suffix : pending).append('\n').append(text.text()).append('\n');
                continue;
            }
            JspJavaTranslation.Place place = (JspJavaTranslation.Place) item;
            pending.append('\n');
            if (place.expression()) {
                pending.append("out.print(");
            }
            String trailing = place.expression() ? ");\n" : "\n";
            if (place == last) {
                afterLast = true;
                suffix.append(trailing);
                continue;
            }
            registrar.addPlace(pending.toString(), null, place.host(), place.range());
            pending = new StringBuilder(trailing);
        }
        suffix.append(unit.footer());
        registrar.addPlace(pending.toString(), suffix.toString(), last.host(), last.range());
        registrar.doneInjecting();
    }

    @Override
    public @NotNull List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
        return List.of(JspScriptletHost.class);
    }
}
