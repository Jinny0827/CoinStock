package co.kr.jigeum.dart;

import co.kr.jigeum.common.config.Config;
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

import java.time.LocalDate;

public class DartClient {

    private static final Logger logger = LoggerFactory.getLogger(DartClient.class);
    private static final String BASE_URL = "https://opendart.fss.or.kr/api";
    private static final ObjectMapper mapper = new ObjectMapper();

    private final String apiKey;

    public DartClient() {
        this.apiKey = Config.getInstance().get("dart.api.key", "");
    }

    /**
     * 종목코드로 재무제표 조회
     * symbol 예: "005930.KS" → stockCode: "005930"
     */
    public FinancialData fetchFinancial(String symbol, StockQuote yahooQuote) {
        String stockCode = symbol.replace(".KS", "").replace(".KQ", "");
        String corpCode = DartCorpCodeLoader.getCorpCode(stockCode);
        if (corpCode == null) return null;

        // 탐색 전략 변경: 추정치(*4)보다는 팩트(사업보고서)를 먼저 찾습니다.
        // 사업보고서(11011) -> 3분기(11013) -> 반기(11012) -> 1분기(11014) 순서
        String[] reportCodes = {"11014", "11013", "11012", "11011"};

        FinancialData data = null;
        int currentYear = LocalDate.now().getYear();

        // 올해 데이터부터 역순 탐색
        for (String rCode : reportCodes) {
            data = fetchByYear(symbol, corpCode, currentYear, rCode, true);
            if (data != null) {
//                applyTtmCorrection(data, rCode);
                break;
            }
        }

        // 올해 데이터가 아예 없으면 작년 사업보고서 탐색
        if (data == null) {
            for (String rCode : reportCodes) {
                data = fetchByYear(symbol, corpCode, currentYear - 1, rCode, true);
                if (data != null) {
//                    applyTtmCorrection(data, rCode);
                    break;
                }
            }
        }

        // 주식수 역산 및 기본 정보 보강
        if (data != null) {
            if (data.getShares() <= 0 && yahooQuote != null) {
                long mCap = yahooQuote.getMarketCap();
                double price = yahooQuote.getPrice();

                if (mCap > 0 && price > 0) {
                    long calcShares = (long) (mCap / price);
                    data.setShares(calcShares);
                } else {
                    logger.warn("[{}] 주식수 역산 실패 - 야후 시총 데이터 없음", symbol);
                }
            }
        }



        return data;
    }

    private FinancialData fetchByYear(String symbol, String corpCode, int year, String reprtCode, boolean applyCorrection) {
        try {
            String url = BASE_URL + "/fnlttSinglAcnt.json"
                    + "?crtfc_key=" + apiKey
                    + "&corp_code=" + corpCode
                    + "&bsns_year=" + year
                    + "&reprt_code=" + reprtCode;

            try (CloseableHttpClient client = HttpClients.createDefault()) {
                HttpGet get = new HttpGet(url);
                try (CloseableHttpResponse response = client.execute(get)) {
                    String body = EntityUtils.toString(response.getEntity(), "UTF-8");
                    FinancialData data = parse(symbol, corpCode, body, year, reprtCode);

                    if (data != null && applyCorrection) {
                        applyTtmCorrection(data, reprtCode);
                    }
                    return data;
                }
            }
        } catch (Exception e) {
            return null;
        }
    }

