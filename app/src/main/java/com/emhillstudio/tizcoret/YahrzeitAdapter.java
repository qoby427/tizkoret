package com.emhillstudio.tizcoret;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class YahrzeitAdapter extends RecyclerView.Adapter<YahrzeitAdapter.ViewHolder> {

    private final Context context;
    private JSONArray data;

    public YahrzeitAdapter(Context ctx) {
        this.context = ctx;
        this.data = UserSettings.getYahrzeitList(ctx);
    }

    public void addEmptyRow() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("name", "");
            obj.put("civilDate", "");
            obj.put("hebrewDate", "");
        } catch (JSONException ignored) {}

        data.put(obj);
        UserSettings.saveYahrzeitList(context, data);
        notifyItemInserted(data.length() - 1);
    }

    public void removeRow(int position) {
        JSONArray newArr = new JSONArray();
        for (int i = 0; i < data.length(); i++) {
            if (i != position) newArr.put(data.optJSONObject(i));
        }
        data = newArr;
        UserSettings.saveYahrzeitList(context, data);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public YahrzeitAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.yahrzeit_row, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull YahrzeitAdapter.ViewHolder holder, int position) {
        JSONObject obj = data.optJSONObject(position);
        if (obj == null) return;

        holder.bind(obj, position);
    }

    @Override
    public int getItemCount() {
        return data.length();
    }

    class ViewHolder extends RecyclerView.ViewHolder {

        EditText etName, etCivil, etHebrew;
        ImageButton btnDelete;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);

            etName = itemView.findViewById(R.id.etName);
            etCivil = itemView.findViewById(R.id.etCivilDate);
            etHebrew = itemView.findViewById(R.id.etHebrewDate);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }

        void bind(JSONObject obj, int position) {
            etName.setText(obj.optString("name", ""));
            etCivil.setText(obj.optString("civilDate", ""));
            etHebrew.setText(obj.optString("hebrewDate", ""));

            // Save on edit
            etName.addTextChangedListener(new SimpleWatcher(s -> save(position, "name", s)));
            etCivil.addTextChangedListener(new SimpleWatcher(s -> save(position, "civilDate", s)));
            etHebrew.addTextChangedListener(new SimpleWatcher(s -> save(position, "hebrewDate", s)));

            btnDelete.setOnClickListener(v -> removeRow(position));
        }

        void save(int pos, String key, String value) {
            try {
                JSONObject obj = data.getJSONObject(pos);
                obj.put(key, value);
                UserSettings.saveYahrzeitList(context, data);
            } catch (JSONException ignored) {}
        }
    }

    // Helper watcher
    static class SimpleWatcher implements TextWatcher {
        private final OnChange callback;

        interface OnChange {
            void onChange(String s);
        }

        SimpleWatcher(OnChange cb) {
            this.callback = cb;
        }

        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
            callback.onChange(s.toString());
        }
        @Override public void afterTextChanged(Editable s) {}
    }
}
