package com.rajendarreddyj.intellij.jsp.language;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;


public class JspParserTest extends BasePlatformTestCase {
    public void testParsesMixedJspRegions() throws Exception {
        Project project = getProject();
        String source = readFixture("mixed-regions.jsp");
        ParsedTree parsedTree = parseViaPsi(project, "mixed-regions.jsp", source);
        List<AstRegion> regions = parsedTree.regions();

        assertTrue("Expected JspPsiFile from parser definition", parsedTree.psiFile() instanceof JspPsiFile);
        assertEquals(source, parsedTree.psiFile().getText());
        assertEquals(source, joinRegionText(regions));
        assertContainsText(regions, "DIRECTIVE", "<%@ taglib uri=\"http://java.sun.com/jsp/jstl/core\" prefix=\"c\" %>");
        assertContainsText(regions, "DIRECTIVE", "<jsp:directive.taglib uri=\"jakarta.tags.core\" prefix=\"jt\"/>");
        assertContainsText(regions, "ACTION", "<jsp:include page=\"/fragment.jsp\"/>");
        assertContainsText(regions, "EL_EXPRESSION", "${user.name}");
        assertContainsText(regions, "SCRIPTLET", "<% int count = 1; %>");
        assertHasTemplateText(regions);
    }

    public void testParsesJspxRegions() throws Exception {
        Project project = getProject();
        String source = readFixture("jspx-regions.jspx");
        ParsedTree parsedTree = parseViaPsi(project, "jspx-regions.jspx", source);
        List<AstRegion> regions = parsedTree.regions();

        assertTrue("Expected JspPsiFile from parser definition", parsedTree.psiFile() instanceof JspPsiFile);
        assertEquals(source, parsedTree.psiFile().getText());
        assertEquals(source, joinRegionText(regions));
        assertContainsText(regions, "DIRECTIVE", "<jsp:directive.taglib uri=\"http://java.sun.com/jsp/jstl/fmt\" prefix=\"fmt\"/>");
        assertContainsText(regions, "DIRECTIVE", "<jsp:directive.taglib uri=\"jakarta.tags.fmt\" prefix=\"jfmt\"/>");
        assertContainsText(regions, "EL_EXPRESSION", "${bean.value}");
        assertHasTemplateText(regions);
    }

    public void testUnterminatedRegionDoesNotConsumeFollowingDirective() throws Exception {
        Project project = getProject();
        String source = readFixture("unterminated-before-directive.jsp");
        ParsedTree parsedTree = parseViaPsi(project, "unterminated-before-directive.jsp", source);
        List<AstRegion> regions = parsedTree.regions();

        assertTrue("Expected JspPsiFile from parser definition", parsedTree.psiFile() instanceof JspPsiFile);
        assertEquals(source, parsedTree.psiFile().getText());
        assertEquals(source, joinRegionText(regions));
        assertContainsType(regions, "ERROR");
        assertContainsText(regions, "DIRECTIVE", "<%@ taglib uri=\"http://java.sun.com/jsp/jstl/functions\" prefix=\"fn\" %>");
        assertContainsText(regions, "DIRECTIVE", "<jsp:directive.taglib uri=\"jakarta.tags.functions\" prefix=\"jfn\"/>");
        assertHasTemplateText(regions);
    }

    private static String readFixture(String fixtureName) throws Exception {
        return Files.readString(Path.of("src", "test", "testData", "parser", fixtureName));
    }

    private static ParsedTree parseViaPsi(Project project, String fileName, String source) {
        return ReadAction.compute(() -> {
            PsiFile psiFile = PsiFileFactory.getInstance(project).createFileFromText(fileName, JspFileType.INSTANCE, source);
            ASTNode root = psiFile.getNode();
            List<AstRegion> regions = new ArrayList<>();
            ASTNode child = root.getFirstChildNode();
            while (child != null) {
                regions.add(new AstRegion(child.getElementType().toString(), child.getText()));
                child = child.getTreeNext();
            }
            return new ParsedTree(psiFile, regions);
        });
    }

    private static void assertContainsText(List<AstRegion> regions, String typeName, String text) {
        assertTrue(
                "Expected region %s with exact text: %s".formatted(typeName, text),
                regions.stream().anyMatch(region -> region.typeName().equals(typeName) && region.text().equals(text))
        );
    }

    private static void assertContainsType(List<AstRegion> regions, String typeName) {
        assertTrue(
                "Expected at least one region of type %s".formatted(typeName),
                regions.stream().anyMatch(region -> region.typeName().equals(typeName))
        );
    }

    private static void assertHasTemplateText(List<AstRegion> regions) {
        assertFalse(
                "Expected non-empty template text regions",
                regions.stream()
                        .filter(region -> region.typeName().equals("TEMPLATE_TEXT"))
                        .map(AstRegion::text)
                        .filter(text -> !text.isBlank())
                        .toList()
                        .isEmpty()
        );
    }

    private static String joinRegionText(List<AstRegion> regions) {
        StringBuilder builder = new StringBuilder();
        regions.forEach(region -> builder.append(region.text()));
        return builder.toString();
    }

    private record ParsedTree(PsiFile psiFile, List<AstRegion> regions) {}

    private record AstRegion(String typeName, String text) {}
}
