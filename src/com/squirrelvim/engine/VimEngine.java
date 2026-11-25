package com.squirrelvim.engine;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * Stateful, editor-agnostic MVP Vim engine. Key tokens are printable
 * characters or named tokens such as {@code <Esc>}, {@code <Enter>},
 * {@code <C-r>}, {@code <C-d>}, and {@code <C-u>}.
 */
public final class VimEngine {
    private final VimEditor editor;
    private VimMode mode = VimMode.NORMAL;
    private Register register = Register.EMPTY;
    private int visualAnchor;
    // The Swing caret moves to the end of a selection. Keep Visual mode's
    // logical cursor separately so expanding a linewise selection upward is
    // not affected by that implementation detail.
    private int visualCursor;
    private int count;
    private int operatorCount = 1;
    private Character pendingOperator;
    private String pending;
    private int findCount;
    private String search = "";
    private boolean searchForward = true;
    private boolean searchWrap = true;

    public VimEngine(VimEditor editor) { this.editor = Objects.requireNonNull(editor); normalizeCaret(); }
    public VimMode getMode() { return mode; }
    public Register getRegister() { return register; }
    public boolean isWaitingForFindCharacter() { return "f".equals(pending); }
    public void setSearchWrap(boolean searchWrap) { this.searchWrap = searchWrap; }

    public void key(String key) {
        if ("<Esc>".equals(key)) { escape(); return; }
        if (mode == VimMode.INSERT) return;
        if (mode == VimMode.SEARCH_FORWARD || mode == VimMode.SEARCH_BACKWARD) { searchKey(key); return; }
        if (mode == VimMode.VISUAL || mode == VimMode.VISUAL_LINE) { visualKey(key); return; }
        normalKey(key);
    }

    private void normalKey(String key) {
        if (isWaitingForFindCharacter()) {
            pending = null;
            if (key.length() == 1 && !Character.isISOControl(key.charAt(0))) {
                findCharacterOnLine(key.charAt(0), findCount);
            }
            return;
        }
        if (key.length() == 1 && Character.isDigit(key.charAt(0)) && !(key.equals("0") && count == 0 && pendingOperator == null)) {
            count = count * 10 + (key.charAt(0) - '0'); return;
        }
        int rawCount = count;
        int n = takeCount();
        if (pending != null) {
            String was = pending; pending = null;
            if ("g".equals(was) && "g".equals(key)) { setCaret(0); return; }
            if ("r".equals(was) && key.length() == 1) { replaceChar(key); return; }
            if (was.startsWith("text-object:")) { applyTextObject(was, key); return; }
            reset(); return;
        }
        if (pendingOperator != null) { operatorKey(key, n); return; }
        switch (key) {
            case "i" -> insertAt(caret()); case "I" -> insertAt(firstNonBlank(lineStart(caret())));
            case "a" -> insertAt(Math.min(length(), caret() + 1)); case "A" -> insertAt(lineEnd(caret()));
            case "o" -> openLine(true); case "O" -> openLine(false);
            case "v" -> startVisual(false); case "V" -> startVisual(true);
            case "d", "c", "y" -> { pendingOperator = key.charAt(0); operatorCount = n; }
            case "h", "j", "k", "l", "w", "b", "e", "0", "^", "$", "{", "}", "<C-d>", "<C-u>" -> move(key, n);
            case "g", "r" -> pending = key;
            case "f" -> { pending = key; findCount = n; }
            case "G" -> gotoLine(rawCount == 0 ? lineCount() : rawCount);
            case "x" -> deleteRange(caret(), Math.min(length(), caret() + n), false);
            case "D" -> deleteRange(caret(), lineEnd(caret()), false);
            case "C" -> { changeRange(caret(), lineEnd(caret()), false); }
            case "Y" -> yankRange(lineStart(caret()), lineEndWithNewline(caret()), true);
            case "p" -> paste(false); case "P" -> paste(true);
            case "u" -> {
                for (int i = 0; i < n; i++) editor.undo();
                normalizeCaret();
            }
            case "<C-r>" -> { editor.redo(); normalizeCaret(); }
            case "J" -> join(n); case "/" -> startSearch(true); case "?" -> startSearch(false);
            case "n" -> repeatSearch(false); case "N" -> repeatSearch(true);
            default -> reset();
        }
    }

