package co.kr.jigeum.groq;

import co.kr.jigeum.cache.ThemeStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ThemeAnalyzer {
    private static final Logger logger = LoggerFactory.getLogger(ThemeAnalyzer.class);

    private final GroqClient groqClient;

    public ThemeAnalyzer() {
        this.groqClient = new GroqClient();
    }

    /**
     * Groq에게 오늘의 핫 테마 5개를 요청하고 ThemeStore에 저장
     */
    public void refreshThemes() {
        ThemeStore store = ThemeStore.getInstance();

        // 오늘 이미 조회했으면 스킵
        if (store.isToday()) {
            logger.debug("테마 이미 오늘 업데이트됨, 스킵");
            return;
        }

        logger.info("Groq 테마 분류 요청 시작");

        String prompt = buildPrompt();
        String response = groqClient.chat(prompt);

        List<String> themes = parseThemes(response);

        if (themes.isEmpty()) {
            logger.warn("테마 파싱 실패, 기본값 사용. Groq 응답: {}", response);
            themes = defaultThemes();
        }

        store.save(themes);
        logger.info("테마 업데이트 완료: {}", themes);
    }

    private String buildPrompt() {
        return "오늘 한국 주식시장과 미국 주식시장에서 가장 주목받고 있는 투자 테마 5개를 선정해줘.\n" +
                "최근 뉴스, 정책, 글로벌 이슈를 반영해서 현재 시장에서 실제로 자금이 몰리는 섹터 기준으로.\n\n" +
                "반드시 아래 형식으로만 답해줘. 설명 없이 쉼표로 구분된 5개 단어만:\n" +
                "AI반도체,방산,2차전지,바이오,로봇";
    }


    /**
     * Groq 응답에서 테마 리스트 파싱
     * 예상 형식: "AI반도체,방산,2차전지,바이오,로봇"
     */
    private List<String> parseThemes(String response) {
        List<String> result = new ArrayList<>();

        try {
            String cleaned = response.trim()
                    .replaceAll("\\n", "")
                    .replaceAll("\\s+", "");

            String[] parts = cleaned.split(",");
            for (String part : parts) {
                String theme = part.trim();
                if (!theme.isEmpty() && theme.length() <= 20) {
                    result.add(theme);
                }
            }

            // 5개가 아니면 실패로 처리
            if (result.size() < 3) {
                return new ArrayList<>();
            }


        } catch (Exception e) {
            logger.error("테마 파싱 오류: {}", e.getMessage());
        }

        return result;
    }

    // 디폴트 테마
    private List<String> defaultThemes() {
        return Arrays.asList("AI반도체", "방산", "2차전지", "바이오", "로봇");
    }

}
