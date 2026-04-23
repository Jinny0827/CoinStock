package co.kr.jigeum.groq;

import co.kr.jigeum.yahoo.StockQuote;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InsightGenerator {

    private static final Logger logger = LoggerFactory.getLogger(InsightGenerator.class);

    private final GroqClient groqClient;

    public InsightGenerator() {
        this.groqClient = new GroqClient();
    }

    /**
     * 세력 감지 종목 AI 인사이트 생성
     */
    public String generateThemeInsight(StockQuote quote, String theme) {
        String prompt = buildThemePrompt(quote, theme);
        logger.info("테마 인사이트 요청: {} ({})", quote.getSymbol(), theme);
        return groqClient.chat(prompt);
    }

    /**
     * 실적주 AI 인사이트 생성
     */
    public String generateFinancialInsight(StockQuote quote,
                                           double eps, double per, double pbr,
                                           double revenueGrowth, double operatingMargin) {
        // PER이 0인 경우(적자 등) 처리를 위한 로직 보강
        String perValue = (per <= 0) ? "산출 불가(최근 적자 발생 혹은 데이터 부재)" : String.format("%.2f배", per);

        String prompt = buildFinancialPrompt(quote, eps, perValue, pbr, revenueGrowth, operatingMargin);
        logger.info("공시 기반 실적 인사이트 요청: {}", quote.getSymbol());
        return groqClient.chat(prompt);
    }

    /**
     * 용어 해석 생성 (PER, PBR, EPS 등)
     */
    public String explainTerm(String term, double value, String industryAvg) {
        String prompt = String.format(
                "주식 용어 '%s'를 초보자에게 설명해주세요.\n\n" +
                        "현재 값: %.2f / 업종 평균: %s\n\n" +
                        "이 수치는 공식 공시를 통해 산출된 결과입니다. " +
                        "일상생활의 비유(예: 마트 물건 가격, 연봉 등)를 들어 이 수치가 현재 매력적인 수준인지 2~3문장으로 쉽게 설명하세요.",
                term, value, industryAvg
        );
        return groqClient.chat(prompt);
    }

    /**
     * 경제 국면 판단
     */
    public String analyzeEconomicPhase(double interestRate, double usdKrw,
                                       double vix, double wtiOil, double gold) {
        String prompt = buildEconomicPrompt(interestRate, usdKrw, vix, wtiOil, gold);
        logger.info("경제 국면 분석 요청");
        return groqClient.chat(prompt);
    }

    private String buildThemePrompt(StockQuote quote, String theme) {
        return String.format(
                "주식 종목 분석을 요청합니다. 주식을 잘 모르는 일반인도 이해할 수 있게 설명해주세요.\n\n" +
                        "종목명: %s (%s)\n" +
                        "현재가: %.0f원\n" +
                        "등락률: %.2f%%\n" +
                        "거래량: %,d\n" +
                        "52주 최저: %.0f원\n" +
                        "현재 주목 테마: %s\n\n" +
                        "이 종목이 '%s' 테마와 어떤 연관이 있는지, 왜 지금 주목받을 수 있는지 " +
                        "3문장 이내로 쉽게 설명해주세요. 한국어로 답변.",
                quote.getName(), quote.getSymbol(),
                quote.getPrice(),
                quote.getChangePercent(),
                quote.getVolume(),
                quote.getLow52Week(),
                theme, theme
        );
    }

    private String buildFinancialPrompt(StockQuote quote, double eps, String per, double pbr,
                                        double revenueGrowth, double operatingMargin) {

        // PBR 예외 처리 (자본잠식 등)
        String pbrValue = (pbr <= 0) ? "산출 불가(자본 잠식 등)" : String.format("%.2f배", pbr);

        return String.format(
                "당신은 전문 주식 분석가입니다. 아래 제공된 [공식 공시 기반 데이터]를 바탕으로 투자 인사이트를 작성하세요.\n" +
                        "일반인도 이해하기 쉬운 언어를 사용하되, 분석은 전문적이어야 합니다.\n\n" +
                        "1. 분석 대상: %s (%s)\n" +
                        "2. 데이터 출처: 한국 금감원(DART) 및 미국 SEC 최신 공시 자료 (시스템 직접 산출)\n" +
                        "3. 실시간 주가: %.0f원\n" +
                        "4. 주요 지표 (최근 12개월 합산 TTM 기준):\n" +
                        "   - EPS (주당순이익): %.2f\n" +
                        "   - PER (주가수익비율): %s\n" +
                        "   - PBR (주가순자산비율): %s\n" +
                        "   - 매출 성장률: %.2f%%\n" +
                        "   - 영업이익률: %.2f%%\n\n" +
                        "요청 사항:\n" +
                        "- 이 데이터가 최신 공시를 바탕으로 직접 계산된 '현재'의 수치임을 언급하며 분석을 시작하세요.\n" +
                        "- 현재 실적이 업종 내에서 어떤 위치인지, 투자자가 '지금' 주목해야 할 핵심 포인트는 무엇인지 3문장 이내로 설명하세요.\n" +
                        "- 한국어로 답변.",
                quote.getName(), quote.getSymbol(),
                quote.getPrice(),
                eps, per, pbrValue,
                revenueGrowth, operatingMargin
        );
    }

    private String buildTermPrompt(String term, double value, String industryAvg) {
        return String.format(
                "주식 용어를 주식을 전혀 모르는 사람도 이해할 수 있게 설명해주세요.\n\n" +
                        "용어: %s\n" +
                        "현재 값: %.2f\n" +
                        "업종 평균: %s\n\n" +
                        "이 수치가 무엇을 의미하는지, 현재 값이 좋은 건지 나쁜 건지 " +
                        "일상적인 비유를 들어 2~3문장으로 설명해주세요. 한국어로 답변.",
                term, value, industryAvg
        );
    }

    private String buildEconomicPrompt(double interestRate, double usdKrw,
                                       double vix, double wtiOil, double gold) {
        return String.format(
                "현재 경제 지표를 분석해서 투자 관점의 경제 국면을 판단해주세요.\n\n" +
                        "미국 기준금리: %.2f%%\n" +
                        "원달러 환율: %.0f원\n" +
                        "VIX 공포지수: %.2f\n" +
                        "WTI 유가: $%.2f\n" +
                        "금 가격: $%.2f\n\n" +
                        "현재 어떤 경제 국면인지 (금리인상기/금리인하기/경기침체/경기회복 등), " +
                        "어떤 섹터가 유리한지 3~4문장으로 설명해주세요. 한국어로 답변.",
                interestRate, usdKrw, vix, wtiOil, gold
        );
    }


}
