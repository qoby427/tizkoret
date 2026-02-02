package com.emhillstudio.tizcoret;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class YahrzeitEntry {
    public String name;
    public Date diedDate;      // <-- store real Date
    public String hebrewDate;
    public Date inYear;

    public YahrzeitEntry(String name, Date diedDate, String hebrewDate, Date inYear) {
        this.name = name;
        this.diedDate = diedDate;
        this.hebrewDate = hebrewDate;
        this.inYear = inYear;
    }
    public boolean isComplete() {
        boolean is = name != null && !name.trim().isEmpty()
                && hebrewDate != null && !hebrewDate.trim().isEmpty()
                && diedDate != null && inYear != null ;
        return is;
    }
}
