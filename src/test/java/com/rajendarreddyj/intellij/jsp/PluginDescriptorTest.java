package com.rajendarreddyj.intellij.jsp;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginDescriptorTest {
    @Test
    void testPluginDeclaresCommunitySafeDependencies() throws Exception {
        InputStream descriptorStream = getClass().getResourceAsStream("/META-INF/plugin.xml");
        assertNotNull(descriptorStream, "plugin.xml must exist in src/main/resources/META-INF");

        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(descriptorStream);
        assertEquals("com.rajendarreddyj.intellij.jsp", textContent(document, "id"));

        Set<String> dependencies = dependencyValues(document);
        assertTrue(dependencies.contains("com.intellij.modules.lang"));
        assertTrue(dependencies.contains("com.intellij.modules.xml"));
        assertTrue(dependencies.contains("com.intellij.java"));

        assertFalse(dependencies.contains("com.intellij.jsp"));
        assertFalse(dependencies.contains("com.intellij.javaee"));
        assertFalse(dependencies.contains("com.intellij.modules.javaee"));
        assertFalse(dependencies.contains("com.intellij.modules.jakartaee"));
    }

    private static String textContent(Document document, String tagName) {
        NodeList list = document.getElementsByTagName(tagName);
        assertTrue(list.getLength() > 0, "Missing <%s> in plugin.xml".formatted(tagName));
        return list.item(0).getTextContent().trim();
    }

    private static Set<String> dependencyValues(Document document) {
        Set<String> values = new HashSet<>();
        NodeList list = document.getElementsByTagName("depends");
        for (int i = 0; i < list.getLength(); i++) {
            values.add(list.item(i).getTextContent().trim());
        }
        return values;
    }
}

