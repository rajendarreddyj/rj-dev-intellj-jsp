package com.rajendarreddyj.intellij.jsp.language;

import com.intellij.lexer.LexerBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public final class JspLexer extends LexerBase {
    private static final int STATE_TEMPLATE = 0;
    private static final int STATE_DIRECTIVE = 1;
    private static final int STATE_ACTION = 2;
    private static final int STATE_COMMENT = 3;
    private static final int STATE_SCRIPTLET_STATEMENT = 4;
    private static final int STATE_SCRIPTLET_DECLARATION = 5;
    private static final int STATE_SCRIPTLET_EXPRESSION = 6;
    private static final int STATE_EL = 7;
    private static final int STATE_BAD = 8;

    private CharSequence buffer = "";
    private int endOffset;
    private int position;

    private int state = STATE_TEMPLATE;
    private IElementType tokenType;
    private int tokenStart;
    private int tokenEnd;

    @Override
    public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
        this.buffer = buffer;
        this.position = startOffset;
        this.endOffset = endOffset;
        this.state = isKnownState(initialState) ? initialState : STATE_TEMPLATE;
        locateNextToken();
    }

    @Override
    public int getState() {
        return state;
    }

    @Override
    public IElementType getTokenType() {
        return tokenType;
    }

    @Override
    public int getTokenStart() {
        return tokenStart;
    }

    @Override
    public int getTokenEnd() {
        return tokenEnd;
    }

    @Override
    public void advance() {
        if (tokenType == null) {
            return;
        }
        position = tokenEnd;
        state = STATE_TEMPLATE;
        locateNextToken();
    }

    @Override
    public @NotNull CharSequence getBufferSequence() {
        return buffer;
    }

    @Override
    public int getBufferEnd() {
        return endOffset;
    }

    private void locateNextToken() {
        if (position >= endOffset) {
            tokenType = null;
            tokenStart = tokenEnd = endOffset;
            return;
        }

        tokenStart = position;
        switch (state) {
            case STATE_DIRECTIVE -> lexUntil("%>", JspTokenTypes.DIRECTIVE, STATE_DIRECTIVE);
            case STATE_COMMENT -> lexUntil("--%>", JspTokenTypes.COMMENT, STATE_COMMENT);
            case STATE_SCRIPTLET_STATEMENT -> lexUntil("%>", JspTokenTypes.SCRIPTLET, STATE_SCRIPTLET_STATEMENT);
            case STATE_SCRIPTLET_DECLARATION -> lexUntil("%>", JspTokenTypes.SCRIPTLET_DECLARATION, STATE_SCRIPTLET_DECLARATION);
            case STATE_SCRIPTLET_EXPRESSION -> lexUntil("%>", JspTokenTypes.SCRIPTLET_EXPRESSION, STATE_SCRIPTLET_EXPRESSION);
            case STATE_EL -> lexUntil("}", JspTokenTypes.EL_EXPRESSION, STATE_EL);
            case STATE_ACTION -> lexJspActionTag();
            case STATE_BAD -> lexBadToEnd();
            case STATE_TEMPLATE -> lexTemplate();
            default -> lexTemplate();
        }
    }

    private void lexTemplate() {
        int boundary = nextBoundary(position);
        if (boundary == -1) {
            tokenType = JspTokenTypes.TEMPLATE_TEXT;
            tokenEnd = endOffset;
            state = STATE_TEMPLATE;
            return;
        }
        if (boundary > position) {
            tokenType = JspTokenTypes.TEMPLATE_TEXT;
            tokenEnd = boundary;
            state = STATE_TEMPLATE;
            return;
        }

        if (startsWith(position, "<%--")) {
            lexDelimited("--%>", JspTokenTypes.COMMENT, STATE_COMMENT);
        } else if (startsWith(position, "<%@")) {
            lexDelimited("%>", JspTokenTypes.DIRECTIVE, STATE_DIRECTIVE);
        } else if (startsWith(position, "<%!")) {
            lexDelimited("%>", JspTokenTypes.SCRIPTLET_DECLARATION, STATE_SCRIPTLET_DECLARATION);
        } else if (startsWith(position, "<%=")) {
            lexDelimited("%>", JspTokenTypes.SCRIPTLET_EXPRESSION, STATE_SCRIPTLET_EXPRESSION);
        } else if (startsWith(position, "<%")) {
            lexDelimited("%>", JspTokenTypes.SCRIPTLET, STATE_SCRIPTLET_STATEMENT);
        } else if (startsWith(position, "${") || startsWith(position, "#{")) {
            lexDelimited("}", JspTokenTypes.EL_EXPRESSION, STATE_EL);
        } else if (lexXmlScriptingElement("scriptlet", JspTokenTypes.SCRIPTLET)
                || lexXmlScriptingElement("declaration", JspTokenTypes.SCRIPTLET_DECLARATION)
                || lexXmlScriptingElement("expression", JspTokenTypes.SCRIPTLET_EXPRESSION)) {
            return;
        } else if (startsWith(position, "<jsp:") || startsWith(position, "</jsp:")) {
            lexJspActionTag();
        } else {
            tokenType = JspTokenTypes.TEMPLATE_TEXT;
            tokenEnd = position + 1;
            state = STATE_TEMPLATE;
        }
    }

    /**
     * JSPX scripting elements ({@code <jsp:scriptlet>...</jsp:scriptlet>}) become one scriptlet token so that Java
     * injection applies. They restart from STATE_TEMPLATE because the open tag is re-detected from the token start.
     */
    private boolean lexXmlScriptingElement(String name, IElementType type) {
        String open = "<jsp:" + name;
        if (!startsWith(position, open)) {
            return false;
        }
        int afterName = position + open.length();
        if (afterName >= endOffset) {
            return false;
        }
        char next = buffer.charAt(afterName);
        if (next != '>' && !Character.isWhitespace(next)) {
            return false;
        }
        int openEnd = findTagEnd(position);
        if (openEnd == -1 || buffer.charAt(openEnd - 1) == '/') {
            return false;
        }
        String close = "</jsp:" + name;
        int search = openEnd + 1;
        int closeEnd = -1;
        while (closeEnd == -1) {
            int closeStart = indexOf(close, search);
            if (closeStart == -1) {
                break;
            }
            int i = closeStart + close.length();
            while (i < endOffset && Character.isWhitespace(buffer.charAt(i))) {
                i++;
            }
            if (i < endOffset && buffer.charAt(i) == '>') {
                closeEnd = i + 1;
            } else {
                search = closeStart + close.length();
            }
        }
        if (closeEnd == -1) {
            tokenType = JspTokenTypes.BAD_REGION;
            tokenEnd = badRegionEnd(position + 1);
            state = STATE_BAD;
            return true;
        }
        tokenType = type;
        tokenEnd = closeEnd;
        state = STATE_TEMPLATE;
        return true;
    }

    private void lexJspActionTag() {
        int close = findTagEnd(position);
        if (close == -1) {
            tokenType = JspTokenTypes.BAD_REGION;
            tokenEnd = badRegionEnd(position + 1);
            state = STATE_BAD;
            return;
        }

        tokenEnd = close + 1;
        tokenType = startsWith(position, "<jsp:directive.") || startsWith(position, "</jsp:directive.")
                ? JspTokenTypes.DIRECTIVE
                : JspTokenTypes.ACTION;
        state = STATE_ACTION;
    }

    private void lexDelimited(String delimiter, IElementType type, int tokenState) {
        lexUntil(delimiter, type, tokenState);
    }

    private void lexUntil(String delimiter, IElementType completeType, int tokenState) {
        int close = indexOf(delimiter, position);
        if (close == -1) {
            tokenType = JspTokenTypes.BAD_REGION;
            tokenEnd = badRegionEnd(position + 1);
            state = STATE_BAD;
            return;
        }

        tokenType = completeType;
        tokenEnd = close + delimiter.length();
        state = tokenState;
    }

    private void lexBadToEnd() {
        tokenType = JspTokenTypes.BAD_REGION;
        tokenEnd = badRegionEnd(position + 1);
        state = STATE_BAD;
    }

    /** Single forward scan for the next JSP region start ({@code <%}, {@code ${}, {@code #{}, {@code <jsp:}, {@code </jsp:}). */
    private int nextBoundary(int from) {
        for (int i = from; i < endOffset; i++) {
            char c = buffer.charAt(i);
            if (c == '<') {
                if (startsWith(i, "<%") || startsWith(i, "<jsp:") || startsWith(i, "</jsp:")) {
                    return i;
                }
            } else if ((c == '$' || c == '#') && i + 1 < endOffset && buffer.charAt(i + 1) == '{') {
                return i;
            }
        }
        return -1;
    }

    private int indexOf(String needle, int from) {
        int last = endOffset - needle.length();
        char first = needle.charAt(0);
        for (int i = from; i <= last; i++) {
            if (buffer.charAt(i) == first && startsWith(i, needle)) {
                return i;
            }
        }
        return -1;
    }

    private int findTagEnd(int from) {
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        for (int i = from; i < endOffset; i++) {
            char c = buffer.charAt(i);
            if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
            } else if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
            } else if (c == '>' && !inSingleQuote && !inDoubleQuote) {
                return i;
            }
        }
        return -1;
    }

    private boolean startsWith(int index, String prefix) {
        if (index < 0 || index + prefix.length() > endOffset) {
            return false;
        }
        for (int i = 0; i < prefix.length(); i++) {
            if (buffer.charAt(index + i) != prefix.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isKnownState(int candidate) {
        return candidate >= STATE_TEMPLATE && candidate <= STATE_BAD;
    }

    private int badRegionEnd(int searchFrom) {
        int boundary = nextBoundary(Math.max(searchFrom, position + 1));
        if (boundary == -1 || boundary <= position) {
            return endOffset;
        }
        return boundary;
    }
}
