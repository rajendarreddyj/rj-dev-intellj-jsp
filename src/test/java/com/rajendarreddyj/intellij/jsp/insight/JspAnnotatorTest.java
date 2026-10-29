package com.rajendarreddyj.intellij.jsp.insight;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class JspAnnotatorTest extends BasePlatformTestCase {
    public void testReportsUndeclaredTagPrefix() {
        myFixture.configureByText("undeclared-prefix.jsp", """
                <%@ taglib uri="http://example.test/tags/core" prefix="c" %>
                <<warning descr="Undeclared JSP tag prefix 'x'">x</warning>:out value="${param.q}"/>
                """);
        myFixture.testHighlighting();
    }

    public void testIgnoresTagsInCommentsAndScriptlets() {
        myFixture.configureByText("comments.jsp", """
                <%-- <x:out value="a"/> --%>
                <% String s = "<y:out/>"; %>
                """);
        myFixture.testHighlighting();
    }

    public void testPrefixDeclaredInIncludedFragmentIsKnown() {
        myFixture.addFileToProject("WEB-INF/views/taglibs.jspf",
                "<%@ taglib prefix=\"c\" uri=\"http://example.test/tags/core\" %>");
        myFixture.configureByText("uses.jsp", """
                <%@ include file="/WEB-INF/views/taglibs.jspf" %>
                <c:out value="${param.q}"/>
                """);
        myFixture.testHighlighting();
    }

    public void testFragmentSeesPrefixDeclaredByIncludingPage() {
        myFixture.addFileToProject("page.jsp", """
                <%@ taglib prefix="c" uri="http://example.test/tags/core" %>
                <%@ include file="/WEB-INF/views/body.jspf" %>
                """);
        com.intellij.psi.PsiFile fragment = myFixture.addFileToProject("WEB-INF/views/body.jspf", "<c:out value=\"x\"/>\n");
        myFixture.configureFromExistingVirtualFile(fragment.getVirtualFile());
        myFixture.testHighlighting();
    }

    public void testTagdirAndXmlnsDeclarePrefixes() {
        myFixture.configureByText("decl.jsp", """
                <%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
                <html xmlns:p="http://example.test/p"><t:box/><p:item/></html>
                """);
        myFixture.testHighlighting();
    }

    public void testReportsUnresolvedPageImportsAndRemovesThem() {
        myFixture.addFileToProject("test/Known.java", "package test; public class Known {}");
        myFixture.configureByText("imports.jsp", """
                <%@ page import="test.Known,<warning descr="Cannot resolve class 'no.such.Thing'">no.such.Thing<caret></warning>,<warning descr="Cannot resolve package 'no.such.pkg'">no.such.pkg.*</warning>" %>
                """);
        myFixture.testHighlighting();
        myFixture.launchAction(myFixture.findSingleIntention("Remove unresolved import 'no.such.Thing'"));
        assertEquals("<%@ page import=\"test.Known,no.such.pkg.*\" %>\n", myFixture.getFile().getText());
    }

    public void testValidatesTagAttributesAgainstTld() {
        myFixture.addFileToProject("WEB-INF/tlds/x.tld", """
                <taglib><short-name>x</short-name><uri>http://example.test/x</uri>
                  <tag><name>box</name>
                    <attribute><name>title</name><required>true</required></attribute>
                    <attribute><name>size</name></attribute>
                  </tag></taglib>
                """);
        myFixture.configureByText("attrs.jsp", """
                <%@ taglib prefix="x" uri="http://example.test/x" %>
                <x:box title="a" size="1"/>
                <x:box title="a" <warning descr="Unknown attribute 'bogus' for tag 'x:box'">bogus</warning>="1"/>
                <<error descr="Missing required attribute 'title'">x:box</error> size="1"/>
                """);
        myFixture.testHighlighting();
    }
}