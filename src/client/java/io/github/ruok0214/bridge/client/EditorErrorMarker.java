package io.github.ruok0214.bridge.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.AbstractTextAreaWidget;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.network.chat.FormattedText;

/** Draws a box around the line a syntax error points at, so the number in the message is enough to find it. */
final class EditorErrorMarker {
    private static final int FILL=0x40FF5555, BORDER=0xFFFF5555;
    private EditorErrorMarker() {}

    private static int padding() { return AbstractTextAreaWidget.DEFAULT_TOTAL_PADDING; }

    private static int wrapWidth(MultiLineEditBox editor, int width, int height) {
        boolean scrollable = editor.getInnerHeight() > height - padding();
        return width - padding() - (scrollable ? AbstractScrollArea.SCROLLBAR_WIDTH : 0);
    }

    /**
     * First wrapped row of a 1-based logical line, or -1 when our wrapping disagrees with the widget's
     * own content height. Guessing a position is worse than drawing nothing.
     */
    private static int rowOf(Font font, String text, int line, int wrapWidth, int innerHeight) {
        String[] lines=text.split("\\R",-1);
        if(line>lines.length)return -1;
        int rows=0, target=-1;
        for(int i=0;i<lines.length;i++) {
            if(i==line-1)target=rows;
            rows+=Math.max(1,font.split(FormattedText.of(lines[i]),wrapWidth).size());
        }
        return rows*font.lineHeight==innerHeight?target:-1;
    }

    static void scrollTo(Font font, MultiLineEditBox editor, String text, int line, int width, int height) {
        if(line<1)return;
        int row=rowOf(font,text,line,wrapWidth(editor,width,height),editor.getInnerHeight());
        if(row<0)return;
        editor.setScrollAmount(Math.max(0,row*font.lineHeight-font.lineHeight));
    }

    static void extract(GuiGraphicsExtractor g, Font font, MultiLineEditBox editor, String text, int line,
                        int x, int y, int width, int height) {
        if(line<1)return;
        int inner=wrapWidth(editor,width,height);
        int row=rowOf(font,text,line,inner,editor.getInnerHeight());
        if(row<0)return;
        int left=x+padding()/2;
        int top=y+padding()/2+row*font.lineHeight-(int)editor.scrollAmount();
        if(top<y||top+font.lineHeight>y+height)return;
        g.fill(left,top,left+inner,top+font.lineHeight,FILL);
        g.outline(left-1,top-1,inner+2,font.lineHeight+2,BORDER);
    }
}