    private void operatorKey(String key, int n) {
        char op = pendingOperator; pendingOperator = null;
        n *= operatorCount;
        operatorCount = 1;
        int start = caret(), end;
        if (key.equals(String.valueOf(op))) { start = lineStart(start); end = start; for (int i = 0; i < n; i++) end = lineEndWithNewline(end); applyOperator(op, start, end, true); return; }
        if (key.equals("D") && op == 'd' || key.equals("C") && op == 'c') { end = lineEnd(start); applyOperator(op, start, end, false); return; }
        if (key.equals("i")) { pending = "text-object:" + op + ":" + n; return; }
        end = motionTarget(key, n);
        if (end < 0) { reset(); return; }
        if (key.equals("w")) end = Math.max(start, end);
        else if (key.equals("$")) end = lineEnd(start);
        else if (end >= start && key.equals("e")) end = Math.min(length(), end + 1);
        int lo = Math.min(start, end), hi = Math.max(start, end);
        applyOperator(op, lo, hi, false);
    }

    private void applyTextObject(String command, String key) {
        String[] parts = command.split(":");
        char op = parts[1].charAt(0);
        int n = Integer.parseInt(parts[2]);
        int[] range = switch (key) {
            case "w" -> innerWordRange(caret(), n);
            case "'", "\"" -> n == 1 ? innerQuoteRange(caret(), key.charAt(0)) : null;
            case "(", ")", "b" -> innerBracketRange(caret(), '(', ')', n);
            case "[", "]" -> innerBracketRange(caret(), '[', ']', n);
            case "{", "}", "B" -> innerBracketRange(caret(), '{', '}', n);
            default -> null;
        };
        if (range != null) applyOperator(op, range[0], range[1], false);
        else reset();
    }

    private void applyOperator(char op, int start, int end, boolean lineWise) {
        if (op == 'y') { yankRange(start, end, lineWise); return; }
        if (op == 'd') deleteRange(start, end, lineWise);
        if (op == 'c') changeRange(start, end, lineWise);
    }

    private void visualKey(String key) {
        if ("i".equals(pending)) {
            pending = null;
            if ("w".equals(key)) selectInnerWord();
            return;
        }
        if (mode == VimMode.VISUAL && "i".equals(key)) {
            pending = key;
            return;
        }
        if ("d".equals(key) || "c".equals(key) || "y".equals(key)) {
            int a = visualAnchor, b = visualCursor;
            int start = Math.min(a, b), end = Math.max(a, b);
            boolean lines = mode == VimMode.VISUAL_LINE;
            if (lines) { start = lineStart(start); end = lineEndWithNewline(end); }
            if ("y".equals(key)) yankRange(start, end + (!lines && end < length() ? 1 : 0), lines);
            else if ("d".equals(key)) deleteRange(start, end + (!lines && end < length() ? 1 : 0), lines);
            else changeRange(start, end + (!lines && end < length() ? 1 : 0), lines);
            return;
        }
        if ("v".equals(key) && mode == VimMode.VISUAL) { escape(); return; }
        if ("V".equals(key)) { mode = VimMode.VISUAL_LINE; selectVisual(); return; }
        if (isMotion(key)) { visualCursor = motionTarget(visualCursor, key, takeCount()); selectVisual(); }
    }

    private void selectInnerWord() {
        String text = editor.getText();
        int endOfLine = lineEnd(visualCursor);
        if (visualCursor >= endOfLine) return;

        int startOfLine = lineStart(visualCursor);
        WordKind kind = wordKind(text.charAt(visualCursor));
        int start = visualCursor;
        int end = visualCursor;
        while (start > startOfLine && wordKind(text.charAt(start - 1)) == kind) start--;
        while (end + 1 < endOfLine && wordKind(text.charAt(end + 1)) == kind) end++;

        visualAnchor = start;
        visualCursor = end;
        selectVisual();
    }

    private enum WordKind { WORD, WHITESPACE, PUNCTUATION }

