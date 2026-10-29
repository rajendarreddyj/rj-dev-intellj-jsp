package com.rajendarreddyj.intellij.jsp.debugger;

import com.intellij.debugger.NoDataException;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.JSR45PositionManager;
import com.intellij.debugger.engine.SourcesFinder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiFile;
import com.rajendarreddyj.intellij.jsp.language.JspFileType;
import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.Location;
import com.sun.jdi.ReferenceType;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class TomcatJspPositionManager extends JSR45PositionManager<Project> {
    private static final com.intellij.openapi.diagnostic.Logger LOG =
            com.intellij.openapi.diagnostic.Logger.getInstance(TomcatJspPositionManager.class);
    static final String JSP_STRATUM = "JSP";
    private static final LanguageFileType[] ACCEPTED_FILE_TYPES = {JspFileType.INSTANCE};

    private final MetadataReporter metadataReporter;
    private final Set<String> tracedKeys = ConcurrentHashMap.newKeySet();

    @FunctionalInterface
    interface MetadataReporter {
        void report(@NotNull DebugProcess debugProcess);
    }

    public TomcatJspPositionManager(@NotNull DebugProcess debugProcess) {
        this(debugProcess, reportingFinder(debugProcess, new TomcatJspSourceFinder()),
                JspDebugMetadataDiagnostics::reportMissingJsr45Metadata);
    }

    private static SourcesFinder<Project> reportingFinder(DebugProcess debugProcess, SourcesFinder<Project> delegate) {
        Set<String> reported = ConcurrentHashMap.newKeySet();
        return (relPath, project, scope) -> {
            PsiFile file = delegate.findSourceFile(relPath, project, scope);
            if (file == null && reported.add(relPath)) {
                debugProcess.printToConsole("[JSP Community Support] Cannot map generated page source '" + relPath
                        + "' to a project JSP file; add a mapping in Settings | JSP Source Roots.\n");
            }
            return file;
        };
    }

    TomcatJspPositionManager(
            @NotNull DebugProcess debugProcess,
            @NotNull SourcesFinder<Project> sourcesFinder,
            @NotNull MetadataReporter metadataReporter
    ) {
        super(debugProcess, debugProcess.getProject(), JSP_STRATUM, ACCEPTED_FILE_TYPES, sourcesFinder);
        this.metadataReporter = metadataReporter;
        traceOnce("start", "JSP position manager active for generated package '" + getGeneratedClassesPackage()
                + "'; web roots " + describeRoots());
    }

    private String describeRoots() {
        try {
            return ApplicationManager.getApplication().runReadAction((Computable<String>) () ->
                    JspSourceRootDiscovery.discoverRoots(myDebugProcess.getProject()).stream()
                            .map(com.intellij.openapi.vfs.VirtualFile::getPath).toList().toString());
        } catch (RuntimeException e) {
            return "(unavailable: " + e + ")";
        }
    }

    @Override
    public com.sun.jdi.request.ClassPrepareRequest createPrepareRequest(
            @NotNull com.intellij.debugger.requests.ClassPrepareRequestor requestor,
            @NotNull com.intellij.debugger.SourcePosition position) throws NoDataException {
        traceOnce("request:" + position.getFile().getName() + ":" + position.getLine(),
                "Breakpoint at " + position.getFile().getName() + ":" + (position.getLine() + 1)
                        + " waiting for classes in '" + getGeneratedClassesPackage() + ".*'");
        return super.createPrepareRequest(requestor, position);
    }

    /**
     * Jasper's {@code generatedJspPackageName} (default {@code org.apache.jsp}); tag files use the {@code .tag}
     * sub-package. Called from the super constructor, so it must not rely on this class's fields.
     */
    @Override
    protected @NotNull String getGeneratedClassesPackage() {
        return generatedPackage(myDebugProcess);
    }

    static @NotNull String generatedPackage(DebugProcess debugProcess) {
        Project project = debugProcess == null ? null : debugProcess.getProject();
        if (project == null || project.isDisposed()) {
            return JspSourceRootSettings.DEFAULT_GENERATED_JSP_PACKAGE;
        }
        JspSourceRootSettings settings = JspSourceRootSettings.getInstance(project);
        return settings == null ? JspSourceRootSettings.DEFAULT_GENERATED_JSP_PACKAGE : settings.getGeneratedJspPackage();
    }

    @Override
    public com.intellij.debugger.SourcePosition getSourcePosition(Location location) throws NoDataException {
        ReferenceType declaringType = safeDeclaringType(location);
        reportIfMissingJspStratum(declaringType);
        if (!isTomcatJspClass(declaringType)) {
            return super.getSourcePosition(location);
        }
        try {
            com.intellij.debugger.SourcePosition position = super.getSourcePosition(location);
            traceOnce("frame-ok:" + declaringType.name(), "Mapped " + declaringType.name() + " to "
                    + position.getFile().getName() + ":" + (position.getLine() + 1));
            return position;
        } catch (NoDataException e) {
            String detail;
            try {
                detail = "path '" + getRelativeSourcePathByLocation(location) + "'";
            } catch (AbsentInformationException | RuntimeException ex) {
                detail = "no JSP source path (" + ex + ")";
            }
            traceOnce("frame-fail:" + declaringType.name(), "Frame in " + declaringType.name()
                    + " could not be mapped to a JSP file: " + detail);
            throw e;
        }
    }

    @Override
    protected String getRelativeSourcePathByLocation(Location location) throws AbsentInformationException {
        ReferenceType declaringType = safeDeclaringType(location);
        reportIfMissingJspStratum(declaringType);
        try {
            return super.getRelativeSourcePathByLocation(location);
        } catch (AbsentInformationException exception) {
            reportIfTomcatJspClass(declaringType);
            throw exception;
        }
    }

    @Override
    protected List<String> getRelativeSourePathsByType(ReferenceType type) throws AbsentInformationException {
        reportIfMissingJspStratum(type);
        try {
            return super.getRelativeSourePathsByType(type);
        } catch (AbsentInformationException exception) {
            reportIfTomcatJspClass(type);
            throw exception;
        }
    }

    @Override
    protected void onClassPrepare(DebugProcess debuggerProcess, ReferenceType referenceType,
                                  com.intellij.debugger.SourcePosition position,
                                  com.intellij.debugger.requests.ClassPrepareRequestor requestor) {
        if (isTomcatJspClass(referenceType)) {
            traceOnce("prepare:" + referenceType.name(), "Loaded JSP class " + referenceType.name()
                    + " (strata " + referenceType.availableStrata() + ") for breakpoint at "
                    + position.getFile().getName() + ":" + (position.getLine() + 1)
                    + compiledClassNote(referenceType.name(), position.getFile()));
        }
        super.onClassPrepare(debuggerProcess, referenceType, position, requestor);
    }

    @Override
    protected List<Location> getLocationsOfLine(ReferenceType type, String sourceName, String sourcePath, int line)
            throws AbsentInformationException {
        reportIfMissingJspStratum(type);
        try {
            List<Location> locations = super.getLocationsOfLine(type, sourceName, sourcePath, line);
            if (locations == null || locations.isEmpty()) {
                traceOnce("noloc:" + type.name() + ":" + sourcePath + ":" + line,
                        "No generated code maps to " + sourcePath + ":" + line + " in " + type.name()
                                + " (the line may be template text, a comment, or the page changed since it was compiled)");
            }
            return locations;
        } catch (AbsentInformationException exception) {
            reportIfTomcatJspClass(type);
            throw exception;
        }
    }

    /** Locates the compiled class in the SmartTomcat work dir and warns when the JSP was edited after compilation. */
    private String compiledClassNote(String className, PsiFile jspFile) {
        Project project = myDebugProcess.getProject();
        java.nio.file.Path classFile = SmartTomcatSupport.generatedClassFile(project, className);
        if (classFile == null) {
            return "; class file not found under " + SmartTomcatSupport.describeWorkDirs(project);
        }
        String note = "; compiled class " + classFile;
        com.intellij.openapi.vfs.VirtualFile source = jspFile.getVirtualFile();
        try {
            if (source != null && source.getTimeStamp() > java.nio.file.Files.getLastModifiedTime(classFile).toMillis()) {
                note += " is OLDER than the JSP source; reload the page so Tomcat recompiles it";
            }
        } catch (java.io.IOException ignored) {
            // timestamps are only a hint
        }
        return note;
    }

    void traceOnce(String key, String message) {
        if (tracedKeys.add(key)) {
            LOG.debug("[JSP Community Support] " + message);
            if (Boolean.getBoolean("jsp.community.debug.trace")) {
                myDebugProcess.printToConsole("[JSP Community Support] " + message + "\n");
            }
        }
    }

    private void reportIfMissingJspStratum(ReferenceType type) {
        if (!isTomcatJspClass(type)) {
            return;
        }
        List<String> strata = type.availableStrata();
        if (strata == null || strata.stream().noneMatch(JSP_STRATUM::equals)) {
            reportIfTomcatJspClass(type);
        }
    }

    private void reportIfTomcatJspClass(ReferenceType type) {
        if (isTomcatJspClass(type)) {
            metadataReporter.report(myDebugProcess);
        }
    }

    private boolean isTomcatJspClass(ReferenceType type) {
        return type != null && isGeneratedPageClass(type.name(), getGeneratedClassesPackage());
    }

    static boolean isInPackage(String className, String packageName) {
        return className != null && className.startsWith(packageName + ".");
    }

    /**
     * Jasper installs the SMAP only on the page class itself; classes declared inside {@code <%! %>} blocks are
     * separate nested classes ({@code page_jsp$Inner}) that never carry the JSP stratum.
     */
    static boolean isGeneratedPageClass(String className, String packageName) {
        return isInPackage(className, packageName) && className.indexOf('$') < 0;
    }

    private static ReferenceType safeDeclaringType(Location location) {
        if (location == null) {
            return null;
        }
        try {
            return location.declaringType();
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
