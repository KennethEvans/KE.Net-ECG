package net.kenevans.polar.polarecg;

import java.util.LinkedList;

public class RunningAverage {
    private LinkedList<Double> list;
    private int maxItems;
    private double sum;

    public RunningAverage(int maxItems) {
        list = new LinkedList<>();
        this.maxItems = maxItems;
        sum = 0;
    }

    public void add(double val) {
        if (list.size() == maxItems) {
            sum -= list.getFirst();
            list.removeFirst();
        }
        list.add(val);
        sum += val;
    }

    public double average() {
        return (list.size() == 0) ? 0 : sum / list.size();
    }

    public double sum() {
        return sum;
    }

    public int size() {
        return list.size();
    }
}
