package com.rajendarreddyj.intellij.jsp.debugger;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.Set;
import java.util.stream.Collectors;

public class JspSourceRootDiscoveryTest extends BasePlatformTestCase {
    public void testDiscoversConventionalProjectWebappRoots() {
        myFixture.addFileToProject("src/main/webapp/WEB-INF/views/home.jsp", "home");
        myFixture.addFileToProject("WebContent/WEB-INF/web.xml", "<web-app/>");
        myFixture.addFileToProject("web/WEB-INF/tags/sample.tag", "<tag/>");
        myFixture.addFileToProject("WEB-INF/web.xml", "<web-app/>");

        Set<String> discoveredRoots = JspSourceRootDiscovery.discover(getProject()).stream()
                .map(JspSourceRootMapping::getLocalSourceRoot)
                .collect(Collectors.toSet());

        assertTrue(discoveredRoots.toString(), containsPathEnding(discoveredRoots, "/src/main/webapp"));
        assertTrue(discoveredRoots.toString(), containsPathEnding(discoveredRoots, "/WebContent"));
        assertTrue(discoveredRoots.toString(), containsPathEnding(discoveredRoots, "/web"));
        assertTrue(discoveredRoots.toString(), containsPathEnding(discoveredRoots, "/src"));
    }

    public void testDiscoversNonConventionalRootByWebInfAndIgnoresBuildOutput() {
        myFixture.addFileToProject("Rollout/JSP/WEB-INF/web.xml", "<web-app/>");
        myFixture.addFileToProject("Rollout/target/app/WEB-INF/web.xml", "<web-app/>");

        Set<String> roots = JspSourceRootDiscovery.discover(getProject()).stream()
                .map(JspSourceRootMapping::getLocalSourceRoot)
                .map(path -> path.replace('\\', '/'))
                .collect(Collectors.toSet());

        assertTrue(roots.toString(), roots.stream().anyMatch(path -> path.endsWith("/Rollout/JSP")));
        assertTrue(roots.toString(), roots.stream().noneMatch(path -> path.contains("/target/")));
    }

    private boolean containsPathEnding(Set<String> roots, String ending) {
        String normalizedEnding = ending.replace('\\', '/');
        return roots.stream()
                .map(path -> path.replace('\\', '/'))
                .anyMatch(path -> path.endsWith(normalizedEnding));
    }
}
