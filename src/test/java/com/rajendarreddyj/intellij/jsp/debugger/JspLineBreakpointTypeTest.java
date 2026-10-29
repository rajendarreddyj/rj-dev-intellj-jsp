package com.rajendarreddyj.intellij.jsp.debugger;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;

import java.util.Arrays;

public class JspLineBreakpointTypeTest extends BasePlatformTestCase {
    public void testBreakpointCanBeToggledOnJspLine() throws Exception {
        PsiFile file = myFixture.configureByText("page.jsp", "<html>\n<% int a = 1; %>\n\n</html>\n");
        VirtualFile vf = file.getVirtualFile();

        assertTrue(XDebuggerUtil.getInstance().canPutBreakpointAt(getProject(), vf, 1));
        assertFalse("blank lines carry no code", XDebuggerUtil.getInstance().canPutBreakpointAt(getProject(), vf, 2));

        XDebuggerUtil.getInstance().toggleLineBreakpoint(getProject(), vf, 1);
        long deadline = System.currentTimeMillis() + 10_000;
        while (breakpointCount() == 0 && System.currentTimeMillis() < deadline) {
            com.intellij.testFramework.PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
            Thread.sleep(50);
        }
        XLineBreakpoint<?>[] breakpoints = ReadAction.compute(() -> allLineBreakpoints());
        assertEquals(1, breakpoints.length);
        assertEquals(1, breakpoints[0].getLine());
        assertTrue(breakpoints[0].getType() instanceof JspLineBreakpointType);
        assertNotNull(XBreakpointType.EXTENSION_POINT_NAME.findExtension(JspLineBreakpointType.class));
    }

    private int breakpointCount() {
        return allLineBreakpoints().length;
    }

    private XLineBreakpoint<?>[] allLineBreakpoints() {
        return Arrays.stream(XDebuggerManager.getInstance(getProject()).getBreakpointManager().getAllBreakpoints())
                .filter(XLineBreakpoint.class::isInstance).map(XLineBreakpoint.class::cast)
                .toArray(XLineBreakpoint<?>[]::new);
    }
}
