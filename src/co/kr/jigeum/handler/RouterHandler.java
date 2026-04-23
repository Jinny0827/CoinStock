package co.kr.jigeum.handler;

import co.kr.jigeum.cache.FinancialStore;
import co.kr.jigeum.cache.StockDataStore;
import co.kr.jigeum.cache.ThemeStore;
import co.kr.jigeum.dart.model.FinancialData;
import co.kr.jigeum.groq.InsightGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;



public class RouterHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private static final Logger logger = LoggerFactory.getLogger(RouterHandler.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) throws Exception {
        // 서버에서 메시지 수신 후 콜백 처리
        String uri = req.uri();
        String method = req.method().name();

        logger.info("[{}] {}", method, uri);

        // URI 파싱 (쿼리스트링 제거)
        String path = uri.contains("?") ? uri.substring(0, uri.indexOf("?")) : uri;

        // 헬스 체크
        if (path.equals("/health")) {
            sendJson(ctx, HttpResponseStatus.OK, Map.of(
                    "status", "UP",
                    "service", "지금 (JIGEUM)"
            ));
            return;
        }

        if (path.equals("/api/stocks/current")) {
            sendJson(ctx, HttpResponseStatus.OK, Map.of(
                    "code", "0000",
                    "data", StockDataStore.getInstance().getAll(),
                    "total", StockDataStore.getInstance().size(),
                    "lastUpdated", StockDataStore.getInstance().getLastUpdated()
            ));
            return;
        }

        if (path.equals("/api/market/kr")) {
            sendJson(ctx, HttpResponseStatus.OK, Map.of(
                    "code", "0000",
                    "data", StockDataStore.getInstance().getKr()
            ));
            return;
        }

        if (path.equals("/api/market/us")) {
            sendJson(ctx, HttpResponseStatus.OK, Map.of(
                    "code", "0000",
                    "data", StockDataStore.getInstance().getUs()
            ));
            return;
        }

        if (path.equals("/api/market/index")) {
            sendJson(ctx, HttpResponseStatus.OK, Map.of(
                    "code", "0000",
                    "data", StockDataStore.getInstance().getIndex()
            ));
            return;
        }

        // 테마 목록
        if (path.equals("/api/themes")) {
            handleThemes(ctx);
            return;
        }

        // 용어 해석
        if (path.startsWith("/api/insight/term")) {
            handleTermInsight(ctx, req);
            return;
        }

        // 경제 국면
        if (path.equals("/api/insight/economic")) {
            handleEconomicInsight(ctx);
            return;
        }
        
        // 종목 재무 데이터
        if (path.startsWith("/api/financial/")) {
            String symbol = path.substring("/api/financial/".length());
            handleFinancial(ctx, symbol);
            return;
        }

        // 실적주 스크리너
        if (path.equals("/api/screener/value")) {
            handleValueScreener(ctx);
            return;
        }


