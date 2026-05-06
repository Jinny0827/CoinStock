package co.kr.jigeum.websocket;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * WebSocket 연결/해제/메시지 수신 처리.
 * /ws 경로로 업그레이드 요청 처리.
 */
public class WebSocketHandler extends SimpleChannelInboundHandler<WebSocketFrame> {
    private static final Logger logger = LoggerFactory.getLogger(WebSocketHandler.class);

    /**
     * WebSocket 핸드셰이크 완료 후 채널 등록.
     * channelActive 대신 이걸 써야 HTTP→WS 업그레이드 완료 시점에만 추가됨.
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            WebSocketSessionStore.getInstance().add(ctx.channel());
            logger.info("[WS] 클라이언트 연결: {} (총 {}명)",
                    ctx.channel().remoteAddress(),
                    WebSocketSessionStore.getInstance().size());
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        WebSocketSessionStore.getInstance().remove(ctx.channel());
        logger.info("[WS] 클라이언트 해제: {} (총 {}명)",
                ctx.channel().remoteAddress(),
                WebSocketSessionStore.getInstance().size());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.warn("[WS] 오류: {}", cause.getMessage());
        ctx.close();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
        if (frame instanceof PingWebSocketFrame) {
            // Ping → Pong 응답
            ctx.writeAndFlush(new PongWebSocketFrame(frame.content().retain()));
        } else if (frame instanceof CloseWebSocketFrame) {
            ctx.close();
        }
        // 클라이언트 → 서버 메시지는 현재 무시 (단방향 Push)
    }
}
