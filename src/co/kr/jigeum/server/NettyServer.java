package co.kr.jigeum.server;

import co.kr.jigeum.common.config.Config;
import co.kr.jigeum.handler.CorsHandler;
import co.kr.jigeum.handler.RouterHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NettyServer {

    private static final Logger logger = LoggerFactory.getLogger(NettyServer.class);

    public void start() throws Exception {
        // 프로퍼티 값과 기본값
        int port        = Config.getInstance().getInt("server.http.port", 8080);
        int bossCount   = Config.getInstance().getInt("server.boss.threads", 1);
        int workerCount = Config.getInstance().getInt("server.worker.threads", 2);
        int execCount   = Config.getInstance().getInt("server.executor.threads", 4);

        EventLoopGroup bossGroup = new NioEventLoopGroup(bossCount);
        EventLoopGroup workerGroup = new NioEventLoopGroup(workerCount);
        EventExecutorGroup executorGroup = new DefaultEventExecutorGroup(execCount);

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline pipeline = ch.pipeline();

                            // HTTP 코덱
                            pipeline.addLast(new HttpServerCodec());
                            // 최대 요청 크기 1MB
                            pipeline.addLast(new HttpObjectAggregator(1024 * 1024));
                            // CORS 처리
                            pipeline.addLast(new CorsHandler());
                            // 라우터 (비즈니스 로직 - 별도 스레드풀)
                            pipeline.addLast(executorGroup, new RouterHandler());
                        }
                    });

            Channel ch = bootstrap.bind(port).sync().channel();
            logger.info("=============================================");
            logger.info("  지금 서버 구동 완료 → http://localhost:{}", port);
            logger.info("=============================================");
            ch.closeFuture().sync();


        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
            executorGroup.shutdownGracefully();
        }

    }


}
