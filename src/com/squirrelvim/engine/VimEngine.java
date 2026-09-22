package com.squirrelvim.engine;

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
    private int count;
    private int operatorCount = 1;
    private Character pendingOperator;
    private String pending;
    private String search = "";
    private boolean searchForward = true;
    private boolean searchWrap = true;

    public VimEngine(VimEditor editor) { this.editor = Objects.requireNonNull(editor); normalizeCaret(); }
    public VimMode getMode() { return mode; }
    public Register getRegister() { return register; }
    public void setSearchWrap(boolean searchWrap) { this.searchWrap = searchWrap; }

    public void key(String key) {
        if ("<Esc>".equals(key)) { escape(); return; }
        if (mode == VimMode.INSERT) return;
        if (mode == VimMode.SEARCH_FORWARD || mode == VimMode.SEARCH_BACKWARD) { searchKey(key); return; }
        if (mode == VimMode.VISUAL || mode == VimMode.VISUAL_LINE) { visualKey(key); return; }
        normalKey(key);
    }

    private void normalKey(String key) {
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
            case "G" -> gotoLine(rawCount == 0 ? lineCount() : rawCount);
            case "x" -> deleteRange(caret(), Math.min(length(), caret() + n), false);
            case "D" -> deleteRange(caret(), lineEnd(caret()), false);
            case "C" -> { changeRange(caret(), lineEnd(caret()), false); }
            case "Y" -> yankRange(lineStart(caret()), lineEndWithNewline(caret()), true);
            case "p" -> paste(false); case "P" -> paste(true);
            case "u" -> editor.undo(); case "<C-r>" -> editor.redo();
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
        if (!"w".equals(key)) { reset(); return; }
        char op = parts[1].charAt(0);
        int n = Integer.parseInt(parts[2]);
        int[] range = innerWordRange(caret(), n);
        if (range != null) applyOperator(op, range[0], range[1], false);
        else reset();
    }

    private void applyOperator(char op, int start, int end, boolean lineWise) {
        if (op == 'y') { yankRange(start, end, lineWise); return; }
        if (op == 'd') deleteRange(start, end, lineWise);
        if (op == 'c') changeRange(start, end, lineWise);
    }

    private void visualKey(String key) {
        if ("d".equals(key) || "c".equals(key) || "y".equals(key)) {
            int a = visualAnchor, b = caret();
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
        if (isMotion(key)) { move(key, takeCount()); selectVisual(); }
    }

    private void startVisual(boolean lines) { visualAnchor = caret(); mode = lines ? VimMode.VISUAL_LINE : VimMode.VISUAL; selectVisual(); }
    private void selectVisual() { int a = visualAnchor, b = caret(); if (mode == VimMode.VISUAL_LINE) editor.select(lineStart(Math.min(a,b)), lineEndWithNewline(Math.max(a,b))); else editor.select(Math.min(a,b), Math.min(length(), Math.max(a,b) + 1)); }
    private void insertAt(int at) { setCaret(at); mode = VimMode.INSERT; reset(); }
    private void openLine(boolean below) { int p = below ? lineEnd(caret()) : lineStart(caret()); editor.replace(p,p,"\n"); setCaret(below ? p + 1 : p); mode = VimMode.INSERT; reset(); }
    private void changeRange(int start, int end, boolean lineWise) { deleteRange(start,end,lineWise); mode = VimMode.INSERT; }
    private void deleteRange(int start, int end, boolean lineWise) { if (end <= start) return; register = new Register(text(start,end), lineWise); editor.replace(start,end,""); setCaret(Math.min(start, Math.max(0,length()-1))); escape(); }
    private void yankRange(int start, int end, boolean lineWise) { register = new Register(text(start,end), lineWise); escape(); }
    private void paste(boolean before) { if (register.text().isEmpty()) return; int p = caret(); if (register.lineWise()) { p = before ? lineStart(p) : lineEndWithNewline(p); editor.replace(p,p,register.text()); setCaret(p); } else { p = before ? p : Math.min(length(),p+1); editor.replace(p,p,register.text()); setCaret(p + register.text().length()-1); } }
    private void replaceChar(String value) { if (length() > 0) { int p=caret(); editor.replace(p, Math.min(length(),p+1),value); setCaret(p); } }
    private void join(int n) { for(int i=0;i<n;i++) { int e=lineEnd(caret()); if(e>=length()) break; int next=e+1; while(next<length() && Character.isWhitespace(charAt(next)) && charAt(next)!='\n') next++; editor.replace(e,next," "); } }

    private void move(String key, int n) { setCaret(motionTarget(key,n)); }
    private int motionTarget(String key, int n) {
        int p=caret();
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
    private void gotoLine(int n) { if(n<=1) setCaret(0); else { int p=0; for(int i=1;i<n && p<length();i++) p=nextLine(p); setCaret(p); } }
    private void startSearch(boolean forward) { mode = forward ? VimMode.SEARCH_FORWARD : VimMode.SEARCH_BACKWARD; searchForward=forward; search=""; }
    private void searchKey(String key) { if("<Enter>".equals(key)) { mode=VimMode.NORMAL; find(search,searchForward); } else if(key.length()==1) search += key; }
    private void repeatSearch(boolean reverse) { if(!search.isEmpty()) find(search, reverse ? !searchForward : searchForward); }
    private void find(String pattern, boolean forward) { if(pattern.isEmpty()) return; String t=editor.getText(); int from=caret(); int hit=forward?t.indexOf(pattern,Math.min(t.length(),from+1)):t.lastIndexOf(pattern,Math.max(0,from-1)); if(hit<0&&searchWrap) hit=forward?t.indexOf(pattern):t.lastIndexOf(pattern); if(hit>=0) setCaret(hit); }
    private void escape() { mode=VimMode.NORMAL; editor.select(caret(),caret()); reset(); normalizeCaret(); }
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
