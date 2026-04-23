package co.kr.jigeum.groq;

import co.kr.jigeum.common.config.Config;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GroqClient {

    private static final Logger logger = LoggerFactory.getLogger(GroqClient.class);

    private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String MODEL = "llama-3.3-70b-versatile";
    private static final ObjectMapper mapper = new ObjectMapper();

    private final String apiKey;

    public GroqClient() {
        this.apiKey = Config.getInstance().get("groq.api.key", "");
    }

    /**
     * Groq에 메시지를 보내고 응답 텍스트를 반환
     */
    public String chat(String prompt) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            logger.warn("Groq API key not configured");
            return "AI 분석 불가 (API 키 미설정)";
        }

        try {
            // 요청 JSON 구성
            ObjectNode body = mapper.createObjectNode();
            body.put("model", MODEL);
            body.put("temperature", 0.7);
            body.put("max_tokens", 1024);

            ArrayNode messages = mapper.createArrayNode();
            ObjectNode userMsg = mapper.createObjectNode();

            userMsg.put("role", "user");
            userMsg.put("content", prompt);
            messages.add(userMsg);
            body.set("messages", messages);

            String requestBody = mapper.writeValueAsString(body);
            logger.debug("Groq request: {}", requestBody);

            RequestConfig requestConfig = RequestConfig.custom()
                    .setConnectTimeout(10000)
                    .setSocketTimeout(30000)
                    .build();

            try(CloseableHttpClient httpClient = HttpClients.custom()
                    .setDefaultRequestConfig(requestConfig)
                    .build()) {

                HttpPost post = new HttpPost(GROQ_URL);
                post.setHeader("Authorization", "Bearer " + apiKey);
                post.setHeader("Content-Type", "application/json");
                post.setEntity(new StringEntity(requestBody, "UTF-8"));

                try (CloseableHttpResponse response = httpClient.execute(post)) {
                    int statusCode = response.getStatusLine().getStatusCode();
                    String responseBody = EntityUtils.toString(response.getEntity(), "UTF-8");

                    if (statusCode != 200) {
                        logger.error("Groq API error: status={}, body={}", statusCode, responseBody);
                        return "AI 분석 실패 (HTTP " + statusCode + ")";
                    }

                    JsonNode json = mapper.readTree(responseBody);
                    String content = json
                            .path("choices").get(0)
                            .path("message")
                            .path("content")
                            .asText("");

                    logger.debug("Groq response: {}", content);
                    return content;
                }

            }

        } catch (Exception e) {
            logger.error("Groq API call failed: {}", e.getMessage(), e);
            return "AI 분석 중 오류 발생";
        }
    }


}
