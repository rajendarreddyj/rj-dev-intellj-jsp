package com.rajendarreddyj.intellij.jsp.debugger;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.List;

public class JspSourceRootSettingsTest extends BasePlatformTestCase {
    public void testSettingsRoundTripPersistsExternalRoots() {
        JspSourceRootSettings settings = getProject().getService(JspSourceRootSettings.class);
        assertNotNull(settings);

        List<JspSourceRootMapping> mappings = List.of(
                new JspSourceRootMapping("external", "D:\\external\\webapp"),
                new JspSourceRootMapping("exploded", "D:\\deployments\\app")
        );
        settings.setMappings(mappings);

        JspSourceRootSettings.State persisted = settings.getState();
        assertNotNull(persisted);

        JspSourceRootSettings reloaded = new JspSourceRootSettings();
        reloaded.loadState(persisted);

        assertEquals(mappings, reloaded.getMappings());
    }

    public void testGeneratedJspPackageDefaultsAndPersists() {
        JspSourceRootSettings settings = new JspSourceRootSettings();
        assertEquals("org.apache.jsp", settings.getGeneratedJspPackage());

        settings.setGeneratedJspPackage(" com.example.jsp ");
        JspSourceRootSettings reloaded = new JspSourceRootSettings();
        reloaded.loadState(settings.getState());
        assertEquals("com.example.jsp", reloaded.getGeneratedJspPackage());

        settings.setGeneratedJspPackage("not a package!");
        assertEquals("org.apache.jsp", settings.getGeneratedJspPackage());
    }

    public void testGeneratedClassPackageMatchingCoversTagFiles() {
        assertTrue(TomcatJspPositionManager.isInPackage("org.apache.jsp.index_jsp", "org.apache.jsp"));
        assertTrue(TomcatJspPositionManager.isInPackage("org.apache.jsp.tag.web.layout_tag", "org.apache.jsp"));
        assertFalse(TomcatJspPositionManager.isInPackage("org.apache.jspx.Other", "org.apache.jsp"));
        assertTrue(TomcatJspPositionManager.isInPackage("com.example.jsp.home_jsp", "com.example.jsp"));
    }
}