    private WordKind wordKind(char character) {
        if (word(character)) return WordKind.WORD;
        if (Character.isWhitespace(character)) return WordKind.WHITESPACE;
        return WordKind.PUNCTUATION;
    }

    private void startVisual(boolean lines) { visualAnchor = caret(); visualCursor = visualAnchor; mode = lines ? VimMode.VISUAL_LINE : VimMode.VISUAL; selectVisual(); }
    private void selectVisual() { int a = visualAnchor, b = visualCursor; if (mode == VimMode.VISUAL_LINE) editor.select(lineStart(Math.min(a,b)), lineEndWithNewline(Math.max(a,b))); else editor.select(Math.min(a,b), Math.min(length(), Math.max(a,b) + 1)); }
    private void insertAt(int at) { setCaret(at); mode = VimMode.INSERT; reset(); }
    private void openLine(boolean below) {
        int p = below ? lineEnd(caret()) : lineStart(caret());
        startInsertEdit(() -> {
            editor.replace(p, p, "\n");
            setCaret(below ? p + 1 : p);
            reset();
        });
    }
    private void changeRange(int start, int end, boolean lineWise) {
        if (end <= start) {
            startInsertEdit(() -> { editor.setCaretOffset(start); reset(); });
            return;
        }
        startInsertEdit(() -> deleteRange(start, end, lineWise));
    }

    private void startInsertEdit(Runnable edit) {
        editor.beginCompoundEdit();
        try {
            edit.run();
            mode = VimMode.INSERT;
        } finally {
            if (mode != VimMode.INSERT) editor.endCompoundEdit();
        }
    }

    public void finishInsertEdit() {
        if (mode == VimMode.INSERT) editor.endCompoundEdit();
    }
    private void deleteRange(int start, int end, boolean lineWise) { if (end <= start) return; register = new Register(text(start,end), lineWise); editor.replace(start,end,""); setCaret(Math.min(start, Math.max(0,length()-1))); mode=VimMode.NORMAL; editor.select(caret(),caret()); reset(); normalizeCaret(); }
    private void yankRange(int start, int end, boolean lineWise) { register = new Register(text(start,end), lineWise); escape(); }
    private void paste(boolean before) { if (register.text().isEmpty()) return; int p = caret(); if (register.lineWise()) { p = before ? lineStart(p) : lineEndWithNewline(p); editor.replace(p,p,register.text()); setCaret(p); } else { p = before ? p : Math.min(length(),p+1); editor.replace(p,p,register.text()); setCaret(p + register.text().length()-1); } }
    private void replaceChar(String value) { if (length() > 0) { int p=caret(); editor.replace(p, Math.min(length(),p+1),value); setCaret(p); } }
    private void join(int n) {
        if (lineEnd(caret()) >= length()) return;
        editor.beginCompoundEdit();
        try {
            for (int i = 0; i < n; i++) {
                int end = lineEnd(caret());
                if (end >= length()) break;
                int next = end + 1;
                while (next < length() && Character.isWhitespace(charAt(next)) && charAt(next) != '\n') next++;
                editor.replace(end, next, " ");
            }
        } finally {
            editor.endCompoundEdit();
        }
    }

    private void move(String key, int n) { setCaret(motionTarget(key,n)); }
    private int motionTarget(String key, int n) { return motionTarget(caret(), key, n); }
    private int motionTarget(int p, String key, int n) {
        return switch(key) {
            case "h" -> Math.max(0,p-n); case "l" -> Math.min(Math.max(0,length()-1),p+n);
            case "j" -> vertical(p,n); case "k" -> vertical(p,-n);
            case "w" -> wordsForward(p,n); case "b" -> wordsBack(p,n); case "e" -> wordEnd(p,n);
            case "0" -> lineStart(p); case "^" -> firstNonBlank(lineStart(p)); case "$" -> Math.max(lineStart(p),lineEnd(p)-1);
            case "{" -> paragraph(p,-n); case "}" -> paragraph(p,n);
            case "<C-d>" -> vertical(p, Math.max(1,lineCount()/2)*n); case "<C-u>" -> vertical(p,-Math.max(1,lineCount()/2)*n);
            default -> -1;
        };
    }
    private void findCharacterOnLine(char character, int occurrences) {
        String text = editor.getText();
        int start = caret();
        int end = lineEnd(start);
        for (int i = start + 1; i < end; i++) {
            if (text.charAt(i) == character && --occurrences == 0) {
                setCaret(i);
                return;
            }
        }
    }

