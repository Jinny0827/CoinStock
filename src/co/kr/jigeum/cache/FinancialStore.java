package co.kr.jigeum.cache;

import co.kr.jigeum.dart.model.FinancialData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class FinancialStore {

    private static final FinancialStore INSTANCE = new FinancialStore();

    // key: symbol (005930.KS) -> 종목, 데이터
    private final ConcurrentHashMap<String, FinancialData> store = new ConcurrentHashMap<>();

    private FinancialStore() {}

    public static FinancialStore getInstance() {
        return INSTANCE;
    }

    public void save(FinancialData data) {
        if (data != null && data.getSymbol() != null) {
            store.put(data.getSymbol(), data);
        }
    }

    public void saveAll(List<FinancialData> dataList) {
        for (FinancialData data : dataList) {
            save(data);
        }
    }

    public FinancialData get(String symbol) {
        return store.get(symbol);
    }

    public List<FinancialData> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(store.values()));
    }

    public boolean contains(String symbol) {
        return store.containsKey(symbol);
    }

    public int size() {
        return store.size();
    }

    public void clear() {
        store.clear();
    }
}
