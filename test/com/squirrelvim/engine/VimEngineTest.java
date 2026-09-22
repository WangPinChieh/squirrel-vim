package com.squirrelvim.engine;

/** Focused MVP command regression tests, run without SQuirreL. */
public final class VimEngineTest {
    public static void main(String[] args) {
        modesAndInsert(); motionsAndCounts(); operatorsAndRegisters(); textObjects(); visualAndSearch(); edgeCases();
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
    private static void visualAndSearch() {
        Fake e=new Fake("alpha beta\nalpha"); VimEngine v=new VimEngine(e);
        v.key("v");v.key("l");v.key("y");eq("al",v.getRegister().text());eq(VimMode.NORMAL,v.getMode());
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
        public int getCaretOffset(){return caret;} public void setCaretOffset(int o){caret=o;} public void select(int s,int e){start=s;end=e;}
        public int getSelectionStart(){return start;} public int getSelectionEnd(){return end;} public void undo(){} public void redo(){}
    }
}
