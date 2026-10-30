package com.rajendarreddyj.intellij.jsp.insight;

import com.intellij.debugger.engine.evaluation.CodeFragmentKind;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class JspJavaInjectionTest extends LightJavaCodeInsightFixtureTestCase {
    private static final LightProjectDescriptor REAL_JDK = new DefaultLightProjectDescriptor(
            () -> JavaSdk.getInstance().createJdk("test-jdk", System.getProperty("java.home"), false));

    @Override
    protected @NotNull LightProjectDescriptor getProjectDescriptor() {
        return REAL_JDK;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.addFileToProject("jakarta/servlet/http/HttpServletRequest.java", """
                package jakarta.servlet.http;
                public interface HttpServletRequest { String getParameter(String name); }
                """);
        myFixture.addFileToProject("jakarta/servlet/http/HttpServletResponse.java",
                "package jakarta.servlet.http; public interface HttpServletResponse {}");
        myFixture.addFileToProject("jakarta/servlet/http/HttpServlet.java",
                "package jakarta.servlet.http; public abstract class HttpServlet {}");
        myFixture.addFileToProject("jakarta/servlet/http/HttpSession.java",
                "package jakarta.servlet.http; public interface HttpSession {}");
        myFixture.addFileToProject("jakarta/servlet/ServletContext.java",
                "package jakarta.servlet; public interface ServletContext {}");
        myFixture.addFileToProject("jakarta/servlet/ServletConfig.java",
                "package jakarta.servlet; public interface ServletConfig {}");
        myFixture.addFileToProject("jakarta/servlet/jsp/PageContext.java",
                "package jakarta.servlet.jsp; public abstract class PageContext {}");
        myFixture.addFileToProject("jakarta/servlet/jsp/JspWriter.java",
                "package jakarta.servlet.jsp; public abstract class JspWriter { public void print(Object o) {} }");
    }

    public void testScriptletsSharePageImportsLocalsAndImplicitObjects() {
        PsiFile file = myFixture.configureByText("page.jsp", """
                <%@ page import="java.util.List, java.util.ArrayList" %>
                <% List<String> items = new ArrayList<>(); String q = request.getParameter("q"); %>
                <ul><% for (String item : items) { %><li><%= item %></li><% } %></ul>
                <%! private int counter; int next() { return ++counter; } %>
                <% out.print(q + next()); %>
                """);
        assertResolved(file, "List", "ArrayList", "items", "request", "getParameter", "item", "out", "print", "q", "next");
    }

    public void testStaticIncludeContributesImportsDeclarationsAndLocals() {
        myFixture.addFileToProject("include/header.jspf", """
                <%@ page import="java.util.Map, java.util.HashMap" %>
                <%! static final String APP = "x"; %>
                <% Map<String, String> lxPage = new HashMap<>(); %>
                <%@ include file="nested.jspf" %>
                """);
        myFixture.addFileToProject("include/nested.jspf", "<% StringBuilder sClassLog = new StringBuilder(); %>");
        PsiFile page = myFixture.addFileToProject("admin/edit.jsp", """
                <%@ include file="../include/header.jspf" %>
                <% Map<String, String> copy = lxPage; copy.put("title", APP); sClassLog.append(copy.size()); %>
                """);
        assertResolved(page, "Map", "lxPage", "copy", "put", "APP", "sClassLog", "append", "size");
    }

    public void testFragmentUsesContextFromIncludingPage() {
        myFixture.addFileToProject("admin/edit.jsp", """
                <%@ page import="java.util.Map" %>
                <% Map<String, Integer> lxPage = null; %>
                <%@ include file="../include/body.jspf" %>
                <% int after = 1; %>
                """);
        PsiFile fragment = myFixture.addFileToProject("include/body.jspf", "<% lxPage.size(); after++; %>");
        assertResolved(fragment, "lxPage", "size");
        assertUnresolved(fragment, "after");
    }

    public void testFragmentPicksNearestIncludingPage() {
        myFixture.addFileToProject("a/page.jsp", "<% int lxFar = 1; %><%@ include file=\"/b/frag.jspf\" %>");
        myFixture.addFileToProject("b/page.jsp", "<% int lxNear = 1; %><%@ include file=\"/b/frag.jspf\" %>");
        PsiFile fragment = myFixture.addFileToProject("b/frag.jspf", "<% lxNear++; lxFar++; %>");
        assertResolved(fragment, "lxNear");
        assertUnresolved(fragment, "lxFar");
    }

    public void testEvaluationContextSeesImplicitObjectsAndScriptletLocals() {
        PsiFile file = myFixture.configureByText("eval.jsp", "<% String local = request.getParameter(\"q\"); %>");
        PsiElement context = JspEvaluationContext.javaContextFor(file);
        assertNotNull(context);
        com.intellij.psi.PsiExpressionCodeFragment fragment = com.intellij.psi.JavaCodeFragmentFactory.getInstance(getProject())
                .createExpressionCodeFragment("local.length() + request.getParameter(\"a\").length()", context, null, true);
        for (com.intellij.psi.PsiReferenceExpression reference : PsiTreeUtil.findChildrenOfType(fragment, com.intellij.psi.PsiReferenceExpression.class)) {
            assertNotNull(reference.getText(), reference.resolve());
        }
        assertTrue(new com.rajendarreddyj.intellij.jsp.debugger.JspCodeFragmentFactory().isContextAccepted(file.getFirstChild()));
    }

    public void testDebuggerPsiFragmentsUseTranslatedJavaContext() {
        PsiFile file = myFixture.configureByText("fragment.jsp", "<% String local = request.getParameter(\"q\"); %>");
        PsiElement expectedContext = JspEvaluationContext.javaContextFor(file);
        PsiElement jspContext = file.getFirstChild();
        var factory = new com.rajendarreddyj.intellij.jsp.debugger.JspCodeFragmentFactory();
        var input = new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, "local.length()");

        PsiCodeFragment expression = factory.createPsiCodeFragment(input, jspContext, getProject());
        PsiCodeFragment presentation = factory.createPresentationPsiCodeFragment(input, jspContext, getProject());

        assertNotNull(expression);
        assertNotNull(presentation);
        assertSame(expectedContext, expression.getContext());
        assertSame(expectedContext, presentation.getContext());
    }

    public void testUndefinedSymbolsStayUnresolved() {
        PsiFile file = myFixture.configureByText("bad.jsp", "<% int ok = 1; %><% missingVariable.toString(); ok++; %>");
        assertResolved(file, "ok");
        assertUnresolved(file, "missingVariable");
    }

    public void testAllScriptletsShareOneInjectedFile() {
        String text = "<% int a = 1; %>text<% a++; %>more<% a--; %>";
        PsiFile file = myFixture.configureByText("one.jsp", text);
        InjectedLanguageManager manager = InjectedLanguageManager.getInstance(getProject());
        // query the last host first: injection may be triggered from any place, yet only one file must exist
        var lastInjected = manager.getInjectedPsiFiles(host(file, text.lastIndexOf("<%")));
        var firstInjected = manager.getInjectedPsiFiles(host(file, text.indexOf("<%")));
        var middleInjected = manager.getInjectedPsiFiles(host(file, text.indexOf("<%", text.indexOf("text"))));
        assertNotNull(firstInjected);
        assertNotNull(middleInjected);
        assertNotNull(lastInjected);
        assertEquals(1, firstInjected.size());
        assertEquals(1, middleInjected.size());
        assertEquals(1, lastInjected.size());
        assertSame(lastInjected.get(0).first, firstInjected.get(0).first);
        assertSame(lastInjected.get(0).first, middleInjected.get(0).first);
    }

    private void assertResolved(PsiFile file, String... names) {
        for (String name : names) {
            List<PsiJavaCodeReferenceElement> references = referencesInHosts(file, name);
            assertFalse("no reference to " + name, references.isEmpty());
            for (PsiJavaCodeReferenceElement reference : references) {
                assertNotNull("unresolved " + name, reference.resolve());
            }
        }
    }

    private void assertUnresolved(PsiFile file, String name) {
        List<PsiJavaCodeReferenceElement> references = referencesInHosts(file, name);
        assertFalse("no reference to " + name, references.isEmpty());
        for (PsiJavaCodeReferenceElement reference : references) {
            assertNull("unexpectedly resolved " + name, reference.resolve());
        }
    }

    /** References typed by the user (inside host ranges), excluding the synthetic prefix/suffix. */
    private List<PsiJavaCodeReferenceElement> referencesInHosts(PsiFile file, String name) {
        InjectedLanguageManager manager = InjectedLanguageManager.getInstance(getProject());
        List<PsiJavaCodeReferenceElement> result = new ArrayList<>();
        Set<PsiElement> seen = new HashSet<>();
        for (PsiLanguageInjectionHost host : PsiTreeUtil.findChildrenOfType(file, PsiLanguageInjectionHost.class)) {
            var injected = manager.getInjectedPsiFiles(host);
            if (injected == null) {
                continue;
            }
            for (var pair : injected) {
                if (!seen.add(pair.first)) {
                    continue;
                }
                for (PsiJavaCodeReferenceElement reference : PsiTreeUtil.findChildrenOfType(pair.first, PsiJavaCodeReferenceElement.class)) {
                    if (!name.equals(reference.getReferenceName())) {
                        continue;
                    }
                    TextRange hostRange = manager.injectedToHost(reference, reference.getTextRange());
                    if (hostRange.getEndOffset() <= file.getTextLength()
                            && hostRange.substring(file.getText()).equals(reference.getText())) {
                        result.add(reference);
                    }
                }
            }
        }
        return result;
    }

    private static PsiLanguageInjectionHost host(PsiFile file, int offset) {
        return PsiTreeUtil.getParentOfType(file.findElementAt(offset + 1), PsiLanguageInjectionHost.class, false);
    }
}
