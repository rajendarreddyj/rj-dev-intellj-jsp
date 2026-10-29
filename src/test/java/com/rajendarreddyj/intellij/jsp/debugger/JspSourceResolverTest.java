package com.rajendarreddyj.intellij.jsp.debugger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JspSourceResolverTest {
    @Test
    void resolvesProjectWebappSource() throws IOException {
        Path root = createTestRoot("project-webapp");
        Path source = Files.createDirectories(root.resolve("WEB-INF/views")).resolve("home.jsp");
        Files.writeString(source, "home");

        JspSourceResolver.Resolution resolution = JspSourceResolver.resolve(
                "WEB-INF/views/home.jsp",
                List.of(new JspSourceRootMapping("", root.toString()))
        );

        assertEquals(JspSourceResolver.Status.RESOLVED, resolution.status());
        assertNotNull(resolution.path());
        assertEquals(source.toAbsolutePath().normalize(), resolution.path());
    }

    @Test
    void resolvesConfiguredExternalSource() throws IOException {
        Path root = createTestRoot("external-webapp");
        Path source = Files.createDirectories(root.resolve("pages")).resolve("external.jsp");
        Files.writeString(source, "external");

        JspSourceResolver.Resolution resolution = JspSourceResolver.resolve(
                "external/pages/external.jsp",
                List.of(new JspSourceRootMapping("external", root.toString()))
        );

        assertEquals(JspSourceResolver.Status.RESOLVED, resolution.status());
        assertEquals(source.toAbsolutePath().normalize(), resolution.path());
    }

    @Test
    void normalizesSmapSeparators() throws IOException {
        Path root = createTestRoot("separator-normalization");
        Path source = Files.createDirectories(root.resolve("WEB-INF/views")).resolve("mixed.jsp");
        Files.writeString(source, "mixed");

        JspSourceResolver.Resolution resolution = JspSourceResolver.resolve(
                "WEB-INF\\views/mixed.jsp",
                List.of(new JspSourceRootMapping("", root.toString()))
        );

        assertEquals(JspSourceResolver.Status.RESOLVED, resolution.status());
        assertEquals(source.toAbsolutePath().normalize(), resolution.path());
    }

    @Test
    void rejectsTraversalOutsideConfiguredRoot() throws IOException {
        Path root = createTestRoot("reject-traversal");
        Files.createDirectories(root.resolve("WEB-INF/views"));

        JspSourceResolver.Resolution resolution = JspSourceResolver.resolve(
                "../outside.jsp",
                List.of(new JspSourceRootMapping("", root.toString()))
        );

        assertEquals(JspSourceResolver.Status.INVALID, resolution.status());
    }

    @Test
    void returnsNotFoundForMissingSource() throws IOException {
        Path root = createTestRoot("missing-source");
        Files.createDirectories(root.resolve("WEB-INF/views"));

        JspSourceResolver.Resolution resolution = JspSourceResolver.resolve(
                "WEB-INF/views/missing.jsp",
                List.of(new JspSourceRootMapping("", root.toString()))
        );

        assertEquals(JspSourceResolver.Status.NOT_FOUND, resolution.status());
    }

    @Test
    void returnsAmbiguousForMultipleMatches() throws IOException {
        Path rootOne = createTestRoot("ambiguous-one");
        Path rootTwo = createTestRoot("ambiguous-two");
        Files.writeString(Files.createDirectories(rootOne.resolve("WEB-INF/views")).resolve("same.jsp"), "one");
        Files.writeString(Files.createDirectories(rootTwo.resolve("WEB-INF/views")).resolve("same.jsp"), "two");

        JspSourceResolver.Resolution resolution = JspSourceResolver.resolve(
                "WEB-INF/views/same.jsp",
                List.of(
                        new JspSourceRootMapping("", rootOne.toString()),
                        new JspSourceRootMapping("", rootTwo.toString())
                )
        );

        assertEquals(JspSourceResolver.Status.AMBIGUOUS, resolution.status());
        assertNull(resolution.path());
    }

    @Test
    void rejectsDriveLetterAndUncSmapPaths() throws IOException {
        Path root = createTestRoot("absolute-path");
        List<JspSourceRootMapping> mappings = List.of(new JspSourceRootMapping("", root.toString()));

        assertEquals(JspSourceResolver.Status.INVALID,
                JspSourceResolver.resolve("C:\\WEB-INF\\views\\home.jsp", mappings).status());
        assertEquals(JspSourceResolver.Status.INVALID,
                JspSourceResolver.resolve("//server/share/home.jsp", mappings).status());
    }

    @Test
    void resolvesJasperWebappRelativePathWithLeadingSlash() throws IOException {
        Path root = createTestRoot("leading-slash");
        Path jsp = Files.createDirectories(root.resolve("WEB-INF/views")).resolve("home.jsp");
        Files.writeString(jsp, "home");

        JspSourceResolver.Resolution resolution = JspSourceResolver.resolve(
                "/WEB-INF/views/home.jsp",
                List.of(new JspSourceRootMapping("", root.toString()))
        );

        assertEquals(JspSourceResolver.Status.RESOLVED, resolution.status());
        assertEquals(jsp.toRealPath(), resolution.path().toRealPath());
    }

    @Test
    void rejectsSymlinkEscapeOutsideConfiguredRoot() throws IOException {
        Path root = createTestRoot("symlink-escape-root");
        Path outsideRoot = createTestRoot("symlink-escape-outside");
        Path outsideFile = Files.createDirectories(outsideRoot.resolve("outside")).resolve("escape.jsp");
        Files.writeString(outsideFile, "outside");

        Path link = root.resolve("linked");
        try {
            Files.createSymbolicLink(link, outsideRoot.resolve("outside"));
        } catch (UnsupportedOperationException | SecurityException | IOException exception) {
            Assumptions.assumeTrue(false, "Symlinks are unavailable on this platform: " + exception.getMessage());
        }

        JspSourceResolver.Resolution resolution = JspSourceResolver.resolve(
                "linked/escape.jsp",
                List.of(new JspSourceRootMapping("", root.toString()))
        );

        assertEquals(JspSourceResolver.Status.NOT_FOUND, resolution.status());
        assertNull(resolution.path());
    }

    @Test
    void rejectsPrefixSiblingLikePaths() throws IOException {
        Path root = createTestRoot("prefix-sibling");
        Path source = Files.createDirectories(root.resolve("2")).resolve("escape.jsp");
        Files.writeString(source, "escape");

        JspSourceResolver.Resolution resolution = JspSourceResolver.resolve(
                "web2/escape.jsp",
                List.of(new JspSourceRootMapping("web", root.toString()))
        );

        assertEquals(JspSourceResolver.Status.NOT_FOUND, resolution.status());
        assertNull(resolution.path());
    }

    @Test
    void ignoresInvalidConfiguredRootPath() {
        JspSourceResolver.Resolution resolution = JspSourceResolver.resolve(
                "WEB-INF/views/home.jsp",
                List.of(new JspSourceRootMapping("", "\u0000invalid-root"))
        );

        assertEquals(JspSourceResolver.Status.NOT_FOUND, resolution.status());
    }

    private Path createTestRoot(String testName) throws IOException {
        Path root = Path.of("build", "jsp-source-resolver-tests", testName + "-" + System.nanoTime());
        if (Files.exists(root)) {
            try (var stream = Files.walk(root)) {
                stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                    }
                });
            }
        }
        Files.createDirectories(root);
        assertTrue(Files.exists(root));
        return root.toAbsolutePath().normalize();
    }
}
