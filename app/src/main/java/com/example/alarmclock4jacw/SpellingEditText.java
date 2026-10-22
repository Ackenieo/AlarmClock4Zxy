package com.example.alarmclock4jacw;

import android.content.Context;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatEditText;

/**
 * 拼写输入框：保留输入法联想会被此处的 noSuggestions 关掉，
 * 同时封掉长按粘贴，避免直接复制答案。
 */
public class SpellingEditText extends AppCompatEditText {

    public SpellingEditText(Context context) {
        super(context);
        init();
    }

    public SpellingEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SpellingEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setLongClickable(false);
        setTextIsSelectable(false);
    }

    @Override
    public boolean onTextContextMenuItem(int id) {
        if (id == android.R.id.paste || id == android.R.id.pasteAsPlainText) {
            return false;
        }
        return super.onTextContextMenuItem(id);
    }
}
