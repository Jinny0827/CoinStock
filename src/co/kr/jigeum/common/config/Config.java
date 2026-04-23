package co.kr.jigeum.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.util.Properties;

// 프로젝트 Configure 클래스
public class Config {
    private static final Logger logger = LoggerFactory.getLogger(Config.class);
    private static Config instance;
    private Properties properties = new Properties();

    private Config() {
        load();
    }

    public static Config getInstance() {
        if (instance == null) {
            // 동기화를 했는데도 null 하면 생성자 호출
            synchronized (Config.class) {
                if (instance == null) {
                    instance = new Config();
                }
            }
        }

        return instance;
    }

    // properties 파일 로드 처리
    private void load() {
        // JVM 옵션 -Dconfig=경로 로 지정
        String configPath = System.getProperty("config",
                "bin/jigeum.properties");

        try {
            properties.load(new FileInputStream(configPath));
            logger.info("설정 로딩 완료: {}", configPath);
        } catch (Exception e) {
            logger.error("설정 파일 로딩 실패: {}", configPath, e);
            throw new RuntimeException("설정 파일 로딩 실패: " + configPath);
        }
    }
    
    public String get(String key) {
        return properties.getProperty(key);
    }

    public String get(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(properties.getProperty(key));
        } catch (Exception e) {
            return defaultValue;
        }
    }

}
