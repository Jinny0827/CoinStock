package co.kr.jigeum.dart;

import co.kr.jigeum.common.config.Config;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

// 한국시장 (DART API)
public class DartCorpCodeLoader {

    private static final Logger logger = LoggerFactory.getLogger(DartCorpCodeLoader.class);
    private static final String CORP_CODE_URL = "https://opendart.fss.or.kr/api/corpCode.xml";

    // 종목 코드 / 종목 명
    private static Map<String, String> stockToCorpCode = new HashMap<>();
    private static Map<String, String> stockToCorpName = new HashMap<>();

    // 초기화 여부
    private static boolean loaded = false;


    /**
     * DART에서 전체 corp_code 매핑 다운로드 및 파싱
     * 서버 시작 시 1회 호출
     */
    public static void load() {
        String apiKey = Config.getInstance().get("dart.api.key", "");
        if(apiKey.isEmpty()) {
            logger.warn("DART API 키 미설정 - corp_code 로딩 스킵");
            return;
        }

        try {
            logger.info("DART corp_code 다운로드 시작");

            String url = CORP_CODE_URL + "?crtfc_key=" + apiKey;
            try (CloseableHttpClient client = HttpClients.createDefault()) {
                HttpGet get = new HttpGet(url);
                try (CloseableHttpResponse response = client.execute(get)) {
                    byte[] zipBytes = EntityUtils.toByteArray(response.getEntity());
                    byte[] xmlBytes = unzip(zipBytes);
                    parseXml(xmlBytes);
                }
            }

            loaded = true;
            logger.info("DART corp_code 로딩 완료 - {}개 종목 매핑", stockToCorpCode.size());

        } catch (Exception e) {
            logger.error("DART corp_code 로딩 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * 종목코드로 corp_code 조회
     * 예: "005930" → "00126380"
     */
    public static String getCorpCode(String stockCode) {
        return stockToCorpCode.getOrDefault(stockCode, null);
    }

    public static String getCorpName(String stockCode) {
        return stockToCorpName.getOrDefault(stockCode, "");
    }

    public static boolean isLoaded() {
        return loaded;
    }

    // ZIP -> XML 바이트 추출
    private static byte[] unzip(byte[] zipBytes) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while((entry = zis.getNextEntry()) != null) {
                if(entry.getName().endsWith(".xml")) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    int len;
                    while ((len = zis.read(buf)) != -1) {
                        baos.write(buf, 0, len);
                    }

                    return baos.toByteArray();
                }
            }
        }
        
        throw new Exception("ZIP 내 XML 파일 없음");
    }

    // XML 파싱 -> stockToCorpCode 맵 생성
    private static void parseXml(byte[] xmlBytes) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(new ByteArrayInputStream(xmlBytes));

        NodeList list = doc.getElementsByTagName("list");
        for (int i = 0; i < list.getLength(); i++) {
            Element el = (Element) list.item(i);
            String stockCode = getText(el, "stock_code").trim();
            String corpCode = getText(el, "corp_code").trim();
            String corpName = getText(el, "corp_name").trim();

            // 상장 종목만
            if(!stockCode.isEmpty()) {
                stockToCorpCode.put(stockCode, corpCode);
                stockToCorpName.put(stockCode, corpName);
            }
        }
    }

    private static String getText(Element el, String tag) {
        NodeList list = el.getElementsByTagName(tag);
        if(list.getLength() > 0) {
            return list.item(0).getTextContent();
        }

        return"";
    }

}
