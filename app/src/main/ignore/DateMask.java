package com.emhillstudio.tizcoret;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

public class DateMask implements TextWatcher {

    private boolean isUpdating;
    private final EditText editText;

    public DateMask(EditText editText) {
        this.editText = editText;
    }

    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

    @Override
    public void afterTextChanged(Editable s) {
        if (isUpdating) return;

        isUpdating = true;

        String digits = s.toString().replaceAll("[^0-9]", "");
        StringBuilder out = new StringBuilder();

        if (digits.length() > 0)
            out.append(digits.substring(0, Math.min(2, digits.length())));

        if (digits.length() > 2)
            out.append("/").append(digits.substring(2, Math.min(4, digits.length())));

        if (digits.length() > 4)
            out.append("/").append(digits.substring(4, Math.min(8, digits.length())));

        editText.setText(out.toString());
        editText.setSelection(out.length());

        isUpdating = false;
    }
}
