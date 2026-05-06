package co.kr.jigeum.cache;

import co.kr.jigeum.yahoo.StockQuote;

import java.util.ArrayList;
import java.util.List;

public class ForceStore {

    private static final ForceStore instance = new ForceStore();
    public static ForceStore getInstance() { return instance; }
    private ForceStore() {}

    private List<StockQuote> detected = new ArrayList<>();
    private long updatedAt = 0;

    public void save(List<StockQuote> list) {
        this.detected = list;
        this.updatedAt = System.currentTimeMillis();
    }

    public List<StockQuote> getAll() { return detected; }
    public long getUpdatedAt() { return updatedAt; }

}
