package com.rajendarreddyj.intellij.jsp.debugger;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads SmartTomcat run configurations reflectively (the plugin is optional) to learn each deployment's document
 * base and Tomcat work directory, where Jasper writes the generated page classes.
 */
public final class SmartTomcatSupport {
    private static final String CONFIGURATION_CLASS = "com.poratu.idea.plugins.tomcat.conf.TomcatRunConfiguration";

    /** One SmartTomcat deployment; {@code catalinaBase} is the configured one or SmartTomcat's per-module default. */
    public record Deployment(@NotNull String name, @Nullable String docBase, @Nullable String catalinaBase,
                             @Nullable String contextPath) {
        /** {@code <catalinaBase>/work/Catalina/localhost/<context>}; the root context is named {@code ROOT}. */
        public @Nullable Path workDir() {
            if (catalinaBase == null) {
                return null;
            }
            String context = contextPath == null ? "" : contextPath.replace("/", "").trim();
            return Path.of(catalinaBase, "work", "Catalina", "localhost", context.isEmpty() ? "ROOT" : context);
        }
    }

    private SmartTomcatSupport() {
    }

    public static @NotNull List<Deployment> deployments(@NotNull Project project) {
        List<Deployment> result = new ArrayList<>();
        if (project.isDisposed()) {
            return result;
        }
        try {
            for (RunnerAndConfigurationSettings settings : RunManager.getInstance(project).getAllSettings()) {
                RunConfiguration configuration = settings.getConfiguration();
                if (configuration.getClass().getName().equals(CONFIGURATION_CLASS)) {
                    result.add(describe(project, settings.getName(), configuration));
                }
            }
        } catch (RuntimeException | LinkageError ignored) {
            // SmartTomcat internals changed; the plugin then simply has no extra hints
        }
        return result;
    }

    /** Document bases of all SmartTomcat deployments that exist on disk. */
    public static @NotNull List<VirtualFile> docBaseRoots(@NotNull Project project) {
        List<VirtualFile> roots = new ArrayList<>();
        for (Deployment deployment : deployments(project)) {
            if (deployment.docBase() != null && !deployment.docBase().isBlank()) {
                VirtualFile root = LocalFileSystem.getInstance().findFileByPath(deployment.docBase());
                if (root != null && root.isDirectory() && !roots.contains(root)) {
                    roots.add(root);
                }
            }
        }
        return roots;
    }

    /** Where Jasper put {@code className}'s {@code .class}, in the first deployment whose work directory has it. */
    public static @Nullable Path generatedClassFile(@NotNull Project project, @NotNull String className) {
        String relative = className.replace('.', '/') + ".class";
        for (Deployment deployment : deployments(project)) {
            Path workDir = deployment.workDir();
            Path candidate = workDir == null ? null : workDir.resolve(relative);
            if (candidate != null && Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /** One-line description of the configured work directories, for console diagnostics. */
    public static @NotNull String describeWorkDirs(@NotNull Project project) {
        List<String> parts = new ArrayList<>();
        for (Deployment deployment : deployments(project)) {
            Path workDir = deployment.workDir();
            if (workDir != null) {
                parts.add("'" + deployment.name() + "' -> " + workDir);
            }
        }
        return parts.isEmpty() ? "no SmartTomcat run configuration found" : "SmartTomcat work dir " + String.join("; ", parts);
    }

    private static Deployment describe(Project project, String name, RunConfiguration configuration) {
        String catalinaBase = text(configuration, "getCatalinaBase");
        if (catalinaBase == null || catalinaBase.isBlank()) {
            Object module = call(configuration, "getModule");
            Object moduleName = module == null ? null : call(module, "getName");
            catalinaBase = moduleName == null ? null
                    : Path.of(System.getProperty("user.home"), ".SmartTomcat", project.getName(), moduleName.toString()).toString();
        }
        return new Deployment(name, text(configuration, "getDocBase"), catalinaBase, text(configuration, "getContextPath"));
    }

    private static @Nullable String text(Object target, String method) {
        Object value = call(target, method);
        return value == null ? null : value.toString();
    }

    private static @Nullable Object call(Object target, String method) {
        try {
            Method m = target.getClass().getMethod(method);
            return m.invoke(target);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
