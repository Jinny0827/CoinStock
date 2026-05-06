package co.kr.jigeum.websocket;

import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;

/**
 * 연결된 클라이언트 채널 목록 관리.
 * 시세 Push할 때 여기서 전체 클라이언트에게 브로드캐스트.
 */
public class WebSocketSessionStore {
    private static final WebSocketSessionStore instance = new WebSocketSessionStore();
    public static WebSocketSessionStore getInstance() { return instance; }
    
    // 연결된 모든 클라이언트 채널 관리(자동 정리 지원)
    private final ChannelGroup channels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    public void add(Channel channel) {
        channels.add(channel);
    }

    public void remove(Channel channel) {
        channels.remove(channel);
    }

    public void broadcast(String message) {
        channels.writeAndFlush(
                new io.netty.handler.codec.http.websocketx.TextWebSocketFrame(message)
        );
    }

    public int size() { return channels.size(); }
}
