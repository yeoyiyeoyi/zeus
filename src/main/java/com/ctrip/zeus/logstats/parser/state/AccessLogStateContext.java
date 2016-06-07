package com.ctrip.zeus.logstats.parser.state;

import com.ctrip.zeus.logstats.parser.KeyValue;

import java.util.LinkedList;
import java.util.List;

/**
 * Created by zhoumy on 2016/6/7.
 */
public class AccessLogStateContext implements StateMachineContext {
    private String value;
    private char[] valueArray;
    private int idx;
    private LinkedList<KeyValue> parsedValues = new LinkedList<>();

    @Override
    public void setSourceValue(String value) {
        this.value = value;
        valueArray = this.value.toCharArray();
    }

    @Override
    public int getCurrentIndex() {
        return idx;
    }

    @Override
    public void proceed(int length) {
        idx += length;
    }

    @Override
    public char[] delay(int length) {
        char[] result = new char[length];
        for (int i = 0; i < length && idx + i < valueArray.length; i++) {
            result[i] = valueArray[idx + i];
        }
        return result;
    }

    @Override
    public char[] getSource() {
        return valueArray;
    }

    @Override
    public String getLastParsedValue() {
        KeyValue last = parsedValues.getLast();
        return last.getValue();
    }

    @Override
    public List<KeyValue> getResult() {
        return parsedValues;
    }

    @Override
    public boolean shouldProceed() {
        return idx < value.length() - 1;
    }

    @Override
    public void addResult(String key, String value) {
        parsedValues.push(new KeyValue(key, value));
    }
}
