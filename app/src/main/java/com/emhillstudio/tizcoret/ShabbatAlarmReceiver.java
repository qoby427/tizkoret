package com.emhillstudio.tizcoret;

public class ShabbatAlarmReceiver extends AlarmReceiver {
    @Override
    protected String channel(){
        return "shabbat_channel";
    }
    @Override
    protected int icon(){
        return R.drawable.ic_shabbat_candles;
    }
}

