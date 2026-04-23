package co.kr.jigeum.yahoo;

import org.apache.http.client.CookieStore;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class YahooCrumbManager {
    private static final Logger logger = LoggerFactory.getLogger(YahooCrumbManager.class);

    private static String crumb;
    private static CookieStore cookieStore = new BasicCookieStore();

    public static void init() throws Exception {
        logger.info("Yahoo Finance Crumb 초기화 중...");

        // 쿠키 호환성 설정: 표준 정책(STANDARD)을 사용하여 날짜 형식 오류 방지
        RequestConfig globalConfig = RequestConfig.custom()
                .setCookieSpec(CookieSpecs.STANDARD)
                .build();

        String fullUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

        try (CloseableHttpClient client = HttpClients.custom()
                .setDefaultCookieStore(cookieStore)
                .setDefaultRequestConfig(globalConfig) // 쿠키 정책 적용
                .build()) {

            // 1단계: Yahoo Finance 메인 접속
            HttpGet mainPageGet = new HttpGet("https://finance.yahoo.com/");
            mainPageGet.setHeader("User-Agent", fullUserAgent);
            mainPageGet.setHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8");

            try (CloseableHttpResponse res = client.execute(mainPageGet)) {
                EntityUtils.consume(res.getEntity());
                // 로그에서 쿠키 개수가 1개보다 많아지는지 확인하세요.
                logger.debug("메인 페이지 접속 및 쿠키 수집 완료: {}개", cookieStore.getCookies().size());
            }

            // 2단계: Crumb 토큰 발급
            // URL을 query2로 변경하거나 세션을 명시적으로 유지
            HttpGet crumbGet = new HttpGet("https://query1.finance.yahoo.com/v1/test/getcrumb");
            crumbGet.setHeader("User-Agent", fullUserAgent);
            crumbGet.setHeader("Referer", "https://finance.yahoo.com/");

            try (CloseableHttpResponse res = client.execute(crumbGet)) {
                int statusCode = res.getStatusLine().getStatusCode();
                if (statusCode == 200) {
                    crumb = EntityUtils.toString(res.getEntity(), "UTF-8").trim();
                    logger.info("Crumb 발급 성공: {}", crumb);
                } else {
                    logger.error("Crumb 발급 실패! HTTP 상태 코드: {}", statusCode);
                    String errorBody = EntityUtils.toString(res.getEntity());
                    logger.error("에러 내용: {}", errorBody);
                }
            }
        }
    }

    public static String getCrumb() { return crumb; }
    public static CookieStore getCookieStore() { return cookieStore; }

    public static boolean isValid() {
        return crumb != null && !crumb.isEmpty();
    }
}
