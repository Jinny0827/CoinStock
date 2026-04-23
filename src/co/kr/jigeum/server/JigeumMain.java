package co.kr.jigeum.server;

import co.kr.jigeum.common.config.Config;
import co.kr.jigeum.dart.DartCorpCodeLoader;
import co.kr.jigeum.scheduler.StockScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JigeumMain {
    private static final Logger logger = LoggerFactory.getLogger(JigeumMain.class);

    public static void main(String[] args) throws Exception {
        logger.info("  지금 (JIGEUM) 서버 시작");
        logger.info("=============================================");

        // 1. 설정 로딩
        logger.info("[1/4] 설정 로딩...");
        Config.getInstance();

        // 2. Yahoo Crumb 초기화 (추가된 부분)
        logger.info("[2/4] Yahoo Crumb 초기화...");
        co.kr.jigeum.yahoo.YahooCrumbManager.init();

        // 3. 스케줄러 및 데이터 로딩
        logger.info("[3/4] 스케줄러 시작...");
        DartCorpCodeLoader.load();
        StockScheduler.start();

        // 4. Netty 서버 시작
        logger.info("[4/4] Netty 서버 시작...");
        NettyServer server = new NettyServer();
        server.start();
    }
}