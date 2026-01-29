package com.emhillstudio.tizcoret;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class YahrzeitAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final Context context;
    private List<YahrzeitEntry> entries;
    private OnEntryChangedListener listener;
    private final SharedPreferences prefs;

    public interface OnEntryChangedListener {
        void onEntryChanged(YahrzeitEntry entry);
    }

    public YahrzeitAdapter(Context context, List<YahrzeitEntry> entries) {
        this.context = context;
        this.entries = entries;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            this.entries.removeIf(e ->
                    e.name == null || e.name.trim().isEmpty() || e.diedDate == null
            );
        }

        prefs = context.getSharedPreferences(UserSettings.PREFS, Context.MODE_PRIVATE);
    }

    // ---------------------------------------------------------
    // SimpleTextWatcher
    // ---------------------------------------------------------
    public abstract static class SimpleTextWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }

    // ---------------------------------------------------------
    // Adapter basics
    // ---------------------------------------------------------
    @Override
    public int getItemCount() {
        return entries.size();
    }

    public void setOnEntryChangedListener(OnEntryChangedListener l) {
        this.listener = l;
    }

    // ---------------------------------------------------------
    // Create ViewHolder
    // ---------------------------------------------------------
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_yahrzeit_row, parent, false);

        RowViewHolder holder = new RowViewHolder(v);
        String format = prefs.getString("date_format", "MM/dd/yyyy");
        EditText diedDate = holder.dateField;
        diedDate.setHint(format.toUpperCase(Locale.US));

        return holder;
    }

    // ---------------------------------------------------------
    // Bind ViewHolder
    // ---------------------------------------------------------
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        RowViewHolder row = (RowViewHolder) holder;
        YahrzeitEntry entry = entries.get(position);

        // ---- remove old watchers ----
        if (row.nameWatcher != null)
            row.nameField.removeTextChangedListener(row.nameWatcher);

        if (row.dateWatcher != null)
            row.dateField.removeTextChangedListener(row.dateWatcher);

        // ---- bind values (NO side effects) ----
        row.isBinding = true;

        row.nameField.setText(entry.name != null ? entry.name : "");

        if (entry.diedDate != null) {
            String format = prefs.getString("date_format", "MM/dd/yyyy");
            SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.US);
            row.dateField.setText(sdf.format(entry.diedDate));
        } else {
            row.dateField.setText("");
        }

        row.hebrewField.setText(entry.hebrewDate != null ? entry.hebrewDate : "");
        row.inYearField.setText(entry.inYear != null ? entry.inYear : "");

        row.isBinding = false;

        // -------------------------------------------------
        // Name watcher (guarded)
        // -------------------------------------------------
        row.nameWatcher = new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (row.isBinding) return;

                row.nameField.setBackgroundResource(0);

                String newName = s.toString();
                if (newName.equals(entry.name)) return;

                entry.name = newName;
            }
        };
        row.nameField.addTextChangedListener(row.nameWatcher);

        // -------------------------------------------------
        // Date watcher (STORED + guarded)
        // -------------------------------------------------
        row.dateWatcher = new TextWatcher() {
            private boolean isEditing;

            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (row.isBinding || isEditing) return;
                isEditing = true;

                String digits = s.toString().replaceAll("[^0-9]", "");
                StringBuilder formatted = new StringBuilder();

                if (digits.length() > 0) {
                    formatted.append(digits.substring(0, Math.min(2, digits.length())));
                    if (digits.length() >= 3) formatted.append("/");
                }
                if (digits.length() > 2) {
                    formatted.append(digits.substring(2, Math.min(4, digits.length())));
                    if (digits.length() >= 5) formatted.append("/");
                }
                if (digits.length() > 4) {
                    formatted.append(digits.substring(4, Math.min(8, digits.length())));
                }

                row.dateField.setText(formatted.toString());
                row.dateField.setSelection(formatted.length());

                if (digits.length() == 8 && isValidDate(formatted.toString())) {
                    Date parsed = parseDate(formatted.toString());
                    entry.diedDate = parsed;
                    boolean noname = entry.name.trim().isEmpty();

                    if (noname) {
                        row.nameField.setBackgroundResource(R.drawable.red_border);
                    }
                    else {
                        row.nameField.setBackgroundResource(0);
                    }

                    entry.hebrewDate = HebrewUtils.toHebrewDate(parsed);
                    entry.inYear = HebrewUtils.computeInYear(parsed);

                    if (listener != null && !entry.name.trim().isEmpty() && entry.isComplete())
                        listener.onEntryChanged(entry);

                    row.hebrewField.setText(entry.hebrewDate);
                    row.inYearField.setText(entry.inYear);
                }

                isEditing = false;
            }
        };
        row.dateField.addTextChangedListener(row.dateWatcher);
    }

    // ---------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------
    private boolean isValidDate(String text) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy", Locale.US);
            sdf.setLenient(false);
            sdf.parse(text);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Date parseDate(String text) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy", Locale.US);
            sdf.setLenient(false);
            return sdf.parse(text);
        } catch (Exception e) {
            return null;
        }
    }

    // ---------------------------------------------------------
    // Public API
    // ---------------------------------------------------------
    public void addEmptyRow() {
        entries.add(new YahrzeitEntry("", null, "", ""));
        notifyItemInserted(entries.size() - 1);
    }

    public List<YahrzeitEntry> getEntries() {
        return entries;
    }

    public void setEntries(List<YahrzeitEntry> newEntries) {
        entries = new ArrayList<>(newEntries);
        notifyDataSetChanged();
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        if (holder instanceof RowViewHolder) {
            RowViewHolder row = (RowViewHolder) holder;

            if (row.nameWatcher != null)
                row.nameField.removeTextChangedListener(row.nameWatcher);

            if (row.dateWatcher != null)
                row.dateField.removeTextChangedListener(row.dateWatcher);
        }
        super.onViewRecycled(holder);
    }

    // ---------------------------------------------------------
    // ViewHolder
    // ---------------------------------------------------------
    static class RowViewHolder extends RecyclerView.ViewHolder {

        EditText nameField;
        EditText dateField;
        TextView hebrewField;
        TextView inYearField;

        TextWatcher nameWatcher;
        TextWatcher dateWatcher;

        boolean isBinding;

        RowViewHolder(@NonNull View itemView) {
            super(itemView);

            nameField = itemView.findViewById(R.id.nameField);
            dateField = itemView.findViewById(R.id.diedDate);
            hebrewField = itemView.findViewById(R.id.hebrewDate);
            inYearField = itemView.findViewById(R.id.inYear);
        }
    }
}
