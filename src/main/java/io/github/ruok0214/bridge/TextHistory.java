package io.github.ruok0214.bridge;
import java.util.ArrayDeque;

public final class TextHistory {
    private final ArrayDeque<String> undo=new ArrayDeque<>();
    private int chars;
    public void remember(String value) {
        undo.addLast(value);chars+=value.length();
        while(undo.size()>50 || chars>8_000_000) chars-=undo.removeFirst().length();
    }
    public String undo(String current) {
        if(undo.isEmpty())return current;
        String last=undo.removeLast();chars-=last.length();return last;
    }
}
