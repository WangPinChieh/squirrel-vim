package com.squirrelvim.engine;

/** Focused MVP command regression tests, run without SQuirreL. */
public final class VimEngineTest {
    public static void main(String[] args) {
        modesAndInsert(); motionsAndCounts(); operatorsAndRegisters(); textObjects(); singleQuoteObjects(); delimitedObjects(); visualInnerWord(); findCharacter(); visualAndSearch(); edgeCases();
    }
    private static void modesAndInsert() {
        Fake e = new Fake("select one"); VimEngine v = new VimEngine(e);
        v.key("i"); eq(VimMode.INSERT,v.getMode()); e.replace(e.getCaretOffset(),e.getCaretOffset(),"X"); v.key("<Esc>");
        eq("Xselect one",e.getText()); eq(VimMode.NORMAL,v.getMode());
    }
    private static void motionsAndCounts() {
        Fake e = new Fake("one two\nthree four\nfive"); VimEngine v=new VimEngine(e);
        v.key("2");v.key("w");eq(8,e.getCaretOffset()); v.key("3");v.key("j");eq(19,e.getCaretOffset());
        v.key("g");v.key("g");eq(0,e.getCaretOffset()); v.key("G");eq(19,e.getCaretOffset());
    }
    private static void operatorsAndRegisters() {
        Fake e=new Fake("one two\nthree\nfour"); VimEngine v=new VimEngine(e);
        v.key("d");v.key("w");eq("two\nthree\nfour",e.getText()); eq("one ",v.getRegister().text());
        v.key("2");v.key("d");v.key("d");eq("four",e.getText());eq(true,v.getRegister().lineWise());
        v.key("P");eq("two\nthree\nfour",e.getText());
    }
    private static void textObjects() {
        Fake e = new Fake("select customer_id from orders"); VimEngine v = new VimEngine(e);
        v.key("w"); v.key("d"); v.key("i"); v.key("w");
        eq("select  from orders", e.getText()); eq("customer_id", v.getRegister().text());

        e = new Fake("select customer_id from orders"); v = new VimEngine(e);
        v.key("w"); v.key("c"); v.key("i"); v.key("w");
        eq("select  from orders", e.getText()); eq(VimMode.INSERT, v.getMode());

        e = new Fake("one two three"); v = new VimEngine(e);
        v.key("2"); v.key("d"); v.key("i"); v.key("w");
        eq(" three", e.getText()); eq("one two", v.getRegister().text());
    }
    private static void singleQuoteObjects() {
        String sql = "select 'hello world', 'next'";
        for (int position : new int[] { 0, 7, 8, 12, 19 }) {
            Fake e = new Fake(sql); VimEngine v = new VimEngine(e);
            e.setCaretOffset(position);
            v.key("c"); v.key("i"); v.key("'");
            eq("select '', 'next'", e.getText());
            eq("hello world", v.getRegister().text()); eq(false, v.getRegister().lineWise());
            eq(VimMode.INSERT, v.getMode()); eq(8, e.getCaretOffset());
            e.replace(e.getCaretOffset(), e.getCaretOffset(), "new");
            v.key("<Esc>"); eq("select 'new', 'next'", e.getText());
        }
        Fake e = new Fake(sql); VimEngine v = new VimEngine(e);
        e.setCaretOffset(20);
        v.key("y"); v.key("i"); v.key("'");
        eq("next", v.getRegister().text()); eq(sql, e.getText());
        v.key("d"); v.key("i"); v.key("'");
        eq("select 'hello world', ''", e.getText()); eq(VimMode.NORMAL, v.getMode());

        for (int position : new int[] { 0, 7, 8 }) {
            e = new Fake("select ''"); v = new VimEngine(e); e.setCaretOffset(position);
            v.key("c"); v.key("i"); v.key("'");
            eq("select ''", e.getText()); eq(VimMode.INSERT, v.getMode()); eq(8, e.getCaretOffset());
        }
        for (String text : new String[] { "select 1", "select 'open", "'open\nclosed'", "" }) {
            e = new Fake(text); v = new VimEngine(e);
            v.key("c"); v.key("i"); v.key("'");
            eq(text, e.getText()); eq(VimMode.NORMAL, v.getMode());
            v.key("l"); eq(Math.min(1, Math.max(0, text.length() - 1)), e.getCaretOffset());
        }
        for (String text : new String[] { "select 'it''s good'", "select 'it\\'s good'", "select '\u732b caf\u00e9'" }) {
            e = new Fake(text); v = new VimEngine(e); e.setCaretOffset(13);
            v.key("c"); v.key("i"); v.key("'");
            eq("select ''", e.getText()); eq(text.substring(8, text.length() - 1), v.getRegister().text());
        }
        e = new Fake(sql); v = new VimEngine(e);
        v.key("c"); v.key("i"); v.key("<Esc>"); v.key("l");
        eq(sql, e.getText()); eq(1, e.getCaretOffset()); eq(VimMode.NORMAL, v.getMode());
    }

