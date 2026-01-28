package com.emhillstudio.tizcoret;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class YahrzeitEntry {
    public String name;
    public Date diedDate;      // <-- store real Date
    public String hebrewDate;
    public String inYear;

    public YahrzeitEntry(String name, Date diedDate, String hebrewDate, String inYear) {
        this.name = name;
        this.diedDate = diedDate;
        this.hebrewDate = hebrewDate;
        this.inYear = inYear;
    }

    public String getDiedDateString() {
        if (diedDate == null) return "";
        SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy", Locale.US);
        return sdf.format(diedDate);
    }

    public long getDiedMillis() {

        return diedDate != null ? diedDate.getTime() : 0L;
    }
    public boolean isComplete() {
        boolean is = name != null && !name.trim().isEmpty()
                && hebrewDate != null && !hebrewDate.trim().isEmpty()
                && diedDate != null
                && inYear != null && !inYear.trim().isEmpty();
        return is;
    }
}
