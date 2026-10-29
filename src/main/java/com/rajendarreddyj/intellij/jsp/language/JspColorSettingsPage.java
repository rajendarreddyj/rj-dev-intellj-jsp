package com.rajendarreddyj.intellij.jsp.language;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;

public final class JspColorSettingsPage implements ColorSettingsPage {
    private static final AttributesDescriptor[] DESCRIPTORS = {
            new AttributesDescriptor("JSP delimiters (<%, %>, ${, })", JspSyntaxHighlighter.JSP_DELIMITER),
            new AttributesDescriptor("Directive / action name", JspSyntaxHighlighter.JSP_TAG_NAME),
            new AttributesDescriptor("Attribute name", JspSyntaxHighlighter.JSP_ATTRIBUTE_NAME),
            new AttributesDescriptor("Attribute value", JspSyntaxHighlighter.JSP_ATTRIBUTE_VALUE),
            new AttributesDescriptor("Equals sign", JspSyntaxHighlighter.JSP_EQUALS),
            new AttributesDescriptor("EL expression", JspSyntaxHighlighter.JSP_EL),
            new AttributesDescriptor("JSP comment", JspSyntaxHighlighter.JSP_COMMENT),
            new AttributesDescriptor("Bad region", JspSyntaxHighlighter.JSP_BAD),
    };

    private static final String DEMO = """
            <%@ page contentType="text/html;charset=UTF-8" import="java.util.List" %>
            <%@ include file="/include/header.jspf" %>
            <%-- a JSP comment --%>
            <html>
            <body>
              <jsp:include page="menu.jsp"/>
              <% String name = request.getParameter("name"); %>
              <p class="greeting">Hello ${user.name} <%= name %></p>
              <script>
                var total = 1 + 2;
              </script>
            </body>
            </html>
            """;

    @Override
    public @Nullable Icon getIcon() {
        return null;
    }

    @Override
    public @NotNull SyntaxHighlighter getHighlighter() {
        return new JspSyntaxHighlighter();
    }

    @Override
    public @NotNull String getDemoText() {
        return DEMO;
    }

    @Override
    public @Nullable Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
        return null;
    }

    @Override
    public AttributesDescriptor @NotNull [] getAttributeDescriptors() {
        return DESCRIPTORS;
    }

    @Override
    public ColorDescriptor @NotNull [] getColorDescriptors() {
        return ColorDescriptor.EMPTY_ARRAY;
    }

    @Override
    public @NotNull String getDisplayName() {
        return "JSP";
    }
}
