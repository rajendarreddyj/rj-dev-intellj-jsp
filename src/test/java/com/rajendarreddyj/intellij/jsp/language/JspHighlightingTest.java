package com.rajendarreddyj.intellij.jsp.language;

import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.psi.TokenType;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.openapi.editor.XmlHighlighterColors;
import com.intellij.lexer.Lexer;

import java.util.ArrayList;
import java.util.List;

public class JspHighlightingTest extends BasePlatformTestCase {
    public void testDirectiveIsSplitIntoDelimiterNameAttributesAndValues() {
        List<String> tokens = tagTokens("<%@ page import=\"java.util.List\" %>");
        assertEquals(List.of("DELIMITER<%@", "TAG_NAME page", "ATTR_NAME import", "ATTR_EQ =",
                "ATTR_VALUE \"java.util.List\"", "DELIMITER%>"), tokens);
    }

    public void testIncludeActionAndXmlDirectiveTags() {
        assertEquals(List.of("DELIMITER<", "TAG_NAME jsp:include", "ATTR_NAME page", "ATTR_EQ =",
                "ATTR_VALUE 'a.jsp'", "DELIMITER/>"), tagTokens("<jsp:include page='a.jsp'/>"));
        assertEquals(List.of("DELIMITER<", "TAG_NAME jsp:directive.include", "ATTR_NAME file", "ATTR_EQ =",
                "ATTR_VALUE \"x.jspf\"", "DELIMITER/>"), tagTokens("<jsp:directive.include file=\"x.jspf\"/>"));
    }

    public void testScriptletBodyIsLeftForJavaAndElIsStyled() {
        assertEquals(List.of("DELIMITER<%=", "BODY name", "DELIMITER%>"), tagTokens("<%=name%>"));
        assertEquals(List.of("DELIMITER${", "EL_BODY user.name", "DELIMITER}"), tagTokens("${user.name}"));
    }

    public void testTagLexerRestartsFromAnyTokenBoundary() {
        String text = "<jsp:scriptlet>int a = 1;</jsp:scriptlet>";
        Lexer lexer = new JspTagLexer();
        lexer.start(text);
        List<String> expected = new ArrayList<>();
        List<int[]> restartPoints = new ArrayList<>();
        while (lexer.getTokenType() != null) {
            restartPoints.add(new int[]{lexer.getTokenStart(), lexer.getState()});
            expected.add(lexer.getTokenType() + ":" + lexer.getTokenStart() + "-" + lexer.getTokenEnd());
            lexer.advance();
        }
        for (int i = 0; i < restartPoints.size(); i++) {
            Lexer restarted = new JspTagLexer();
            restarted.start(text, restartPoints.get(i)[0], text.length(), restartPoints.get(i)[1]);
            List<String> actual = new ArrayList<>();
            while (restarted.getTokenType() != null) {
                actual.add(restarted.getTokenType() + ":" + restarted.getTokenStart() + "-" + restarted.getTokenEnd());
                restarted.advance();
            }
            assertEquals(expected.subList(i, expected.size()), actual);
        }
    }

    public void testTemplateTextIsHighlightedAsHtmlAndScriptSurvivesScriptlets() {
        String text = "<%@ page import=\"a.B\" %>\n<div class=\"x\">hi</div>\n<script>\nvar a = 1;\n<% int n = 2; %>\nvar b = 2;\n</script>\n";
        EditorHighlighter highlighter = new JspEditorHighlighterProvider().getEditorHighlighter(
                getProject(), JspFileType.INSTANCE, null, EditorColorsManager.getInstance().getGlobalScheme());
        highlighter.setText(text);

        assertTrue("directive name styled", hasKey(highlighter, text, "page", JspSyntaxHighlighter.JSP_TAG_NAME));
        assertTrue("html tag name styled", hasKey(highlighter, text, "div", XmlHighlighterColors.HTML_TAG_NAME));
        assertTrue("html attribute styled", hasKey(highlighter, text, "class", XmlHighlighterColors.HTML_ATTRIBUTE_NAME));
        assertTrue("script tag after scriptlet closes", hasKey(highlighter, text, "script", XmlHighlighterColors.HTML_TAG_NAME,
                text.lastIndexOf("script")));
    }

    private static boolean hasKey(EditorHighlighter highlighter, String text, String word, TextAttributesKey key) {
        return hasKey(highlighter, text, word, key, text.indexOf(word));
    }

    private static boolean hasKey(EditorHighlighter highlighter, String text, String word, TextAttributesKey key, int offset) {
        HighlighterIterator iterator = highlighter.createIterator(offset);
        assertEquals(word, text.substring(iterator.getStart(), iterator.getEnd()));
        return iterator.getTextAttributesKeys().length > 0 && contains(iterator.getTextAttributesKeys(), key);
    }

    private static boolean contains(TextAttributesKey[] keys, TextAttributesKey expected) {
        for (TextAttributesKey key : keys) {
            if (key.equals(expected) || expected.equals(key.getFallbackAttributeKey())) {
                return true;
            }
        }
        return false;
    }

    private static List<String> tagTokens(String text) {
        Lexer lexer = new JspTagLexer();
        lexer.start(text);
        List<String> result = new ArrayList<>();
        while (lexer.getTokenType() != null) {
            if (lexer.getTokenType() != TokenType.WHITE_SPACE) {
                String name = lexer.getTokenType().toString().replace("JSP_", "");
                String content = lexer.getTokenText();
                result.add(name + (name.equals("DELIMITER") ? "" : " ") + content);
            }
            lexer.advance();
        }
        return result;
    }
}
