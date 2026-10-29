package com.rajendarreddyj.intellij.jsp.debugger;

import java.util.Objects;

public class JspSourceRootMapping {
    private String smapPrefix = "";
    private String localSourceRoot = "";

    public JspSourceRootMapping() {
    }

    public JspSourceRootMapping(String smapPrefix, String localSourceRoot) {
        this.smapPrefix = smapPrefix == null ? "" : smapPrefix;
        this.localSourceRoot = localSourceRoot == null ? "" : localSourceRoot;
    }

    public String getSmapPrefix() {
        return smapPrefix;
    }

    public void setSmapPrefix(String smapPrefix) {
        this.smapPrefix = smapPrefix == null ? "" : smapPrefix;
    }

    public String getLocalSourceRoot() {
        return localSourceRoot;
    }

    public void setLocalSourceRoot(String localSourceRoot) {
        this.localSourceRoot = localSourceRoot == null ? "" : localSourceRoot;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof JspSourceRootMapping other)) {
            return false;
        }
        return Objects.equals(smapPrefix, other.smapPrefix)
                && Objects.equals(localSourceRoot, other.localSourceRoot);
    }

    @Override
    public int hashCode() {
        return Objects.hash(smapPrefix, localSourceRoot);
    }
}
