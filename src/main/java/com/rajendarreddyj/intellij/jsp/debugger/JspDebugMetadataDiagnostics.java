package com.rajendarreddyj.intellij.jsp.debugger;

import com.intellij.debugger.engine.DebugProcess;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

public final class JspDebugMetadataDiagnostics {
    private static final Key<Boolean> MISSING_JSR45_METADATA_REPORTED =
            Key.create("com.rajendarreddyj.intellij.jsp.debugger.missingJsr45MetadataReported");

    static final String MISSING_JSR45_MESSAGE =
            "[JSP Community Support] Missing JSP JSR-45 debug metadata for Jasper-generated classes. " +
                    "Ensure Jasper keeps SMAP enabled (suppressSmap=false, development=true) and compiles with class " +
                    "debug info enabled (-g / classdebuginfo=true). If Jasper's generatedJspPackageName is customised, set the same " +
                    "package in Settings | JSP Source Roots.";

    private static volatile MessageSink messageSink = (process, message) ->
            process.printToConsole(message + System.lineSeparator());

    private JspDebugMetadataDiagnostics() {
    }

    @FunctionalInterface
    interface MessageSink {
        void print(@NotNull DebugProcess process, @NotNull String message);
    }

    public static void reportMissingJsr45Metadata(@NotNull DebugProcess process) {
        reportMissingJsr45Metadata(process, messageSink);
    }

    static void reportMissingJsr45Metadata(@NotNull DebugProcess process, @NotNull MessageSink sink) {
        boolean shouldReport;
        synchronized (process) {
            shouldReport = !Boolean.TRUE.equals(process.getUserData(MISSING_JSR45_METADATA_REPORTED));
            if (shouldReport) {
                process.putUserData(MISSING_JSR45_METADATA_REPORTED, Boolean.TRUE);
            }
        }
        if (shouldReport) {
            sink.print(process, MISSING_JSR45_MESSAGE);
        }
    }
}
