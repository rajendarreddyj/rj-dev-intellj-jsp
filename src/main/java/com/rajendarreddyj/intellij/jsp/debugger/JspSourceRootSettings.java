package com.rajendarreddyj.intellij.jsp.debugger;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@State(
        name = "JspSourceRootSettings",
        storages = @Storage(StoragePathMacros.WORKSPACE_FILE)
)
public class JspSourceRootSettings implements PersistentStateComponent<JspSourceRootSettings.State> {
    /** Jasper's default {@code generatedJspPackageName} (Tomcat 9, 10.1 and 11). */
    public static final String DEFAULT_GENERATED_JSP_PACKAGE = "org.apache.jsp";
    private static final Pattern PACKAGE_PATTERN =
            Pattern.compile("[A-Za-z_$][A-Za-z\\d_$]*(\\.[A-Za-z_$][A-Za-z\\d_$]*)*");

    private State state = new State();

    public static JspSourceRootSettings getInstance(Project project) {
        return project.getService(JspSourceRootSettings.class);
    }

    public List<JspSourceRootMapping> getMappings() {
        return new ArrayList<>(state.mappings);
    }

    public void setMappings(List<JspSourceRootMapping> mappings) {
        state.mappings = sanitize(mappings);
    }

    public String getGeneratedJspPackage() {
        return sanitizePackage(state.generatedJspPackage);
    }

    public void setGeneratedJspPackage(String generatedJspPackage) {
        state.generatedJspPackage = sanitizePackage(generatedJspPackage);
    }

    @Override
    public @Nullable State getState() {
        State copy = new State();
        copy.mappings = sanitize(state.mappings);
        copy.generatedJspPackage = sanitizePackage(state.generatedJspPackage);
        return copy;
    }

    @Override
    public void loadState(@NotNull State state) {
        State copy = new State();
        XmlSerializerUtil.copyBean(state, copy);
        copy.mappings = sanitize(copy.mappings);
        copy.generatedJspPackage = sanitizePackage(copy.generatedJspPackage);
        this.state = copy;
    }

    private static List<JspSourceRootMapping> sanitize(List<JspSourceRootMapping> mappings) {
        List<JspSourceRootMapping> sanitized = new ArrayList<>();
        if (mappings == null) {
            return sanitized;
        }
        for (JspSourceRootMapping mapping : mappings) {
            if (mapping == null) {
                continue;
            }
            String localRoot = mapping.getLocalSourceRoot() == null ? "" : mapping.getLocalSourceRoot().trim();
            if (localRoot.isEmpty()) {
                continue;
            }
            sanitized.add(new JspSourceRootMapping(
                    mapping.getSmapPrefix() == null ? "" : mapping.getSmapPrefix().trim(),
                    localRoot
            ));
        }
        return sanitized;
    }

    static String sanitizePackage(String value) {
        String trimmed = value == null ? "" : value.trim();
        return PACKAGE_PATTERN.matcher(trimmed).matches() ? trimmed : DEFAULT_GENERATED_JSP_PACKAGE;
    }

    public static class State {
        public List<JspSourceRootMapping> mappings = new ArrayList<>();
        public String generatedJspPackage = DEFAULT_GENERATED_JSP_PACKAGE;
    }
}