        // 404
        sendJson(ctx, HttpResponseStatus.NOT_FOUND, Map.of(
                "code", "404",
                "message", "요청한 경로를 찾을 수 없습니다: " + path
        ));
    }

    private void handleThemes(ChannelHandlerContext ctx) throws Exception {
        ThemeStore store = ThemeStore.getInstance();
        sendJson(ctx, HttpResponseStatus.OK, Map.of(
                "themes", store.getThemes(),
                "lastUpdated", store.getLastUpdated() != null ? store.getLastUpdated().toString() : "",
                "code", "0000"
        ));
    }

    private void handleTermInsight(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        QueryStringDecoder decoder = new QueryStringDecoder(request.uri());
        String term = getParam(decoder, "term", "PER");
        String valueStr = getParam(decoder, "value", "0");
        String avg = getParam(decoder, "avg", "업종평균 미제공");

        double value;
        try {
            value = Double.parseDouble(valueStr);
        } catch (NumberFormatException e) {
            value = 0;
        }

        String insight = new InsightGenerator().explainTerm(term, value, avg);

        sendJson(ctx, HttpResponseStatus.OK, Map.of(
                "term", term,
                "value", value,
                "insight", insight,
                "code", "0000"
        ));
    }

    // 환율, vix, wti유가, 금값 등 FRED 적용 후 진행 예정
    private void handleEconomicInsight(ChannelHandlerContext ctx) throws Exception {
        double interestRate = 4.25;
        double usdKrw = 1380.0;
        double vix = 18.5;
        double wtiOil = 72.0;
        double gold = 3300.0;

        String insight = new InsightGenerator().analyzeEconomicPhase(interestRate, usdKrw, vix, wtiOil, gold);

        sendJson(ctx, HttpResponseStatus.OK, Map.of(
                "interestRate", interestRate,
                "usdKrw", usdKrw,
                "vix", vix,
                "wtiOil", wtiOil,
                "gold", gold,
                "insight", insight,
                "code", "0000"
        ));
    }

    // 재무 분석 데이터 호출
    private void handleFinancial(ChannelHandlerContext ctx, String symbol) throws Exception {
        FinancialData data = FinancialStore.getInstance().get(symbol);
        if(data == null) {
            sendJson(ctx, HttpResponseStatus.NOT_FOUND, Map.of(
                    "code", "404",
                    "message", "재무데이터 없음: " + symbol
            ));
            return;
        }

        sendJson(ctx, HttpResponseStatus.OK, Map.of(
                "code", "0000",
                "symbol", data.getSymbol(),
                "corpName", data.getCorpName() != null ? data.getCorpName() : "",
                "bsnsYear", data.getBsnsYear(),
                "eps", data.getEps(),
                "per", data.getPer(),
                "pbr", data.getPbr(),
                "revenueGrowth", data.getRevenueGrowth(),
                "operatingMargin", data.getOperatingMargin()
        ));
    }

    private void handleValueScreener(ChannelHandlerContext ctx) throws Exception {
        List<FinancialData> all = FinancialStore.getInstance().getAll();
        // 종목, 종목별데이터의 Map형태 리스트 데이터
        List<Map<String, Object>> result = new ArrayList<>();

        for (FinancialData fd : all) {
            // 실적주 조건: PBR < 1 or PER < 10, 매출성장률 > 0
            boolean lowPbr = fd.getPbr() > 0 && fd.getPbr() < 1.0;
            boolean lowPer = fd.getPer() > 0 && fd.getPer() < 10.0;
            boolean growing = fd.getRevenueGrowth() > 0;

            if ((lowPbr || lowPer) && growing) {
                result.add(Map.of(
                        "symbol", fd.getSymbol(),
                        "eps", fd.getEps(),
                        "per", fd.getPer(),
                        "pbr", fd.getPbr(),
                        "revenueGrowth", fd.getRevenueGrowth(),
                        "operatingMargin", fd.getOperatingMargin()
                ));
            }
        }

        sendJson(ctx, HttpResponseStatus.OK, Map.of(
                "code", "0000",
                "data", result,
                "total", result.size()
        ));
        
    }


    // 쿼리 파라미터 쪼개서 전달
    private String getParam(QueryStringDecoder decoder, String key, String defaultVal) {
        List<String> vals = decoder.parameters().get(key);
        return (vals != null && !vals.isEmpty()) ? vals.get(0) : defaultVal;
    }

    private void sendJson(ChannelHandlerContext ctx, HttpResponseStatus status, Object body) throws Exception {
        String json = mapper.writeValueAsString(body);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

        DefaultFullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, status,
                Unpooled.wrappedBuffer(bytes));

        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, bytes.length);

        // CORS 헤더 추가
        CorsHandler.addCorsHeaders(response);

        ctx.writeAndFlush(response);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("RouterHandler 오류: {}", cause.getMessage());
        ctx.close();
    }
}
