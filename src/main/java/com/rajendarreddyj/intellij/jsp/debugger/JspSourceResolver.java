package com.rajendarreddyj.intellij.jsp.debugger;

import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public final class JspSourceResolver {
    private JspSourceResolver() {
    }

    public enum Status {
        RESOLVED,
        NOT_FOUND,
        AMBIGUOUS,
        INVALID
    }

    public record Resolution(Status status, @Nullable Path path) {
        public static Resolution resolved(Path path) {
            return new Resolution(Status.RESOLVED, path);
        }

        public static Resolution of(Status status) {
            return new Resolution(status, null);
        }
    }

    public static Resolution resolve(String smapPath, List<JspSourceRootMapping> mappings) {
        NormalizedRelativePath normalizedSmap = normalizeRelativePath(smapPath);
        if (!normalizedSmap.valid()) {
            return Resolution.of(Status.INVALID);
        }

        if (mappings == null || mappings.isEmpty()) {
            return Resolution.of(Status.NOT_FOUND);
        }

        List<Path> matches = new ArrayList<>();
        for (JspSourceRootMapping mapping : mappings) {
            if (mapping == null) {
                continue;
            }
            Path rootPath = normalizeRoot(mapping.getLocalSourceRoot());
            if (rootPath == null) {
                continue;
            }

            String remainder = stripPrefix(normalizedSmap.normalized(), mapping.getSmapPrefix());
            if (remainder == null || remainder.isBlank()) {
                continue;
            }

            NormalizedRelativePath normalizedRemainder = normalizeRelativePath(remainder);
            if (!normalizedRemainder.valid()) {
                continue;
            }

            Path candidate = rootPath.resolve(normalizedRemainder.normalized().replace('/', java.io.File.separatorChar)).normalize();
            if (!isUnderRoot(rootPath, candidate)) {
                continue;
            }
            if (Files.isRegularFile(candidate)) {
                matches.add(candidate);
            }
        }

        Set<Path> distinctMatches = new LinkedHashSet<>(matches);
        if (distinctMatches.size() > 1) {
            return Resolution.of(Status.AMBIGUOUS);
        }
        if (distinctMatches.size() == 1) {
            return Resolution.resolved(distinctMatches.iterator().next());
        }
        return Resolution.of(Status.NOT_FOUND);
    }

    static String stripPrefix(String smapPath, String configuredPrefix) {
        String normalizedPrefix = normalizePrefix(configuredPrefix);
        String normalizedPath = trimSlashes(smapPath);
        if (normalizedPrefix.isEmpty()) {
            return normalizedPath;
        }
        if (normalizedPath.equals(normalizedPrefix)) {
            return "";
        }
        String prefixWithSlash = normalizedPrefix + "/";
        if (!normalizedPath.startsWith(prefixWithSlash)) {
            return null;
        }
        return normalizedPath.substring(prefixWithSlash.length());
    }

    private static String normalizePrefix(String value) {
        if (value == null) {
            return "";
        }
        return trimSlashes(value.replace('\\', '/').trim());
    }

    private static String trimSlashes(String value) {
        String result = value;
        while (result.startsWith("/")) {
            result = result.substring(1);
        }
        while (result.endsWith("/") && result.length() > 1) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    @Nullable
    private static Path normalizeRoot(String rootPath) {
        if (rootPath == null || rootPath.isBlank()) {
            return null;
        }
        final Path root;
        try {
            root = Paths.get(rootPath).toAbsolutePath().normalize();
        } catch (InvalidPathException exception) {
            return null;
        }
        if (!Files.isDirectory(root)) {
            return null;
        }
        return root;
    }

    private static boolean isUnderRoot(Path rootPath, Path candidate) {
        try {
            Path realRoot = rootPath.toRealPath();
            Path realCandidate = candidate.toRealPath();
            return realCandidate.startsWith(realRoot);
        } catch (java.io.IOException ignored) {
            return false;
        }
    }

    private static NormalizedRelativePath normalizeRelativePath(String path) {
        if (path == null) {
            return NormalizedRelativePath.invalid();
        }

        String normalized = path.replace('\\', '/').trim();
        if (normalized.isEmpty()) {
            return NormalizedRelativePath.invalid();
        }

        String lower = normalized.toLowerCase(Locale.ROOT);
        // Jasper SMAP paths are webapp-relative with a leading '/'; UNC and drive-letter paths are rejected.
        if (normalized.startsWith("//") || lower.matches("^[a-z]:.*")) {
            return NormalizedRelativePath.invalid();
        }

        String[] segments = normalized.split("/");
        List<String> cleanedSegments = new ArrayList<>();
        for (String segment : segments) {
            if (segment.isEmpty() || ".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment)) {
                return NormalizedRelativePath.invalid();
            }
            cleanedSegments.add(segment);
        }
        if (cleanedSegments.isEmpty()) {
            return NormalizedRelativePath.invalid();
        }

        return new NormalizedRelativePath(String.join("/", cleanedSegments), true);
    }

    private record NormalizedRelativePath(String normalized, boolean valid) {
        private static NormalizedRelativePath invalid() {
            return new NormalizedRelativePath("", false);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof NormalizedRelativePath other)) {
                return false;
            }
            return valid == other.valid && Objects.equals(normalized, other.normalized);
        }

        @Override
        public int hashCode() {
            return Objects.hash(normalized, valid);
        }
    }
}