    private static void delimitedObjects() {
        String[][] pairs = { {"'", "'", "'"}, {"\"", "\"", "\""},
                {"(", ")", "()b"}, {"[", "]", "[]"}, {"{", "}", "{}B"} };
        for (String[] pair : pairs) {
            String original = "pre " + pair[0] + "old" + pair[1] + " post";
            for (char key : pair[2].toCharArray()) {
                for (char operator : "cdy".toCharArray()) {
                    for (int position : new int[] { 0, 4, 5, 6, 8 }) {
                        assertObject(original, position, "" + operator + "i" + key,
                                "old", "pre " + pair[0] + pair[1] + " post", operator);
                    }
                }
                for (int position : new int[] { 0, 4, 5 }) {
                    assertObject("pre " + pair[0] + pair[1], position, "ci" + key,
                            "", "pre " + pair[0] + pair[1], 'c');
                }
                for (String missing : new String[] { "", "plain", "pre " + pair[0] + "open" }) {
                    Fake e = new Fake(missing); VimEngine v = new VimEngine(e);
                    keys(v, "ci" + key);
                    eq(missing, e.getText()); eq(VimMode.NORMAL, v.getMode());
                    keys(v, "i"); eq(VimMode.INSERT, v.getMode());
                }
                Fake e = new Fake(original); VimEngine v = new VimEngine(e);
                keys(v, "ci"); v.key("<Esc>"); keys(v, "l");
                eq(original, e.getText()); eq(1, e.getCaretOffset());
            }
        }
        for (String[] pair : pairs) {
            if (pair[0].equals(pair[1])) continue;
            String nested = "pre " + pair[0] + "outer " + pair[0] + "inner" + pair[1] + " tail" + pair[1];
            int inside = nested.indexOf("inner");
            for (char key : pair[2].toCharArray()) {
                for (char operator : "cdy".toCharArray()) {
                    assertObject(nested, inside, "" + operator + "i" + key, "inner",
                            nested.replace("inner", ""), operator);
                    assertObject(nested, inside, "2" + operator + "i" + key,
                            "outer " + pair[0] + "inner" + pair[1] + " tail",
                            "pre " + pair[0] + pair[1], operator);
                }
                assertObject(nested, inside, "c2i" + key,
                        "outer " + pair[0] + "inner" + pair[1] + " tail",
                        "pre " + pair[0] + pair[1], 'c');
                assertObject("pre " + pair[0] + "\n one\n two\n" + pair[1], 9, "ci" + key,
                        "\n one\n two\n", "pre " + pair[0] + pair[1], 'c');
                Fake e = new Fake(nested); VimEngine v = new VimEngine(e); e.setCaretOffset(inside);
                keys(v, "3ci" + key); eq(nested, e.getText()); eq(VimMode.NORMAL, v.getMode());
            }
        }
        assertObject("select \"a\\\"b\"", 10, "ci\"", "a\\\"b", "select \"\"", 'c');
        assertObject("select \"a\"\"b\"", 10, "ci\"", "a\"\"b", "select \"\"", 'c');
        assertObject("select \"one\", \"two\"", 13, "ci\"", "two", "select \"one\", \"\"", 'c');
        assertObject("f(')', \"(\", [x], {y})", 15, "ci(", "')', \"(\", [x], {y}", "f()", 'c');
        assertObject("(first) (second)", 8, "ci)", "second", "(first) ()", 'c');
        assertObject("-- don't match (\nselect (one /* ) ' */ + two -- )\n)", 25, "ci(",
                "one /* ) ' */ + two -- )\n", "-- don't match (\nselect ()", 'c');
        assertObject("/* don't match ( */ select (one)", 28, "ci(",
                "one", "/* don't match ( */ select ()", 'c');
        for (String command : new String[] { "ci\"", "2ci\"", "ci(", "ci[", "ci{" }) {
            Fake e = new Fake("past\n\"next\" ([{}])"); VimEngine v = new VimEngine(e);
            keys(v, command); eq("past\n\"next\" ([{}])", e.getText()); eq(VimMode.NORMAL, v.getMode());
        }
    }

