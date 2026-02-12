package com.emhillstudio.tizcoret;

public class EventInfo {
    public EventType type;
    public long eventTime;
    public String payloadJson;

    public AlarmEntry early;
    public AlarmEntry final5;

    public int requestCodeBase;

    public Class<?> receiverClass() {
        return type == EventType.SHABBAT
                ? ShabbatAlarmReceiver.class
                : YahrzeitAlarmReceiver.class;
    }
}
