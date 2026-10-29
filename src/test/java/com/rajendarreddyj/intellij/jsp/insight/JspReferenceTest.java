package com.rajendarreddyj.intellij.jsp.insight;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.Arrays;

public class JspReferenceTest extends BasePlatformTestCase {
    public void testResolvesStaticIncludeWithinWebapp() {
        myFixture.addFileToProject("src/main/webapp/WEB-INF/views/fragment.jspf", "fragment");
        myFixture.configureByText("page.jsp", "<%@ include file=\"/WEB-INF/views/fragm<caret>ent.jspf\" %>");

        PsiReference reference = referenceAtCaret();
        assertNotNull(reference);
        PsiFile target = assertInstanceOf(reference.resolve(), PsiFile.class);
        assertEquals("fragment.jspf", target.getName());
    }

    public void testResolvesRelativeIncludeWithParentTraversalInsideWebapp() {
        myFixture.addFileToProject("src/main/webapp/WEB-INF/shared/header.jspf", "header");
        PsiFile page = myFixture.addFileToProject(
                "src/main/webapp/WEB-INF/views/page.jsp",
                "<%@ include file=\"../shared/hea<caret>der.jspf\" %>"
        );
        myFixture.configureFromExistingVirtualFile(page.getVirtualFile());

        PsiReference reference = referenceAtCaret();
        assertNotNull(reference);
        PsiFile target = assertInstanceOf(reference.resolve(), PsiFile.class);
        assertEquals("header.jspf", target.getName());
    }

    public void testRejectsRelativeIncludeEscapingWebappRoot() {
        myFixture.addFileToProject("src/main/escape.jspf", "escape");
        PsiFile page = myFixture.addFileToProject(
                "src/main/webapp/WEB-INF/views/page.jsp",
                "<%@ include file=\"../../../escape<caret>.jspf\" %>"
        );
        myFixture.configureFromExistingVirtualFile(page.getVirtualFile());

        PsiReference reference = referenceAtCaret();
        assertNotNull(reference);
        assertNull(reference.resolve());
    }

    public void testResolvesTagLibraryDeclarationToTld() {
        myFixture.addFileToProject("src/main/webapp/WEB-INF/tld/core.tld", """
                <?xml version="1.0" encoding="UTF-8"?>
                <taglib xmlns="https://jakarta.ee/xml/ns/jakartaee" version="3.0">
                  <uri>http://example.test/tags/core</uri>
                  <short-name>c</short-name>
                </taglib>
                """);
        myFixture.configureByText("taglib.jsp", "<%@ taglib uri=\"http://example.test/tags/co<caret>re\" prefix=\"c\" %>");

        PsiReference reference = referenceAtCaret();
        assertNotNull(reference);
        PsiFile target = assertInstanceOf(reference.resolve(), PsiFile.class);
        assertEquals("core.tld", target.getName());
    }

    public void testResolvesElUseBeanName() {
        myFixture.addFileToProject("test/beans/UserBean.java", """
                package test.beans;

                public class UserBean {
                    public String getDisplayName() { return "ok"; }
                }
                """);
        myFixture.configureByText("bean-name.jsp", """
                <jsp:useBean id="user" class="test.beans.UserBean"/>
                ${us<caret>er}
                """);

        PsiReference reference = referenceAtCaret();
        assertNotNull(reference);
        PsiElement target = reference.resolve();
        assertNotNull(target);
        assertEquals("bean-name.jsp", target.getContainingFile().getName());
    }

    public void testResolvesElBeanPropertyToGetter() {
        myFixture.addFileToProject("test/beans/UserBean.java", """
                package test.beans;

                public class UserBean {
                    public String getDisplayName() { return "ok"; }
                }
                """);
        myFixture.configureByText("bean-property.jsp", """
                <jsp:useBean id="user" class="test.beans.UserBean"/>
                ${user.displayN<caret>ame}
                """);

        PsiReference reference = referenceAtCaret();
        assertNotNull(reference);
        PsiMethod target = assertInstanceOf(reference.resolve(), PsiMethod.class);
        assertEquals("getDisplayName", target.getName());
        PsiClass containingClass = target.getContainingClass();
        assertNotNull(containingClass);
        assertEquals("test.beans.UserBean", containingClass.getQualifiedName());
    }

    public void testResolvesLeadingSpaceElBeanPropertyToGetter() {
        myFixture.addFileToProject("test/beans/UserBean.java", """
                package test.beans;

                public class UserBean {
                    public String getDisplayName() { return "ok"; }
                }
                """);
        myFixture.configureByText("bean-property-space.jsp", """
                <jsp:useBean id="user" class="test.beans.UserBean"/>
                ${ user.displayN<caret>ame}
                """);

        PsiReference reference = referenceAtCaret();
        assertNotNull(reference);
        PsiMethod target = assertInstanceOf(reference.resolve(), PsiMethod.class);
        assertEquals("getDisplayName", target.getName());
    }

