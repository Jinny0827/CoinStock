package co.kr.jigeum.handler;

import co.kr.jigeum.common.config.Config;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CorsHandler extends ChannelInboundHandlerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(CorsHandler.class);
    private static final String ALLOWED_ORIGIN = Config.getInstance().get("cors.allowed.origin", "http://localhost:5173");

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof FullHttpMessage)) {
            ctx.fireChannelRead(msg);
            return;
        }

        FullHttpRequest req = (FullHttpRequest) msg;

        // OPTIONS 프리플라이트 요청 처리
        if (req.method().equals(HttpMethod.OPTIONS)) {
            DefaultFullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            addCorsHeaders(response);
            ctx.writeAndFlush(response);
            return;
        }

        // 다음 핸들러로 전달
        ctx.fireChannelRead(msg);
    }

    public static void addCorsHeaders(io.netty.handler.codec.http.HttpResponse response) {
        response.headers().set("Access-Control-Allow-Origin", ALLOWED_ORIGIN);
        response.headers().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        response.headers().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        response.headers().set("Access-Control-Max-Age", "3600");
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("CorsHandler 오류: {}", cause.getMessage());
        ctx.close();
    }
}
