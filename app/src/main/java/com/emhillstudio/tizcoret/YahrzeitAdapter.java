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
    private SharedPreferences prefs;

    public interface OnEntryChangedListener {
        void onEntryChanged(YahrzeitEntry entry);
    }
    public YahrzeitAdapter(Context context, List<YahrzeitEntry> entries, OnEntryChangedListener listener) {
        this.context = context;
        this.entries = entries;
        this.listener = listener;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            this.entries.removeIf(e ->
                    e.name == null || e.name.trim().isEmpty() ||
                            e.diedDate == null
            );
        }
        prefs = context.getSharedPreferences(UserSettings.PREFS, Context.MODE_PRIVATE);
    }

    // ---------------------------------------------------------
    // SimpleTextWatcher
    // ---------------------------------------------------------
    public abstract class SimpleTextWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
    }

    // ---------------------------------------------------------
    // View types
    // ---------------------------------------------------------
    @Override
    public int getItemCount() {
        return entries.size();
    }

    // ---------------------------------------------------------
    // Create ViewHolders
    // ---------------------------------------------------------
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_yahrzeit_row, parent, false);
        return new RowViewHolder(v);
    }


    // ---------------------------------------------------------
    // Bind ViewHolders
    // ---------------------------------------------------------
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        RowViewHolder row = (RowViewHolder) holder;
        YahrzeitEntry entry = entries.get(position);

        // Remove old watchers to avoid duplicates
        if (row.nameWatcher != null)
            row.nameField.removeTextChangedListener(row.nameWatcher);

        if (row.dateWatcher != null)
            row.dateField.removeTextChangedListener(row.dateWatcher);

        // Set current values (null-safe)
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

        // -------------------------
        // Name watcher
        // -------------------------
        row.nameWatcher = new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                entry.name = s.toString();
                UserSettings.saveYahrzeitName(context, entry);
            }
        };
        row.nameField.addTextChangedListener(row.nameWatcher);

        row.dateField.addTextChangedListener(new TextWatcher() {
            private boolean isEditing;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (isEditing) return;

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

                if (digits.length() == 8) {
                    if (isValidDate(formatted.toString())) {
                        Date parsed = parseDate(formatted.toString());

                        entry.diedDate = parsed;

                        // Compute Hebrew + in-year
                        String heb = HebrewUtils.toHebrewDate(parsed);
                        String inYear = HebrewUtils.computeInYear(parsed);

                        entry.hebrewDate = heb;
                        entry.inYear = inYear;
                        row.hebrewField.setText(heb);
                        row.inYearField.setText(inYear);

                        if(!entry.name.isEmpty() && !entry.diedDate.toString().isEmpty()) {
                            UserSettings.saveYahrzeitList(context, getEntries());
                            listener.onEntryChanged(entry);
                        }
                    }
                }

                isEditing = false;
            }
        });
    }
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
    // Add empty row
    // ---------------------------------------------------------
    public void addEmptyRow() {
        entries.add(new YahrzeitEntry("", null, "", ""));
        notifyItemInserted(entries.size()); // header is at position 0
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
    public List<YahrzeitEntry> getEntries() {
        return entries;
    }

    public void setEntries(List<YahrzeitEntry> newEntries) {
        entries = new ArrayList<>(newEntries);
        notifyDataSetChanged();
    }

    // ---------------------------------------------------------
    // Row ViewHolder
    // ---------------------------------------------------------
    static class RowViewHolder extends RecyclerView.ViewHolder {

        EditText nameField;
        EditText dateField;
        TextView hebrewField;
        TextView inYearField;

        TextWatcher nameWatcher;
        TextWatcher dateWatcher;

        public RowViewHolder(@NonNull View itemView) {
            super(itemView);

            nameField = itemView.findViewById(R.id.nameField);
            dateField = itemView.findViewById(R.id.diedDate);
            hebrewField = itemView.findViewById(R.id.hebrewDate);
            inYearField = itemView.findViewById(R.id.inYear);
            nameField.setOnFocusChangeListener((v, hasFocus) -> {
                System.out.println("NAME FOCUS = " + hasFocus);
            });

            dateField.setOnFocusChangeListener((v, hasFocus) -> {
                System.out.println("DATE FOCUS = " + hasFocus);
            });
        }
    }
}
