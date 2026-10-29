package com.rajendarreddyj.intellij.jsp.language;

import com.intellij.lexer.Lexer;
import com.intellij.psi.tree.IElementType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JspLexerTest {
    @Test
    void testRecognizesDirectiveActionCommentScriptletAndEl() {
        String source = "t<%@ page contentType=\"text/html\" %>t<jsp:include page=\"/a.jsp\"/>t<%-- c --%>t<%! int n=1; %>t<%= n %>t<% n++; %>t${user.name}t";
        assertLexed(source, List.of(
                token(JspTokenTypes.TEMPLATE_TEXT, "t"),
                token(JspTokenTypes.DIRECTIVE, "<%@ page contentType=\"text/html\" %>"),
                token(JspTokenTypes.TEMPLATE_TEXT, "t"),
                token(JspTokenTypes.ACTION, "<jsp:include page=\"/a.jsp\"/>"),
                token(JspTokenTypes.TEMPLATE_TEXT, "t"),
                token(JspTokenTypes.COMMENT, "<%-- c --%>"),
                token(JspTokenTypes.TEMPLATE_TEXT, "t"),
                token(JspTokenTypes.SCRIPTLET_DECLARATION, "<%! int n=1; %>"),
                token(JspTokenTypes.TEMPLATE_TEXT, "t"),
                token(JspTokenTypes.SCRIPTLET_EXPRESSION, "<%= n %>"),
                token(JspTokenTypes.TEMPLATE_TEXT, "t"),
                token(JspTokenTypes.SCRIPTLET, "<% n++; %>"),
                token(JspTokenTypes.TEMPLATE_TEXT, "t"),
                token(JspTokenTypes.EL_EXPRESSION, "${user.name}"),
                token(JspTokenTypes.TEMPLATE_TEXT, "t")
        ));
    }

    @Test
    void testJspxAndClassicJspBoundaries() {
        String source = "<root><jsp:directive.include file=\"/x.jspf\"/><%@ include file=\"/y.jspf\" %></root>";
        assertLexed(source, List.of(
                token(JspTokenTypes.TEMPLATE_TEXT, "<root>"),
                token(JspTokenTypes.DIRECTIVE, "<jsp:directive.include file=\"/x.jspf\"/>"),
                token(JspTokenTypes.DIRECTIVE, "<%@ include file=\"/y.jspf\" %>"),
                token(JspTokenTypes.TEMPLATE_TEXT, "</root>")
        ));
    }

    @Test
    void testPlainTemplateTextRemainsTemplateText() {
        String source = "<html><body><p>Hello world</p></body></html>";
        assertLexed(source, List.of(
                token(JspTokenTypes.TEMPLATE_TEXT, source)
        ));
    }

    @Test
    void testUnterminatedRegionProducesBadToken() {
        String source = "x<%-- never closes";
        assertLexed(source, List.of(
                token(JspTokenTypes.TEMPLATE_TEXT, "x"),
                token(JspTokenTypes.BAD_REGION, "<%-- never closes")
        ));
    }

    @Test
    void testJspxScriptingElementsBecomeScriptletTokens() {
        String source = "<jsp:scriptlet>int a = 1;</jsp:scriptlet><jsp:declaration>int b;</jsp:declaration>"
                + "<jsp:expression>a</jsp:expression ><jsp:include page=\"/a.jsp\"/>";
        assertLexed(source, List.of(
                token(JspTokenTypes.SCRIPTLET, "<jsp:scriptlet>int a = 1;</jsp:scriptlet>"),
                token(JspTokenTypes.SCRIPTLET_DECLARATION, "<jsp:declaration>int b;</jsp:declaration>"),
                token(JspTokenTypes.SCRIPTLET_EXPRESSION, "<jsp:expression>a</jsp:expression >"),
                token(JspTokenTypes.ACTION, "<jsp:include page=\"/a.jsp\"/>")
        ));
    }

    @Test
    void testLexerRespectsEndOffset() {
        String source = "ab<% x %>cd";
        Lexer lexer = new JspLexer();
        lexer.start(source, 0, 4, 0);
        assertEquals(JspTokenTypes.TEMPLATE_TEXT, lexer.getTokenType());
        assertEquals(2, lexer.getTokenEnd());
        lexer.advance();
        assertEquals(JspTokenTypes.BAD_REGION, lexer.getTokenType());
        assertEquals(4, lexer.getTokenEnd());
    }

    @Test
    void testRestartPreservesScriptletSubtypeIdentity() {
        String source = "x<%! int n=1; %>y<%= n %>z<% n++; %>";
        List<TokenSnapshot> snapshots = lexWithState(source);

        assertRestartMatches(source, snapshots, JspTokenTypes.SCRIPTLET_DECLARATION);
        assertRestartMatches(source, snapshots, JspTokenTypes.SCRIPTLET_EXPRESSION);
        assertRestartMatches(source, snapshots, JspTokenTypes.SCRIPTLET);
    }

    private static void assertLexed(String source, List<ExpectedToken> expected) {
        Lexer lexer = new JspLexer();
        lexer.start(source);

        List<ExpectedToken> actual = new ArrayList<>();
        while (lexer.getTokenType() != null) {
            actual.add(token(
                    lexer.getTokenType(),
                    source.substring(lexer.getTokenStart(), lexer.getTokenEnd())
            ));
            lexer.advance();
        }
        assertEquals(expected, actual);
    }

    private static List<TokenSnapshot> lexWithState(String source) {
        Lexer lexer = new JspLexer();
        lexer.start(source);

        List<TokenSnapshot> snapshots = new ArrayList<>();
        while (lexer.getTokenType() != null) {
            snapshots.add(new TokenSnapshot(
                    lexer.getTokenType(),
                    source.substring(lexer.getTokenStart(), lexer.getTokenEnd()),
                    lexer.getTokenStart(),
                    lexer.getState()
            ));
            lexer.advance();
        }
        return snapshots;
    }

    private static void assertRestartMatches(String source, List<TokenSnapshot> snapshots, IElementType tokenType) {
        TokenSnapshot expected = snapshots.stream()
                .filter(snapshot -> snapshot.tokenType().equals(tokenType))
                .findFirst()
                .orElseThrow();

        Lexer restarted = new JspLexer();
        restarted.start(source, expected.startOffset(), source.length(), expected.state());

        assertEquals(expected.tokenType(), restarted.getTokenType());
        assertEquals(expected.text(), source.substring(restarted.getTokenStart(), restarted.getTokenEnd()));
    }

    private static ExpectedToken token(IElementType tokenType, String text) {
        return new ExpectedToken(tokenType, text);
    }

    private record ExpectedToken(IElementType tokenType, String text) {
    }

    private record TokenSnapshot(IElementType tokenType, String text, int startOffset, int state) {
    }
}
