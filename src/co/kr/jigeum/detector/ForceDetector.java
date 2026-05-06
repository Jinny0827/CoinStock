package co.kr.jigeum.detector;

import co.kr.jigeum.scheduler.StockScheduler;
import co.kr.jigeum.yahoo.StockQuote;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 종목별 거래량 평균을 누적해두고, 현재 거래량/등락률이 임계값 초과하면 세력 의심 목록 반환
 *
  */
public class ForceDetector {
    private static final Logger logger = LoggerFactory.getLogger(ForceDetector.class);

    // 종목별 평균 거래량 누적 (지수평활법)
    private static final Map<String, Long> avgVolumeMap = new ConcurrentHashMap<>();
    private static final double ALPHA = 0.2; // 평활 계수 (낮을수록 장기 평균)

    public static List<StockQuote> detect(List<StockQuote> quotes) {
        List<StockQuote> result = new ArrayList<>();

        for (StockQuote q : quotes) {
            if (q.getVolume() <= 0) continue;

            // 당일 세션 EMA (재시작 시 현재값으로 초기화)
            long sessionAvg = avgVolumeMap.getOrDefault(q.getSymbol(), q.getVolume());

            // 조건 1: 야후 10일 평균 대비 3배 이상 (평소보다 오늘 자체가 많음)
            long avg10 = q.getAvgVolume10Day();
            boolean vsLongTerm = avg10 > 0 && q.getVolume() >= avg10 * 3;

            // 조건 2: 당일 세션 EMA 대비 1.5배 이상 (지금 이 순간 갑자기 터짐)
            boolean vsIntraday = q.getVolume() >= sessionAvg * 1.5;

            // 거래량 조건: 둘 중 하나라도 충족
            boolean volumeSpike = vsLongTerm || vsIntraday;

            // 주가 급등 등급 구분
            boolean extremeSpike = q.getChangePercent() >= 10.0; // 극단 급등 → 거래량 무관 즉시 감지
            boolean priceSpike   = q.getChangePercent() >= 3.0;

            if (extremeSpike || (volumeSpike && priceSpike)) {
                result.add(q);
            }

            // 세션 EMA 업데이트
            long newAvg = (long)(ALPHA * q.getVolume() + (1 - ALPHA) * sessionAvg);
            avgVolumeMap.put(q.getSymbol(), newAvg);
        }

        return result;
    }
}
