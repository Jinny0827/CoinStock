package co.kr.jigeum.scheduler;

import co.kr.jigeum.cache.FinancialStore;
import co.kr.jigeum.cache.StockDataStore;
import co.kr.jigeum.common.config.Config;
import co.kr.jigeum.dart.DartClient;
import co.kr.jigeum.dart.DartCorpCodeLoader;
import co.kr.jigeum.dart.model.FinancialData;
import co.kr.jigeum.groq.ThemeAnalyzer;
import co.kr.jigeum.yahoo.StockQuote;
import co.kr.jigeum.yahoo.YahooFinanceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class StockScheduler {
    private static final Logger logger = LoggerFactory.getLogger(StockScheduler.class);
    private static final ThemeAnalyzer themeAnalyzer = new ThemeAnalyzer();
    private static LocalDate lastFinancialUpdate = null;

    // 국장 종목
    private static final String[] KR_SYMBOLS = {
            "005930.KS", "000660.KS", "035420.KS", "035720.KS",
            "051910.KS", "006400.KS", "028260.KS", "105560.KS",
            "055550.KS", "012330.KS"
    };

    // 미장 종목
    private static final String[] US_SYMBOLS = {
            "AAPL", "MSFT", "NVDA", "GOOGL", "AMZN",
            "META", "TSLA", "BRK-B", "JPM", "V"
    };

    // 지수
    private static final String[] INDEX_SYMBOLS = {
            "^KS11", "^KQ11", "^GSPC", "^IXIC"
    };

    public static void start() throws Exception {
        int interval = Config.getInstance().getInt("scheduler.interval.seconds", 5);
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

        executor.scheduleAtFixedRate(() -> {
            try {
                boolean krOpen = isKrMarketOpen();
                boolean usOpen = isUsMarketOpen();

                if (!krOpen && !usOpen) {
                    logger.debug("전체 장 마감 - 수집 스킵");
                    return;
                }

                YahooFinanceClient yahooClient = new YahooFinanceClient();
                StockDataStore dataStore = StockDataStore.getInstance();
                FinancialStore financialStore = FinancialStore.getInstance();

                // 1. 테마 및 지수 수집 (야후 v8 기반 가격정보)
                themeAnalyzer.refreshThemes();
                dataStore.save(yahooClient.fetchQuotes(INDEX_SYMBOLS));

                // 2. [국장] 시세 및 재무 데이터 처리
                if (krOpen) {
                    logger.debug("[국장] 시세 수집 중...");
                    List<StockQuote> krQuotes = yahooClient.fetchQuotes(KR_SYMBOLS);
                    dataStore.save(krQuotes);

                    // 재무 데이터 업데이트 (하루 1회)
                    updateFinancialsIfNeeded(KR_SYMBOLS, true);
                }

                // 3. [미장] 시세 및 재무 데이터 처리
                if (usOpen) {
                    logger.debug("[미장] 시세 수집 중...");
                    List<StockQuote> usQuotes = yahooClient.fetchQuotes(US_SYMBOLS);
                    dataStore.save(usQuotes);

                    // 재무 데이터 업데이트 (하루 1회) - 여기서 SEC 또는 전용 API 호출
                    updateFinancialsIfNeeded(US_SYMBOLS, false);
                }

            } catch (Exception e) {
                logger.error("스케줄러 실행 에러: {}", e.getMessage());
            }
        }, 0, interval, TimeUnit.SECONDS);

        logger.info("시세 수집 스케줄러 시작 - {}초 간격", interval);
    }

    /**
     * 국장/미장 통합 재무 데이터 업데이트 로직
     */
    private static void updateFinancialsIfNeeded(String[] symbols, boolean isKr) {
        LocalDate today = LocalDate.now();
//        if (lastFinancialUpdate != null && lastFinancialUpdate.equals(today)) return;

        logger.info("[{}] 재무데이터 수집 시작", isKr ? "국장" : "미장");

        FinancialStore financialStore = FinancialStore.getInstance();
        StockDataStore dataStore = StockDataStore.getInstance();
        DartClient dartClient = new DartClient();

        for (String symbol : symbols) {
            try {
                FinancialData fd = null;

                // [수정 2] DartClient 호출 전, 야후에서 수집된 quote를 먼저 꺼내옴
                StockQuote quote = dataStore.get(symbol);

                if (isKr) {
                    // [수정 3] 메서드 시그니처 변경에 따라 quote를 함께 전달
                    // 이래야 컴파일 에러가 사라지고, 주식수 역산이 가능해집니다.
                    fd = dartClient.fetchFinancial(symbol, quote);
                } else {
                    // 미장: SEC EDGAR 또는 외부 API (추후 구현)
                    logger.debug("미장[{}] 재무 데이터 수집 준비 중...", symbol);
                }

                if (fd != null && quote != null) {
                    // 야후 실시간 가격으로 PER, PBR 최종 산출
                    fd.calculate(quote.getPrice());
                    financialStore.save(fd);
                    logger.info("[{}] 최종 지표 계산 완료 - EPS: {}, PER: {}", symbol, fd.getEps(), fd.getPer());
                }
            } catch (Exception e) {
                logger.warn("[{}] 재무 데이터 수집 실패: {}", symbol, e.getMessage());
            }
        }

        lastFinancialUpdate = today;
    }


    // 국장 장중 여부 (KST 09:00 ~ 16:00)
    private static boolean isKrMarketOpen() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));
        if (now.getDayOfWeek().getValue() >= 6) return false;
        int time = now.getHour() * 100 + now.getMinute();
        return time >= 900 && time < 1800;
    }

    // 미장 장중 여부 (EST 09:30 ~ 16:00)
    private static boolean isUsMarketOpen() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("America/New_York"));
        if (now.getDayOfWeek().getValue() >= 6) return false;
        int time = now.getHour() * 100 + now.getMinute();
        return time >= 930 && time < 1600;
    }
}
