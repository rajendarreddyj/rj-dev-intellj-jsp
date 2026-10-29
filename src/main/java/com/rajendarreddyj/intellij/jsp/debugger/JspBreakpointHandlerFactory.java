package com.rajendarreddyj.intellij.jsp.debugger;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.JavaBreakpointHandler;
import com.intellij.debugger.engine.JavaBreakpointHandlerFactory;

/**
 * Without a handler for {@link JspLineBreakpointType} the Java debugger only receives breakpoints of its own
 * {@code java-line} type, so JSP breakpoints would never be installed in the target VM.
 */
public final class JspBreakpointHandlerFactory implements JavaBreakpointHandlerFactory {
    @Override
    public JavaBreakpointHandler createHandler(DebugProcessImpl process) {
        return new JavaBreakpointHandler(JspLineBreakpointType.class, process);
    }
}
