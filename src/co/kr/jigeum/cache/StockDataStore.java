package co.kr.jigeum.cache;

import co.kr.jigeum.yahoo.StockQuote;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Redis 사용 전 메모리 캐시 사용
 *
 */
public class StockDataStore {
    private static final Logger logger = LoggerFactory.getLogger(StockDataStore.class);

    // 싱글톤 (캐시 객체 static하게 올리고 한 객체로만 계속 사용)
    private static final StockDataStore instance = new StockDataStore();
    public static StockDataStore getInstance() { return instance; }
    private StockDataStore() {}

    // 종목별 현재가 저장 (symbol → StockQuote)
    private final Map<String, StockQuote> quoteMap = new ConcurrentHashMap<>();

    // 마지막 업데이트 시간
    private long lastUpdated = 0;

    /**
     * 수집된 데이터 저장
     */
    public void save(List<StockQuote> quotes) {
        for (StockQuote quote : quotes) {
            quoteMap.put(quote.getSymbol(), quote);
        }

        lastUpdated = System.currentTimeMillis();
        logger.debug("캐시 저장 완료: {}개 종목", quoteMap.size());
    }

    /**
     * 전체 종목 조회
     */
    public List<StockQuote> getAll() {
        return new ArrayList<>(quoteMap.values());
    }

    /**
     * 단일 종목 조회
     */
    public StockQuote get(String symbol) {
        return quoteMap.get(symbol);
    }

    /**
     * 국장만 조회
     */
    public List<StockQuote> getKr() {
        List<StockQuote> result = new ArrayList<>();
        for (StockQuote quote : quoteMap.values()) {
            if (quote.getSymbol().endsWith(".KS") || quote.getSymbol().endsWith(".KQ")) {
                result.add(quote);
            }
        }
        return result;
    }

    /**
     * 미장만 조회
     */
    public List<StockQuote> getUs() {
        List<StockQuote> result = new ArrayList<>();
        for (StockQuote quote : quoteMap.values()) {
            String symbol = quote.getSymbol();
            if (!symbol.endsWith(".KS") && !symbol.endsWith(".KQ") && !symbol.startsWith("^")) {
                result.add(quote);
            }
        }
        return result;
    }

    /**
     * 지수만 조회
     */
    public List<StockQuote> getIndex() {
        List<StockQuote> result = new ArrayList<>();
        for (StockQuote quote : quoteMap.values()) {
            if (quote.getSymbol().startsWith("^")) {
                result.add(quote);
            }
        }
        return result;
    }

    public long getLastUpdated() { return lastUpdated; }
    public int size() { return quoteMap.size(); }

}
