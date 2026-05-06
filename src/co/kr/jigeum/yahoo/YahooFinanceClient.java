package co.kr.jigeum.yahoo;

import co.kr.jigeum.common.config.Config;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class YahooFinanceClient {

    private static final Logger logger = LoggerFactory.getLogger(YahooFinanceClient.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final String CHART_URL = "https://query1.finance.yahoo.com/v8/finance/chart";

    private final int timeout;

    public YahooFinanceClient() {
        this.timeout = Config.getInstance().getInt("yahoo.finance.timeout", 5000);
    }

    /**
     * 여러 종목 현재가 조회 (병렬 호출)
     */
    public List<StockQuote> fetchQuotes(String[] symbols) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(symbols.length, 10));

        List<CompletableFuture<StockQuote>> futures = new ArrayList<>();
        for (String symbol : symbols) {
            CompletableFuture<StockQuote> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return fetchFromV8(symbol);
                } catch (Exception e) {
                    logger.warn("[{}] 조회 실패: {}", symbol, e.getMessage());
                    return null;
                }
            }, executor);
            futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        List<StockQuote> result = new ArrayList<>();
        for (CompletableFuture<StockQuote> future : futures) {
            StockQuote quote = future.get();
            if (quote != null) {
                result.add(quote);
                logger.debug("{}", quote);
            }
        }

        executor.shutdown();
        logger.info("수집 완료: {}개 종목", result.size());
        return result;
    }


    /**
     * Yahoo Finance v8 chart API (fallback)
     */
    private StockQuote fetchFromV8(String symbol) {
        try {
            String encodedSymbol = symbol.replace("^", "%5E");
            // v8 엔진은 crumb 없이도 기본 헤더만 잘 갖추면 수집 가능합니다.
            String url = CHART_URL + "/" + encodedSymbol + "?interval=1m&range=1d";

            RequestConfig requestConfig = RequestConfig.custom()
                    .setConnectTimeout(timeout)
                    .setSocketTimeout(timeout)
                    .build();

            try (CloseableHttpClient httpClient = HttpClients.custom()
                    .setDefaultRequestConfig(requestConfig)
                    .build()) {

                HttpGet request = new HttpGet(url);
                // 브라우저처럼 보이게 하기 위한 필수 헤더
                request.setHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
                request.setHeader("Referer", "https://finance.yahoo.com/");

                try (CloseableHttpResponse response = httpClient.execute(request)) {
                    String json = EntityUtils.toString(response.getEntity(), "UTF-8");
                    JsonNode root = mapper.readTree(json);
                    JsonNode result = root.path("chart").path("result");

                    if (result.isMissingNode() || !result.isArray() || result.size() == 0) {
                        return null;
                    }

                    JsonNode meta = result.get(0).path("meta");
                    double price         = meta.path("regularMarketPrice").asDouble();
                    double previousClose = meta.path("previousClose").asDouble();
                    double change        = price - previousClose;
                    double changePercent = previousClose > 0 ? (change / previousClose) * 100 : 0;

                    StockQuote quote = new StockQuote();
                    quote.setSymbol(meta.path("symbol").asText());
                    quote.setName(meta.path("shortName").asText());
                    quote.setPrice(price);
                    quote.setChange(change);
                    quote.setChangePercent(changePercent);
                    quote.setVolume(meta.path("regularMarketVolume").asLong());

                    // [중요] DART/SEC에서 주식수를 못 가져올 경우 시가총액을 활용하기 위해 저장
                    quote.setMarketCap(meta.path("marketCap").asLong());

                    quote.setAvgVolume10Day(meta.path("averageDailyVolume10Day").asLong());
                    quote.setHigh52Week(meta.path("fiftyTwoWeekHigh").asDouble());
                    quote.setLow52Week(meta.path("fiftyTwoWeekLow").asDouble());
                    quote.setMarket(meta.path("exchangeName").asText());

                    return quote;
                }
            }
        } catch (Exception e) {
            logger.warn("[{}] v8 조회 실패: {}", symbol, e.getMessage());
        }
        return null;
    }
}