    private void gotoLine(int n) { if(n<=1) setCaret(0); else { int p=0; for(int i=1;i<n && p<length();i++) p=nextLine(p); setCaret(p); } }
    private void startSearch(boolean forward) { mode = forward ? VimMode.SEARCH_FORWARD : VimMode.SEARCH_BACKWARD; searchForward=forward; search=""; }
    private void searchKey(String key) { if("<Enter>".equals(key)) { mode=VimMode.NORMAL; find(search,searchForward); } else if(key.length()==1) search += key; }
    private void repeatSearch(boolean reverse) { if(!search.isEmpty()) find(search, reverse ? !searchForward : searchForward); }
    private void find(String pattern, boolean forward) { if(pattern.isEmpty()) return; String t=editor.getText(); int from=caret(); int hit=forward?t.indexOf(pattern,Math.min(t.length(),from+1)):t.lastIndexOf(pattern,Math.max(0,from-1)); if(hit<0&&searchWrap) hit=forward?t.indexOf(pattern):t.lastIndexOf(pattern); if(hit>=0) setCaret(hit); }
    private void escape() { finishInsertEdit(); if (mode == VimMode.VISUAL || mode == VimMode.VISUAL_LINE) setCaret(visualCursor); mode=VimMode.NORMAL; editor.select(caret(),caret()); reset(); normalizeCaret(); }
    private void reset() { count=0; pendingOperator=null; operatorCount=1; pending=null; }
    private int takeCount() { int n=count==0?1:count; count=0; return n; }
    private boolean isMotion(String key) { return "h j k l w b e 0 ^ $ { } <C-d> <C-u>".contains(key); }
    private int caret() { return Math.max(0,Math.min(Math.max(0,length()-1),editor.getCaretOffset())); }
    private int length() { return editor.getText().length(); }
    private String text(int s,int e) { return editor.getText().substring(Math.max(0,s),Math.max(0,Math.min(length(),e))); }
    private char charAt(int p) { return editor.getText().charAt(p); }
    private void setCaret(int p) { editor.setCaretOffset(Math.max(0,Math.min(Math.max(0,length()-1),p))); }
    private void normalizeCaret() { setCaret(editor.getCaretOffset()); }
    private int lineStart(int p) { String t=editor.getText(); int i=Math.min(p,t.length()); while(i>0&&t.charAt(i-1)!='\n')i--; return i; }
    private int lineEnd(int p) { String t=editor.getText(); int i=Math.min(p,t.length()); while(i<t.length()&&t.charAt(i)!='\n')i++; return i; }
    private int lineEndWithNewline(int p) { int e=lineEnd(p); return e<length()?e+1:e; }
    private int nextLine(int p) { int e=lineEnd(p); return e<length()?e+1:e; }
    private int firstNonBlank(int p) { while(p<lineEnd(p)&&Character.isWhitespace(charAt(p)))p++; return p; }
    private int vertical(int p,int lines) { int col=p-lineStart(p); while(lines>0){int n=nextLine(p);if(n==p)break;p=n;lines--;} while(lines<0){int s=lineStart(p);if(s==0)break;p=lineStart(s-1);lines++;} return Math.min(lineEnd(p)-1<lineStart(p)?lineStart(p):lineEnd(p)-1,lineStart(p)+col); }
    private int lineCount(){int n=1;for(char c:editor.getText().toCharArray())if(c=='\n')n++;return n;}
    private boolean word(char c){return Character.isLetterOrDigit(c)||c=='_';}
    private int wordsForward(int p,int n){String t=editor.getText();for(int k=0;k<n;k++){while(p<t.length()&&word(t.charAt(p)))p++;while(p<t.length()&&!word(t.charAt(p)))p++;}return Math.min(Math.max(0,t.length()-1),p);}
    private int wordsBack(int p,int n){String t=editor.getText();for(int k=0;k<n;k++){p=Math.max(0,p-1);while(p>0&&!word(t.charAt(p)))p--;while(p>0&&word(t.charAt(p-1)))p--;}return p;}
    private int wordEnd(int p,int n){String t=editor.getText();for(int k=0;k<n;k++){while(p<t.length()&&!word(t.charAt(p)))p++;while(p<t.length()-1&&word(t.charAt(p+1)))p++;if(k<n-1)p++;}return Math.min(Math.max(0,t.length()-1),p);}
    /** Finds an enclosing or next quote pair on this line, with SQL doubled quotes. */
    private int[] innerQuoteRange(int position, char quote) {
        String text = editor.getText();
        int end = lineEnd(position);
        for (int i = lineStart(position); i < end; i++) {
            char ch = text.charAt(i);
            if (ch == '\\') { i++; continue; }
            if (ch != quote) continue;
            int closing = quoteEnd(text, i, end);
            if (closing < 0) return null;
            if (position <= closing) return new int[] { i + 1, closing };
            i = closing;
        }
        return null;
    }

