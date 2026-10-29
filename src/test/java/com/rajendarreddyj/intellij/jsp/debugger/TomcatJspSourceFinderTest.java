package com.rajendarreddyj.intellij.jsp.debugger;

import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class TomcatJspSourceFinderTest extends BasePlatformTestCase {
    public void testResolvesJspSmapPathToProjectPsiFile() {
        PsiFile expected = myFixture.addFileToProject("src/main/webapp/WEB-INF/views/home.jsp", "home");
        PsiFile resolved = new TomcatJspSourceFinder().findSourceFile("/WEB-INF/views/home.jsp", getProject(), getProject());

        assertNotNull(resolved);
        assertEquals(expected.getVirtualFile().getPath(), resolved.getVirtualFile().getPath());
    }

    public void testResolvesJspSmapPathToConfiguredExternalPsiFile() throws IOException {
        Path externalRoot = createExternalRoot("configured-external");
        Path externalJsp = Files.createDirectories(externalRoot.resolve("pages")).resolve("external.jsp");
        Files.writeString(externalJsp, "external");

        JspSourceRootSettings.getInstance(getProject())
                .setMappings(List.of(new JspSourceRootMapping("external", externalRoot.toString())));

        PsiFile resolved = new TomcatJspSourceFinder().findSourceFile("/external/pages/external.jsp", getProject(), getProject());
        assertNotNull(resolved);
        assertEquals(externalJsp.toRealPath().toString(), Path.of(resolved.getVirtualFile().getPath()).toRealPath().toString());
    }

    public void testReturnsNoFileForAmbiguousSmapPath() throws IOException {
        Path externalRootOne = createExternalRoot("ambiguous-one");
        Path externalRootTwo = createExternalRoot("ambiguous-two");
        Files.writeString(Files.createDirectories(externalRootOne.resolve("shared")).resolve("item.jsp"), "one");
        Files.writeString(Files.createDirectories(externalRootTwo.resolve("shared")).resolve("item.jsp"), "two");
        JspSourceRootSettings.getInstance(getProject())
                .setMappings(List.of(
                        new JspSourceRootMapping("mapped", externalRootOne.toString()),
                        new JspSourceRootMapping("mapped", externalRootTwo.toString())
                ));

        PsiFile resolved = new TomcatJspSourceFinder().findSourceFile("/mapped/shared/item.jsp", getProject(), getProject());
        assertNull(resolved);
    }

    public void testResolvesNonConventionalWebappRootBySuffixIgnoringBuildOutput() {
        PsiFile expected = myFixture.addFileToProject("RolloutManager/JSP/App/admin/Edit.jsp", "a");
        myFixture.addFileToProject("RolloutManager/JSP/en/admin/Edit.jsp", "b");
        myFixture.addFileToProject("RolloutManager/target/war/App/admin/Edit.jsp", "c");

        PsiFile resolved = new TomcatJspSourceFinder().findSourceFile("App/admin/Edit.jsp", getProject(), getProject());
        assertNotNull(resolved);
        assertEquals(expected.getVirtualFile().getPath(), resolved.getVirtualFile().getPath());
    }

    public void testReturnsNoFileWhenJspSourceIsUnavailable() {
        PsiFile resolved = new TomcatJspSourceFinder().findSourceFile("/WEB-INF/views/missing.jsp", getProject(), getProject());
        assertNull(resolved);
    }

    private static Path createExternalRoot(String testName) throws IOException {
        Path root = Path.of("build", "tomcat-jsp-source-finder-tests", testName + "-" + System.nanoTime());
        Files.createDirectories(root);
        return root.toAbsolutePath().normalize();
    }
}
