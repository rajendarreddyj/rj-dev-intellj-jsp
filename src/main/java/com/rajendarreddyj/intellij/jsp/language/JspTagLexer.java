package com.rajendarreddyj.intellij.jsp.language;

import com.intellij.lexer.LexerBase;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * Splits one JSP region token (directive, action, scriptlet, expression, EL) into delimiters, tag name, attributes
 * and body. The lexer state records the mode so highlighting can restart at any token boundary.
 */
public final class JspTagLexer extends LexerBase {
    private static final int START = 0;
    private static final int DIRECTIVE_NAME = 1;
    private static final int ACTION_NAME = 2;
    private static final int IN_TAG = 3;
    private static final int IN_BODY_TAG = 4;
    private static final int JAVA_BODY = 5;
    private static final int EL_BODY_STATE = 6;
    private static final int XML_BODY = 7;

    private CharSequence buffer = "";
    private int endOffset;
    private int state;
    private int tokenStart;
    private int tokenEnd;
    private IElementType tokenType;
    private int nextState;

    @Override
    public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
        this.buffer = buffer;
        this.endOffset = endOffset;
        this.state = initialState >= START && initialState <= XML_BODY ? initialState : START;
        locateAt(startOffset);
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
        state = nextState;
        locateAt(tokenEnd);
    }

    private void locateAt(int position) {
        tokenStart = position;
        if (position >= endOffset) {
            tokenType = null;
            tokenEnd = endOffset;
            return;
        }
        locate(position);
    }

    private void locate(int position) {
        nextState = state;
        char c = buffer.charAt(position);
        switch (state) {
            case START -> locateStart(position);
            case DIRECTIVE_NAME, ACTION_NAME -> locateName(position, c);
            case IN_TAG, IN_BODY_TAG -> locateInTag(position, c);
            case JAVA_BODY -> locateBody(position, "%>", JspHighlightTokens.BODY, START);
            case EL_BODY_STATE -> locateBody(position, "}", JspHighlightTokens.EL_BODY, START);
            case XML_BODY -> locateBody(position, "</jsp:", JspHighlightTokens.BODY, START);
            default -> single(position, JspHighlightTokens.BODY, START);
        }
    }

    private void locateStart(int position) {
        if (startsWith(position, "<%@")) {
            emit(position + 3, JspHighlightTokens.DELIMITER, DIRECTIVE_NAME);
        } else if (startsWith(position, "<%!") || startsWith(position, "<%=")) {
            emit(position + 3, JspHighlightTokens.DELIMITER, JAVA_BODY);
        } else if (startsWith(position, "<%")) {
            emit(position + 2, JspHighlightTokens.DELIMITER, JAVA_BODY);
        } else if (startsWith(position, "${") || startsWith(position, "#{")) {
            emit(position + 2, JspHighlightTokens.DELIMITER, EL_BODY_STATE);
        } else if (startsWith(position, "</jsp:")) {
            emit(position + 2, JspHighlightTokens.DELIMITER, ACTION_NAME);
        } else if (startsWith(position, "<jsp:")) {
            emit(position + 1, JspHighlightTokens.DELIMITER, ACTION_NAME);
        } else {
            emit(endOffset, JspHighlightTokens.BODY, START);
        }
    }

    private void locateName(int position, char c) {
        if (Character.isWhitespace(c)) {
            emit(whitespaceEnd(position), TokenType.WHITE_SPACE, state);
            return;
        }
        int end = position;
        while (end < endOffset && isNameChar(buffer.charAt(end))) {
            end++;
        }
        if (end == position) {
            end = position + 1;
        }
        boolean xmlBodyElement = state == ACTION_NAME && isScriptingElement(position, end);
        emit(end, JspHighlightTokens.TAG_NAME, xmlBodyElement ? IN_BODY_TAG : IN_TAG);
    }

    private void locateInTag(int position, char c) {
        if (Character.isWhitespace(c)) {
            emit(whitespaceEnd(position), TokenType.WHITE_SPACE, state);
        } else if (startsWith(position, "%>")) {
            emit(position + 2, JspHighlightTokens.DELIMITER, START);
        } else if (startsWith(position, "/>")) {
            emit(position + 2, JspHighlightTokens.DELIMITER, START);
        } else if (c == '>') {
            emit(position + 1, JspHighlightTokens.DELIMITER, state == IN_BODY_TAG ? XML_BODY : START);
        } else if (c == '=') {
            emit(position + 1, JspHighlightTokens.ATTR_EQ, state);
        } else if (c == '"' || c == '\'') {
            int end = position + 1;
            while (end < endOffset && buffer.charAt(end) != c) {
                end++;
            }
            emit(Math.min(end + 1, endOffset), JspHighlightTokens.ATTR_VALUE, state);
        } else {
            int end = position;
            while (end < endOffset && isNameChar(buffer.charAt(end))) {
                end++;
            }
            if (end == position) {
                end = position + 1;
            }
            emit(end, JspHighlightTokens.ATTR_NAME, state);
        }
    }

    private void locateBody(int position, String terminator, IElementType bodyType, int afterState) {
        if (startsWith(position, terminator)) {
            if (terminator.equals("</jsp:")) {
                // closing action tag: continue through the regular start handling
                nextState = START;
                locateStart(position);
                return;
            }
            emit(position + terminator.length(), JspHighlightTokens.DELIMITER, afterState);
            return;
        }
        int end = position;
        while (end < endOffset && !startsWith(end, terminator)) {
            end++;
        }
        emit(end, bodyType, state);
    }

    private void single(int position, IElementType type, int afterState) {
        emit(position + 1, type, afterState);
    }

    private void emit(int end, IElementType type, int afterState) {
        tokenType = type;
        tokenEnd = Math.min(end, endOffset);
        nextState = afterState;
    }

    private boolean isScriptingElement(int start, int end) {
        String name = buffer.subSequence(start, end).toString();
        return name.equals("jsp:scriptlet") || name.equals("jsp:declaration") || name.equals("jsp:expression");
    }

    private int whitespaceEnd(int position) {
        int end = position;
        while (end < endOffset && Character.isWhitespace(buffer.charAt(end))) {
            end++;
        }
        return end;
    }

    private boolean startsWith(int position, String text) {
        int length = text.length();
        if (position + length > endOffset) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            if (buffer.charAt(position + i) != text.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isNameChar(char c) {
        return !Character.isWhitespace(c) && c != '=' && c != '>' && c != '"' && c != '\'' && c != '/' && c != '%';
    }

    @Override
    public @NotNull CharSequence getBufferSequence() {
        return buffer;
    }

    @Override
    public int getBufferEnd() {
        return endOffset;
    }
}