    public void testRenamingFragmentUpdatesIncludeDirectives() {
        PsiFile fragment = myFixture.addFileToProject("inc/frag.jspf", "x");
        PsiFile page = myFixture.addFileToProject("page.jsp", "<%@ include file=\"/inc/frag.jspf\" %>");

        myFixture.renameElement(fragment, "renamed.jspf");

        assertEquals("<%@ include file=\"/inc/renamed.jspf\" %>", page.getVirtualFile() == null ? "" :
                com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(page.getVirtualFile()).getText());
    }

    public void testFindsIncludeDirectivesAsUsagesOfFragment() {
        PsiFile fragment = myFixture.addFileToProject("inc/frag.jspf", "x");
        myFixture.addFileToProject("a.jsp", "<%@ include file=\"/inc/frag.jspf\" %>");
        myFixture.addFileToProject("b.jsp", "<jsp:include page=\"/inc/frag.jspf\"/>");

        assertEquals(2, myFixture.findUsages(fragment).size());
    }

    public void testResolvesChainedElPropertiesAndTypedVariables() {
        myFixture.addFileToProject("test/beans/Address.java", """
                package test.beans;
                public class Address { public String getCity() { return ""; } }
                """);
        myFixture.addFileToProject("test/beans/Order.java", """
                package test.beans;
                public class Order { public int getTotal() { return 1; } }
                """);
        myFixture.addFileToProject("test/beans/UserBean.java", """
                package test.beans;
                public class UserBean {
                    public Address getAddress() { return null; }
                    public Order[] getOrders() { return null; }
                }
                """);
        myFixture.configureByText("chain.jsp", """
                <jsp:useBean id="user" class="test.beans.UserBean"/>
                <c:set var="home" value="${user.address}"/>
                <c:forEach var="order" items="${user.orders}">${order.tot<caret>al}</c:forEach>
                ${user.address.city} ${home.city}
                """);
        assertEquals("getTotal", assertInstanceOf(referenceAtCaret().resolve(), PsiMethod.class).getName());
        for (String expression : new String[] {"user.address.ci", "home.ci"}) {
            int offset = myFixture.getFile().getText().indexOf("${" + expression) + 2 + expression.length();
            myFixture.getEditor().getCaretModel().moveToOffset(offset);
            assertEquals("getCity", assertInstanceOf(referenceAtCaret().resolve(), PsiMethod.class).getName());
        }
    }

    public void testResolvesSetAttributeVariableToDeclaration() {
        myFixture.configureByText("attr.jsp", """
                <% request.setAttribute("cart", new Object()); %>
                ${ca<caret>rt}
                """);
        assertNotNull(referenceAtCaret().resolve());
    }

    public void testGoToDeclarationFindsIncludeThroughRealReferenceMechanism() {
        myFixture.addFileToProject("web/include/inc.jspf", "x");
        PsiFile page = myFixture.addFileToProject("web/admin/p.jsp", "<%@ include file=\"../include/i<caret>nc.jspf\" %>");
        myFixture.configureFromExistingVirtualFile(page.getVirtualFile());

        PsiReference reference = myFixture.getFile().findReferenceAt(myFixture.getCaretOffset());
        assertNotNull(reference);
        assertEquals("inc.jspf", assertInstanceOf(reference.resolve(), PsiFile.class).getName());
    }

    public void testPageImportResolvesToClass() {
        myFixture.addFileToProject("com/acme/sec/Sanitizer.java", "package com.acme.sec; public class Sanitizer {}");
        myFixture.configureByText("p.jsp", "<%@ page import=\"java.util.*, com.acme.sec.Sani<caret>tizer\" %>");

        PsiReference reference = myFixture.getFile().findReferenceAt(myFixture.getCaretOffset());
        assertNotNull(reference);
        assertEquals("com.acme.sec.Sanitizer", assertInstanceOf(reference.resolve(), PsiClass.class).getQualifiedName());
    }

    private PsiReference referenceAtCaret() {
        PsiElement element = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
        assertNotNull(element);
        PsiElement host = element;
        while (host != null && !(host instanceof com.rajendarreddyj.intellij.jsp.language.JspRegionPsiElement) && !(host instanceof PsiFile)) {
            host = host.getParent();
        }
        assertNotNull(host);
        int relativeOffset = myFixture.getCaretOffset() - host.getTextRange().getStartOffset();
        return Arrays.stream(JspReferenceContributor.referencesFor(host))
                .filter(reference -> reference.getRangeInElement().contains(relativeOffset))
                .findFirst()
                .orElse(null);
    }
}
