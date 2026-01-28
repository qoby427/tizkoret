package com.emhillstudio.tizcoret;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.Window;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class MessageActivity extends AppCompatActivity {
    public AlertDialog showMessage(String message, boolean success) {
        View view = getLayoutInflater().inflate(R.layout.dialog_message, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(view)
                .create();
        TextView txt = view.findViewById(R.id.txtMessage);
        txt.setText(message);

        dialog.show(); // MUST be first

        // Remove the default dialog frame that steals the first tap
        View parent = (View) view.getParent();
        if (parent != null) parent.setBackground(null);

        // Some devices wrap it twice
        View grandParent = (View) parent.getParent();
        if (grandParent != null) grandParent.setBackground(null);

        // Now apply your custom background
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                success
                        ? new int[]{0xFFB2FFB2, 0xFF7FE87F}
                        : new int[]{0xFFFFB2B2, 0xFFE87F7F}
        );
        bg.setCornerRadius(32f);
        bg.setStroke(4, 0x55000000);
        view.setBackground(bg);

        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.getWindow().setElevation(24f);

        dialog.setCanceledOnTouchOutside(false);

        view.findViewById(R.id.btnMessageOk).setOnClickListener(v -> {
            dialog.dismiss();
        });

        return dialog;
    }

    public void showQuestion(
            String title,
            String message,
            Runnable onYes
    ) {
        View view = getLayoutInflater().inflate(R.layout.dialog_yes_no, null);

        TextView msgView = view.findViewById(R.id.dialogMessage);
        msgView.setText(message);
        TextView titleView = view.findViewById(R.id.dialogTitle);
        titleView.setText(title);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(view)
                .create();

        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(32f);
        bg.setStroke(4, 0x55000000);
        bg.setColors(new int[]{
                0xFFE0F7FF,
                0xFFB0D8FF
        });
        bg.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);

        view.setBackground(bg);

        view.findViewById(R.id.btnYes).setOnClickListener(v -> {
            dialog.dismiss();
            if (onYes != null) onYes.run();
        });

        view.findViewById(R.id.btnNo).setOnClickListener(v -> dialog.dismiss());

        dialog.show();

        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        View parent = (View) view.getParent();
        if (parent != null) parent.setBackground(null);
        View grandParent = (View) parent.getParent();
        if (grandParent != null) grandParent.setBackground(null);
    }
}
