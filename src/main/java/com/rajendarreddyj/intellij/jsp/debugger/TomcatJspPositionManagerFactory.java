package com.rajendarreddyj.intellij.jsp.debugger;

import com.intellij.debugger.PositionManager;
import com.intellij.debugger.PositionManagerFactory;
import com.intellij.debugger.engine.DebugProcess;
import org.jetbrains.annotations.NotNull;

public class TomcatJspPositionManagerFactory extends PositionManagerFactory {
    @Override
    public PositionManager createPositionManager(@NotNull DebugProcess process) {
        return new TomcatJspPositionManager(process);
    }
}