    private int quoteEnd(String text, int opening, int limit) {
        char quote = text.charAt(opening);
        for (int i = opening + 1; i < limit; i++) {
            if (text.charAt(i) == '\\') { i++; continue; }
            if (text.charAt(i) != quote) continue;
            if (i + 1 < limit && text.charAt(i + 1) == quote) { i++; continue; }
            return i;
        }
        return -1;
    }

    /** Counts enclosing pairs from the innermost pair; pairs can span lines. */
    private int[] innerBracketRange(int position, char opening, char closing, int levels) {
        String text = editor.getText();
        Deque<Integer> openings = new ArrayDeque<>();
        int[] next = null;
        int lineEnd = lineEnd(position);
        boolean enclosed = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '-' && i + 1 < text.length() && text.charAt(i + 1) == '-') {
                int end = text.indexOf('\n', i + 2);
                if (end < 0) break;
                i = end;
            } else if (ch == '/' && i + 1 < text.length() && text.charAt(i + 1) == '*') {
                int end = text.indexOf("*/", i + 2);
                if (end < 0) break;
                i = end + 1;
            } else if (ch == '\'' || ch == '"') {
                int end = quoteEnd(text, i, text.length());
                if (end < 0) break;
                i = end;
            } else if (ch == opening) {
                openings.push(i);
            } else if (ch == closing && !openings.isEmpty()) {
                int start = openings.pop();
                if (start <= position && position <= i) {
                    enclosed = true;
                    if (--levels == 0) return new int[] { start + 1, i };
                } else if (start > position && start < lineEnd &&
                           (next == null || start < next[0] - 1)) {
                    next = new int[] { start + 1, i };
                }
            }
        }
        return !enclosed && levels == 1 ? next : null;
    }

    /** Returns a characterwise inner-word range; whitespace is excluded. */
    private int[] innerWordRange(int p, int n) {
        String t = editor.getText();
        if (t.isEmpty()) return null;
        p = Math.max(0, Math.min(t.length() - 1, p));
        while (p < t.length() && !word(t.charAt(p))) p++;
        if (p == t.length()) return null;
        int start = p;
        while (start > 0 && word(t.charAt(start - 1))) start--;
        int end = p;
        for (int i = 0; i < n; i++) {
            while (end < t.length() && word(t.charAt(end))) end++;
            if (i < n - 1) while (end < t.length() && !word(t.charAt(end))) end++;
        }
        return new int[] { start, end };
    }
    private int paragraph(int p,int direction){for(int k=0;k<Math.abs(direction);k++){if(direction>0){p=nextLine(p);while(p<length()&&lineStart(p)!=lineEnd(p))p=nextLine(p);while(p<length()&&lineStart(p)==lineEnd(p))p=nextLine(p);}else{p=lineStart(p);if(p>0)p=lineStart(p-1);while(p>0&&lineStart(p)!=lineEnd(p))p=lineStart(p-1);}}return p;}
}
