package co.kr.jigeum.sec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * AAPL(예시) → CIK번호 변환용.
 * DART의 DartCorpCodeLoader랑 같은 역할.
 */
public class SecCikLoader {
    private static final Logger logger = LoggerFactory.getLogger(SecCikLoader.class);
    private static final Map<String, String> tickerToCik = new HashMap<>();

    static {
        try {
            load();
        } catch (Exception e) {
            logger.error("SEC CIK 로드 실패: {}", e.getMessage());
        }
    }

    private static void load() throws Exception {
        String url = "https://www.sec.gov/files/company_tickers.json";
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpGet get = new HttpGet(url);
            get.setHeader("User-Agent", "jigeum-app contact@jigeum.kr");
            try (CloseableHttpResponse res = client.execute(get)) {
                String body = EntityUtils.toString(res.getEntity(), "UTF-8");
                JsonNode root = new ObjectMapper().readTree(body);
                root.fields().forEachRemaining(entry -> {
                    String ticker = entry.getValue().path("ticker").asText().toUpperCase();
                    int cik = entry.getValue().path("cik_str").asInt();
                    tickerToCik.put(ticker, String.format("%010d", cik));
                });
                logger.info("SEC CIK 로드 완료: {}개", tickerToCik.size());
            }
        }

        // 누락 종목 수동보완
        tickerToCik.putIfAbsent("BRK-B", "0001067983");
    }

    public static String getCik(String ticker) {
        return tickerToCik.get(ticker.toUpperCase());
    }

}
