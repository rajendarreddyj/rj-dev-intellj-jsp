package com.rajendarreddyj.intellij.jsp.insight;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class JspCompletionTest extends BasePlatformTestCase {
    public void testCompletesDirectiveNames() {
        myFixture.configureByText("directive.jsp", "<%@ <caret>%>");
        assertEquals(List.of("attribute", "include", "page", "tag", "taglib", "variable"), lookupStrings(myFixture.completeBasic()));
    }

    public void testCompletesStandardActions() {
        myFixture.configureByText("actions.jsp", "<jsp:<caret>/>");
        assertEquals(
                List.of("attribute", "body", "element", "fallback", "forward", "getProperty", "include", "param", "params", "plugin", "setProperty", "text", "useBean"),
                lookupStrings(myFixture.completeBasic())
        );
    }

    public void testCompletesTagsFromDeclaredTagLibrary() {
        myFixture.addFileToProject("WEB-INF/tld/core.tld", """
                <?xml version="1.0" encoding="UTF-8"?>
                <taglib xmlns="https://jakarta.ee/xml/ns/jakartaee" version="3.0">
                  <uri>http://example.test/tags/core</uri>
                  <short-name>c</short-name>
                  <tag><name>if</name></tag>
                  <tag><name>out</name></tag>
                </taglib>
                """);
        String sourceWithCaret = """
                <%@ taglib uri="http://example.test/tags/core" prefix="c" %>
                <c:<caret> />
                """;
        int offset = sourceWithCaret.indexOf("<caret>");
        String source = sourceWithCaret.replace("<caret>", "");
        List<String> actual = JspCompletionContributor.collectCompletionItems(getProject(), source, offset);
        assertEquals("prefixTags=" + new JspTagLibraryService().getTags(getProject(), "c") + ", uriTags=" + new JspTagLibraryService().getTags(getProject(), "http://example.test/tags/core"), List.of("if", "out"), actual);
    }

    public void testInjectsJavaIntoClassicAndJspxScriptlets() {
        assertJavaInjected("classic.jsp", "<% String value = \"abc\"; value.length(); %>", "<%");
        assertJavaInjected("page.jspx",
                "<jsp:root><jsp:scriptlet>String value = \"abc\"; value.length();</jsp:scriptlet></jsp:root>",
                "<jsp:scriptlet>");
        assertJavaInjected("expr.jspx", "<jsp:root><jsp:expression>1 + 2</jsp:expression></jsp:root>", "<jsp:expression>");
    }

    private void assertJavaInjected(String fileName, String text, String hostStart) {
        com.intellij.psi.PsiFile file = myFixture.configureByText(fileName, text);
        com.intellij.psi.PsiElement leaf = file.findElementAt(text.indexOf(hostStart) + 1);
        com.intellij.psi.PsiLanguageInjectionHost host = com.intellij.psi.util.PsiTreeUtil.getParentOfType(
                leaf, com.intellij.psi.PsiLanguageInjectionHost.class, false);
        assertNotNull(fileName, host);
        var injected = com.intellij.lang.injection.InjectedLanguageManager.getInstance(getProject()).getInjectedPsiFiles(host);
        assertNotNull(fileName, injected);
        assertEquals(fileName, com.intellij.lang.java.JavaLanguage.INSTANCE, injected.get(0).first.getLanguage());
    }

    public void testCompletesElImplicitObjects() {
        myFixture.configureByText("implicit.jsp", "${<caret>}");
        assertEquals(
                List.of("applicationScope", "cookie", "header", "headerValues", "initParam", "pageContext", "pageScope", "param", "paramValues", "requestScope", "sessionScope"),
                lookupStrings(myFixture.completeBasic())
        );
    }

    public void testCompletesJavaMemberInScriptlet() {
        String sourceWithCaret = "<% String value = \"abc\"; value.<caret> %>";
        int offset = sourceWithCaret.indexOf("<caret>");
        String source = sourceWithCaret.replace("<caret>", "");
        assertTrue(JspCompletionContributor.collectCompletionItems(getProject(), source, offset).contains("length"));
    }

    public void testCompletesUseBeanPropertyInEl() {
        myFixture.addFileToProject("test/beans/UserBean.java", """
                package test.beans;

                public class UserBean {
                    public String getDisplayName() { return "ok"; }
                }
                """);
        myFixture.configureByText("bean-el.jsp", """
                <jsp:useBean id="user" class="test.beans.UserBean"/>
                ${user.<caret>}
                """);
        assertTrue(lookupStrings(myFixture.completeBasic()).contains("displayName"));
    }

    public void testReturnsNoTagsForUnresolvedTaglibIdentifier() {
        myFixture.addFileToProject("src/main/webapp/WEB-INF/tld/core.tld", """
                <?xml version="1.0" encoding="UTF-8"?>
                <taglib xmlns="https://jakarta.ee/xml/ns/jakartaee" version="3.0">
                  <uri>http://example.test/tags/core</uri>
                  <short-name>c</short-name>
                  <tag><name>if</name></tag>
                </taglib>
                """);
        assertEquals(Set.of(), new JspTagLibraryService().getTags(getProject(), "unknown-prefix"));
        assertEquals(Set.of(), new JspTagLibraryService().getTags(getProject(), "http://example.test/tags/missing"));
    }

    public void testInvalidatesTldCacheAfterVfsChange() {
        myFixture.addFileToProject("src/main/webapp/WEB-INF/tld/core.tld", """
                <?xml version="1.0" encoding="UTF-8"?>
                <taglib xmlns="https://jakarta.ee/xml/ns/jakartaee" version="3.0">
                  <uri>http://example.test/tags/core</uri>
                  <short-name>c</short-name>
                  <tag><name>if</name></tag>
                </taglib>
                """);

        JspTagLibraryService service = new JspTagLibraryService();
        assertEquals(Set.of("if"), service.getTags(getProject(), "http://example.test/tags/core"));
        assertEquals(Set.of(), service.getTags(getProject(), "http://example.test/tags/extra"));

        myFixture.addFileToProject("src/main/webapp/WEB-INF/tld/extra.tld", """
                <?xml version="1.0" encoding="UTF-8"?>
                <taglib xmlns="https://jakarta.ee/xml/ns/jakartaee" version="3.0">
                  <uri>http://example.test/tags/extra</uri>
                  <short-name>x</short-name>
                  <tag><name>out</name></tag>
                </taglib>
                """);

        assertEquals(Set.of("out"), service.getTags(getProject(), "http://example.test/tags/extra"));
        assertEquals(Set.of("out"), service.getTags(getProject(), "x"));
    }

    private static List<String> lookupStrings(LookupElement[] elements) {
        if (elements == null) {
            return List.of();
        }
        return Arrays.stream(elements).map(LookupElement::getLookupString).distinct().sorted().toList();
    }
}