    private static void assertObject(String original, int position, String command,
                                     String contents, String changed, char operator) {
        Fake e = new Fake(original); VimEngine v = new VimEngine(e); e.setCaretOffset(position);
        keys(v, command);
        eq(operator == 'y' ? original : changed, e.getText());
        eq(contents, v.getRegister().text()); eq(false, v.getRegister().lineWise());
        eq(operator == 'c' ? VimMode.INSERT : VimMode.NORMAL, v.getMode());
        if (operator == 'c') {
            int at = e.getCaretOffset();
            e.replace(at, at, "new");
            eq(changed.substring(0, at) + "new" + changed.substring(at), e.getText());
            v.key("<Esc>"); eq(VimMode.NORMAL, v.getMode());
        }
    }

    private static void keys(VimEngine engine, String command) {
        for (char key : command.toCharArray()) engine.key(String.valueOf(key));
    }

    private static void visualInnerWord() {
        Fake e = new Fake("select customer_id  ,() from");
        VimEngine v = new VimEngine(e);
        e.setCaretOffset(11);
        v.key("v"); v.key("i"); v.key("w");
        eq(VimMode.VISUAL, v.getMode());
        eq(7, e.getSelectionStart());
        eq(18, e.getSelectionEnd());
        v.key("y");
        eq("customer_id", v.getRegister().text());

        e.setCaretOffset(18);
        v.key("v"); v.key("i"); v.key("w"); v.key("y");
        eq("  ", v.getRegister().text());

        e.setCaretOffset(21);
        v.key("v"); v.key("i"); v.key("w"); v.key("y");
        eq(",()", v.getRegister().text());

        e = new Fake("last");
        v = new VimEngine(e);
        e.setCaretOffset(3);
        v.key("v"); v.key("i"); v.key("w"); v.key("d");
        eq("", e.getText());
    }

    private static void findCharacter() {
        Fake e = new Fake("a1 a1 a0\\\na1 z");
        VimEngine v = new VimEngine(e);
        v.key("2"); v.key("f"); v.key("1");
        eq(4, e.getCaretOffset());
        v.key("f"); v.key("1");
        eq(4, e.getCaretOffset());
        v.key("2"); v.key("f"); v.key("0");
        eq(4, e.getCaretOffset());
        v.key("f"); v.key("0");
        eq(7, e.getCaretOffset());
        v.key("f"); v.key("\\");
        eq(8, e.getCaretOffset());
        v.key("f"); v.key("<Esc>"); v.key("h");
        eq(7, e.getCaretOffset());
        eq(VimMode.NORMAL, v.getMode());
        v.key("0"); v.key("f"); v.key(" ");
        eq(2, e.getCaretOffset());
    }

    private static void visualAndSearch() {
        Fake e=new Fake("alpha beta\nalpha"); VimEngine v=new VimEngine(e);
        v.key("v");v.key("l");v.key("y");eq("al",v.getRegister().text());eq(VimMode.NORMAL,v.getMode());
        e = new Fake("one\ntwo\nthree"); v = new VimEngine(e);
        v.key("j"); v.key("V"); v.key("j"); v.key("k");
        eq(4, e.getSelectionStart()); eq(8, e.getSelectionEnd());
        v.key("k");
        eq(0, e.getSelectionStart()); eq(8, e.getSelectionEnd());
        e = new Fake("alpha beta\nalpha"); v = new VimEngine(e);
        v.key("/"); for(char c:"beta".toCharArray())v.key(String.valueOf(c));v.key("<Enter>");eq(6,e.getCaretOffset());
        v.key("n");eq(6,e.getCaretOffset()); v.key("?");for(char c:"alpha".toCharArray())v.key(String.valueOf(c));v.key("<Enter>");eq(0,e.getCaretOffset());
    }
    private static void edgeCases() {
        Fake empty=new Fake(""); VimEngine v=new VimEngine(empty); v.key("x");v.key("w");v.key("d");v.key("d");eq("",empty.getText());
        Fake unicode=new Fake("\u732b caf\u00e9, dog"); v=new VimEngine(unicode); v.key("d");v.key("e");eq(" caf\u00e9, dog",unicode.getText());
        Fake line=new Fake("alpha beta\ngamma"); v=new VimEngine(line);v.key("d");v.key("$");eq("\ngamma",line.getText());
    }
    private static void eq(Object expected,Object actual){if(!expected.equals(actual))throw new AssertionError("expected="+expected+" actual="+actual);}
    private static final class Fake implements VimEditor {
        private String text; private int caret; private int start,end;
        Fake(String text){this.text=text;}
        public String getText(){return text;} public void replace(int s,int e,String value){text=text.substring(0,s)+value+text.substring(e);caret=s+value.length();}
        public int getCaretOffset(){return caret;} public void setCaretOffset(int o){caret=o;} public void select(int s,int e){start=s;end=e;caret=e;}
        public int getSelectionStart(){return start;} public int getSelectionEnd(){return end;} public void undo(){} public void redo(){}
    }
}