    private FinancialData parse(String symbol, String corpCode, String body, int year, String reprtCode) {
        try {
            JsonNode root = mapper.readTree(body);
            if (!"000".equals(root.path("status").asText())) return null;

            JsonNode list = root.path("list");
            if (!list.isArray() || list.size() == 0) return null;

            FinancialData data = new FinancialData();
            data.setSymbol(symbol);
            data.setCorpCode(corpCode);
            data.setBsnsYear(year);

            String stockCode = symbol.replace(".KS", "").replace(".KQ", "");
            String corpName = DartCorpCodeLoader.getCorpName(stockCode);

            if (corpName == null || corpName.isEmpty()) {
                corpName = symbol;
            }
            data.setCorpName(corpName);

            for (JsonNode item : list) {
                String account = item.path("account_nm").asText().replace(" ", "");
                String fs      = item.path("fs_div").asText();
                String amountStr = item.path("thstrm_amount").asText().replace(",", "");


                if (!"CFS".equals(fs)) continue;

                long value = parseLong(item.path("thstrm_amount").asText().replace(",", ""));
                long prev  = parseLong(item.path("frmtrm_amount").asText().replace(",", ""));

                // 1. 순이익 매핑 (지배주주 우선)
                if (account.contains("지배기업") && account.contains("소유주") && account.contains("순이익")) {
                    // 가장 정확한 지배주주 순이익
                    data.setNetIncome(value);
                } else if (account.contains("지배주주순이익") || account.contains("지배기업지분순이익")) {
                    data.setNetIncome(value);
                } else if (account.equals("당기순이익(손실)") || account.equals("당기순이익")) {
                    // 지배주주 데이터가 아직 없을 때만 일반 순이익 세팅
                    if (data.getNetIncome() == 0) {
                        data.setNetIncome(value);
                    }
                }

                // 2. 자본총계 매핑 (지배주주 우선)
                else if (account.contains("지배기업") && account.contains("소유주") && account.contains("자본")) {
                    data.setTotalEquity(value);
                } else if (account.contains("지배주주자본") || account.contains("지배기업지분자본")) {
                    data.setTotalEquity(value);
                } else if (account.equals("자본총계")) {
                    if (data.getTotalEquity() == 0) {
                        data.setTotalEquity(value);
                    }
                }

                // 3. 매출 및 기타 지표
                else if (account.equals("매출액") || account.equals("수익(매출액)") || account.equals("영업수익")) {
                    data.setRevenue(value);
                    data.setPrevRevenue(prev);
                } else if (account.equals("영업이익") || account.equals("영업이익(손실)")) {
                    data.setOperatingIncome(value);
                } else if (account.equals("이자수익") && data.getRevenue() == 0) {
                    // 금융주 대응
                    data.setRevenue(value);
                    data.setPrevRevenue(prev);
                }
            }

            // 주식수 수집
            data.setShares(fetchSharesFromDart(symbol, corpCode, year, reprtCode));

            return data;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * DART에서만 주식수 조회
     */
    private long fetchSharesFromDart(String symbol, String corpCode, int year, String reprtCode) {
        // 1순위: 현재 조회 중인 보고서(예: 11014)에서 주식수 조회
        long shares = queryDartShares(corpCode, year, reprtCode);

        // 2순위: 실패 시, 해당 연도 사업보고서(11011)에서 조회
        if (shares <= 0 && !"11011".equals(reprtCode)) {
            shares = queryDartShares(corpCode, year, "11011");
        }

        // 3순위: 그래도 실패 시, 전년도 사업보고서에서 조회 (안정성 확보)
        if (shares <= 0) {
            shares = queryDartShares(corpCode, year - 1, "11011");
        }

        if (shares > 0) {
            logger.debug("[{}] DART 주식수 획득 성공: {}주", symbol, shares);
        }
        return shares;
    }

    private long queryDartShares(String corpCode, int year, String rCode) {
        try {
            // 주식의 총수 현황 API 사용
            String url = BASE_URL + "/stockTotqySttus.json"
                    + "?crtfc_key=" + apiKey
                    + "&corp_code=" + corpCode
                    + "&bsns_year=" + year
                    + "&reprt_code=" + rCode;

            try (CloseableHttpClient client = HttpClients.createDefault()) {
                HttpGet get = new HttpGet(url);
                try (CloseableHttpResponse response = client.execute(get)) {
                    String body = EntityUtils.toString(response.getEntity(), "UTF-8");
                    JsonNode root = mapper.readTree(body);
                    JsonNode list = root.path("list");

                    if (list.isArray()) {
                        for (JsonNode item : list) {
                            String se = item.path("se").asText();
                            // '보통주'라는 키워드가 포함된 행에서 '발행주식의 총수(istc_totqy)' 추출
                            if (se.contains("보통주")) {
                                String val = item.path("istc_totqy").asText().replace(",", "");
                                long res = parseLong(val);
                                if (res > 0) return res;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 로깅 생략 (재시도 로직이므로)
        }
        return 0L;
    }

    private long parseLong(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (Exception e) {
            return 0L;
        }
    }

    /**
     * 보고서 종류에 따라 연간(TTM) 수치로 환산
     * 올해 누적치 + (작년 전체 - 작년 동기 누적치)
     */
    private void applyTtmCorrection(FinancialData data, String reportCode) {
        // 11011(사업보고서)은 이미 1년치 풀 데이터이므로 보정 필요 없음
        if ("11011".equals(reportCode)) return;

        try {
            // 1. 현재 데이터는 '올해 누적' (예: 1분기 누적, 반기 누적 등)
            long currentAccumulatedNetIncome = data.getNetIncome();

            // 2. 작년 전체 실적을 가져옴 (필수 베이스 데이터)
            FinancialData lastYearFull = fetchByYear(data.getSymbol(), data.getCorpCode(), data.getBsnsYear() - 1, "11011", false);

            // 3. 작년 동기 누적 실적을 가져옴
            FinancialData lastYearSamePeriod = fetchByYear(data.getSymbol(), data.getCorpCode(), data.getBsnsYear() - 1, reportCode, false);

            if (lastYearFull != null && lastYearSamePeriod != null) {
                // TTM 공식 = 올해 누적 + (작년 전체 - 작년 동기 누적)
                long ttmNetIncome = currentAccumulatedNetIncome + (lastYearFull.getNetIncome() - lastYearSamePeriod.getNetIncome());
                long ttmRevenue = data.getRevenue() + (lastYearFull.getRevenue() - lastYearSamePeriod.getRevenue());
                long ttmOpIncome = data.getOperatingIncome() + (lastYearFull.getOperatingIncome() - lastYearSamePeriod.getOperatingIncome());

                data.setNetIncome(ttmNetIncome);
                data.setRevenue(ttmRevenue);
                data.setOperatingIncome(ttmOpIncome);

            } else {
                // 작년 데이터가 없을 경우에만 어쩔 수 없이 배수(Multiplier) 폴백
                logger.warn("[{}] 작년 데이터 부재로 배수 보정 처리", data.getSymbol());
                double multiplier = 1.0;
                switch (reportCode) {
                    case "11014": multiplier = 4.0; break;
                    case "11012": multiplier = 2.0; break;
                    case "11013": multiplier = 1.33; break;
                }
                data.setNetIncome((long)(data.getNetIncome() * multiplier));
            }
        } catch (Exception e) {
            logger.error("TTM 보정 중 오류: {}", e.getMessage());
        }
    }
}
