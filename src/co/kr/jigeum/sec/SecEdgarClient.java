package co.kr.jigeum.sec;

import co.kr.jigeum.dart.model.FinancialData;
import co.kr.jigeum.yahoo.StockQuote;
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
 * CIK로 SEC EDGAR companyfacts API 호출 → EPS/매출/순이익/자본 파싱.
 */
public class SecEdgarClient {
    private static final Logger logger = LoggerFactory.getLogger(SecEdgarClient.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String BASE = "https://data.sec.gov/api/xbrl/companyfacts/";


    public FinancialData fetch(String symbol, StockQuote quote) {
        String cik = SecCikLoader.getCik(symbol);
        if(cik == null) {
            logger.warn("[SEC] CIK 없음: {}", symbol);
            return null;
        }

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpGet get = new HttpGet(BASE + "CIK" + cik + ".json");
            get.setHeader("User-Agent", "jigeum-app contact@jigeum.kr");

            try (CloseableHttpResponse res = client.execute(get)) {
                String body = EntityUtils.toString(res.getEntity(), "UTF-8");
                JsonNode facts = mapper.readTree(body).path("facts").path("us-gaap");

                FinancialData data = new FinancialData();
                data.setSymbol(symbol);
                data.setCorpName(quote != null ? quote.getName() : symbol);

                // EPS (기본 주당 이익, TTM 10-K 기준)
                double eps = latestAnnual(facts, "EarningsPerShareBasic");
                data.setDartEps(eps);

                // 매출
                long rev = (long) latestAnnual(facts, "RevenueFromContractWithCustomerExcludingAssessedTax");
                if (rev == 0) rev = (long) latestAnnual(facts, "Revenues");
                data.setRevenue(rev);

                // 전년 매출 (성장률용)
                long prevRev = (long) prevAnnual(facts, "RevenueFromContractWithCustomerExcludingAssessedTax");
                if (prevRev == 0) prevRev = (long) prevAnnual(facts, "Revenues");
                data.setPrevRevenue(prevRev);

                // 순이익
                long net = (long) latestAnnual(facts, "NetIncomeLoss");
                data.setNetIncome(net);

                // 영업이익
                long op = (long) latestAnnual(facts, "OperatingIncomeLoss");
                data.setOperatingIncome(op);

                // 자본 (BPS용)
                long equity = (long) latestAnnual(facts, "StockholdersEquity");
                data.setTotalEquity(equity);

                // 주식수
                long shares = (long) latestAnnual(facts, "CommonStockSharesOutstanding");
                data.setShares(shares);

                logger.info("[SEC] {} EPS={} rev={} net={}", symbol, eps, rev, net);
                return data;
            }
        } catch (Exception e) {
            logger.warn("[SEC] {} 실패: {}", symbol, e.getMessage());
        }
        return null;
    }

    // 가장 최근 10-K annual 값
    private double latestAnnual(JsonNode facts, String tag) {
        try {
            JsonNode units = facts.path(tag).path("units");
            JsonNode arr = units.isObject() ? units.elements().next() : null;
            if (arr == null || !arr.isArray()) return 0;

            JsonNode best = null;
            for (JsonNode item : arr) {
                if (!"10-K".equals(item.path("form").asText())) continue;
                if (best == null || item.path("end").asText().compareTo(best.path("end").asText()) > 0)
                    best = item;
            }
            return best != null ? best.path("val").asDouble() : 0;
        } catch (Exception e) { return 0; }
    }

    // 전년도 10-K 값 (성장률 계산용)
    private double prevAnnual(JsonNode facts, String tag) {
        try {
            JsonNode units = facts.path(tag).path("units");
            JsonNode arr = units.isObject() ? units.elements().next() : null;
            if (arr == null || !arr.isArray()) return 0;

            String latest = null, prev = null;
            for (JsonNode item : arr) {
                if (!"10-K".equals(item.path("form").asText())) continue;
                String end = item.path("end").asText();
                if (latest == null || end.compareTo(latest) > 0) { prev = latest; latest = end; }
            }
            if (prev == null) return 0;
            for (JsonNode item : arr) {
                if ("10-K".equals(item.path("form").asText()) && prev.equals(item.path("end").asText()))
                    return item.path("val").asDouble();
            }
        } catch (Exception e) {}
        return 0;
    }
}
