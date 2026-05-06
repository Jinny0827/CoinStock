package co.kr.jigeum.fred;

import co.kr.jigeum.common.config.Config;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * FRED(Federal Reserve Economic Data) API로 금리/VIX/WTI/금 실시간 수집.
 * 현재 handleEconomicInsight에 하드코딩된 값들을 교체하기 위해.
 *
 * 수집 지표:
 *
 * FEDFUNDS — 기준금리
 * VIXCLS — VIX 공포지수
 * DCOILWTICO — WTI 유가
 * GOLDAMGBD228NLBM — 금 가격
 */
public class FredClient {
    private static final Logger logger = LoggerFactory.getLogger(FredClient.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String BASE = "https://api.stlouisfed.org/fred/series/observations";

    private final String apiKey;

    public FredClient() {
        this.apiKey = Config.getInstance().get("fred.api.key", "");
    }

    /**
     * 특정 시리즈의 최신값 반환
     * @param seriesId FEDFUNDS, VIXCLS, DCOILWTICO, GOLDAMGBD228NLBM
     */
    public double fetchLatest(String seriesId) {
        try {
            String url = BASE
                    + "?series_id=" + seriesId
                    + "&api_key=" + apiKey
                    + "&file_type=json"
                    + "&sort_order=desc"
                    + "&limit=10"; // 최근 10개 중 유효값 탐색 (결측치 대비)

            try (CloseableHttpClient client = HttpClients.createDefault()) {
                HttpGet get = new HttpGet(url);
                get.setHeader("User-Agent", "jigeum-app contact@jigeum.kr");

                try (CloseableHttpResponse res = client.execute(get)) {
                    String body = EntityUtils.toString(res.getEntity(), "UTF-8");
                    JsonNode observations = mapper.readTree(body).path("observations");

                    // 최신값부터 탐색 (결측치 "." 스킵)
                    for (JsonNode obs : observations) {
                        String val = obs.path("value").asText();
                        if (!".".equals(val)) {
                            double result = Double.parseDouble(val);
                            logger.debug("[FRED] {} = {}", seriesId, result);
                            return result;
                        }
                    }
                }

            }
        } catch (Exception e) {
            logger.warn("[FRED] {} 조회 실패: {}", seriesId, e.getMessage());
        }

        return 0.0;
    }




}
