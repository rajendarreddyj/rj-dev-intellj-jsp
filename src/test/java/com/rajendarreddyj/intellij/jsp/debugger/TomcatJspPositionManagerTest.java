package com.rajendarreddyj.intellij.jsp.debugger;

import com.intellij.debugger.PositionManager;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebugProcessListener;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.jdi.VirtualMachineProxy;
import com.intellij.debugger.engine.managerThread.DebuggerManagerThread;
import com.intellij.debugger.requests.RequestManager;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.containers.ContainerUtil;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.ArrayType;
import com.sun.jdi.ClassLoaderReference;
import com.sun.jdi.ClassType;
import com.sun.jdi.Method;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class TomcatJspPositionManagerTest extends BasePlatformTestCase {
    public void testNestedClassesFromDeclarationsAreNotExpectedToCarrySmap() {
        String pkg = "org.apache.jsp";
        assertTrue(TomcatJspPositionManager.isGeneratedPageClass("org.apache.jsp.admin.CustomListEdit_jsp", pkg));
        assertFalse(TomcatJspPositionManager.isGeneratedPageClass("org.apache.jsp.admin.CustomListEdit_jsp$BOListCreator", pkg));
        assertFalse(TomcatJspPositionManager.isGeneratedPageClass("com.acme.Other", pkg));
    }

    public void testReportsMissingJsr45MetadataOnce() {
        FakeDebugProcess debugProcess = new FakeDebugProcess(getProject());
        List<String> diagnostics = new ArrayList<>();
        JspDebugMetadataDiagnostics.MessageSink sink = (process, message) -> diagnostics.add(message);

        JspDebugMetadataDiagnostics.reportMissingJsr45Metadata(debugProcess, sink);
        JspDebugMetadataDiagnostics.reportMissingJsr45Metadata(debugProcess, sink);

        assertEquals(1, diagnostics.size());
        String message = diagnostics.get(0);
        assertTrue(message.contains("suppressSmap=false"));
        assertTrue(message.contains("classdebuginfo=true"));
        assertTrue(message.contains("-g"));
    }

    private static final class FakeDebugProcess extends UserDataHolderBase implements DebugProcess {
        private final Project project;
        private final List<String> consoleMessages = ContainerUtil.createConcurrentList();

        private FakeDebugProcess(Project project) {
            this.project = project;
        }

        @Override
        public @NotNull Project getProject() {
            return project;
        }

        @Override
        public RequestManager getRequestsManager() {
            return null;
        }

        @Override
        public PositionManager getPositionManager() {
            return null;
        }

        @Override
        public VirtualMachineProxy getVirtualMachineProxy() {
            return null;
        }

        @Override
        public void addDebugProcessListener(DebugProcessListener listener, Disposable parentDisposable) {
        }

        @Override
        public void addDebugProcessListener(DebugProcessListener listener) {
        }

        @Override
        public void removeDebugProcessListener(DebugProcessListener listener) {
        }

        @Override
        public void appendPositionManager(PositionManager manager) {
        }

        @Override
        public void waitFor() {
        }

        @Override
        public void waitFor(long timeout) {
        }

        @Override
        public void stop(boolean force) {
        }

        @Override
        public ExecutionResult getExecutionResult() {
            return null;
        }

        @Override
        public DebuggerManagerThread getManagerThread() {
            return null;
        }

        @Override
        public Value invokeMethod(EvaluationContext evaluationContext, ObjectReference objectReference, Method method, List<? extends Value> list) throws EvaluateException {
            return null;
        }

        @Override
        public Value invokeMethod(EvaluationContext evaluationContext, ClassType classType, Method method, List<? extends Value> list) throws EvaluateException {
            return null;
        }

        @Override
        public Value invokeInstanceMethod(EvaluationContext evaluationContext, ObjectReference objectReference, Method method, List<? extends Value> list, int i) throws EvaluateException {
            return null;
        }

        @Override
        public ReferenceType findClass(EvaluationContext evaluationContext, String s, ClassLoaderReference classLoaderReference) throws EvaluateException {
            return null;
        }

        @Override
        public ArrayReference newInstance(ArrayType arrayType, int i) throws EvaluateException {
            return null;
        }

        @Override
        public ObjectReference newInstance(EvaluationContext evaluationContext, ClassType classType, Method method, List<? extends Value> list) throws EvaluateException {
            return null;
        }

        @Override
        public boolean isAttached() {
            return false;
        }

        @Override
        public boolean isDetached() {
            return false;
        }

        @Override
        public boolean isDetaching() {
            return false;
        }

        @Override
        public GlobalSearchScope getSearchScope() {
            return GlobalSearchScope.EMPTY_SCOPE;
        }

        @Override
        public void printToConsole(String s) {
            consoleMessages.add(s);
        }

        @Override
        public ProcessHandler getProcessHandler() {
            return null;
        }
    }
}
