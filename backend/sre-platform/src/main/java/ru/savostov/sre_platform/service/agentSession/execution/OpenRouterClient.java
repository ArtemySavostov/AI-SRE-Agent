package ru.savostov.sre_platform.service.agentSession.execution;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "sre.agent-session.provider", havingValue = "openrouter")
public class OpenRouterClient {
    private final ObjectMapper mapper;
    private final HttpClient http;
    private final String apiKey;
    private final String model;
    private final Duration timeout;

    @Autowired
    public OpenRouterClient(ObjectMapper mapper,
            @Value("${sre.openrouter.api-key:}") String apiKey,
            @Value("${sre.openrouter.model:openrouter/free}") String model,
            @Value("${sre.openrouter.timeout-seconds:60}") int timeoutSeconds) {
        this(mapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), apiKey, model, timeoutSeconds);
    }

    public OpenRouterClient(ObjectMapper mapper, HttpClient http, String apiKey, String model, int timeoutSeconds) {
        if (model.isBlank() || timeoutSeconds < 1 || timeoutSeconds > 300)
            throw new IllegalArgumentException("OpenRouter: model must be set, timeout must be 1–300 seconds");
        this.mapper = mapper;
        this.http = http;
        this.apiKey = apiKey.trim();
        this.model = model;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
    }

    public String model() { return model; }

    public String generate() {
        if (apiKey.isBlank()) throw new Failure("Не задан OPENROUTER_API_KEY в окружении backend");
        var body = Map.of("model", model, "stream", false, "max_tokens", 4096,
                "messages", List.of(
                    Map.of("role", "system", "content", "Ты SRE-помощник. Ответь по-русски кратко, не более 8 пунктов. У тебя нет доступа к инфраструктуре. Не утверждай, что проверял сервис, читал логи или выполнял команды."),
                    Map.of("role", "user", "content", "Тест подключения агента. Кратко опиши, какие данные нужны для диагностики Spring Boot приложения в Docker: состояние, логи, метрики и конфигурация. Это общий план, а не диагностика реального сервиса.")));
        try {
            var request = HttpRequest.newBuilder(URI.create("https://openrouter.ai/api/v1/chat/completions"))
                    .timeout(timeout).header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw statusFailure(response.statusCode());
            var json = mapper.readTree(response.body());
            if (json.has("error")) throw statusFailure(json.path("error").path("code").asInt(502));
            var choice = json.path("choices").path(0);
            if ("length".equals(choice.path("finish_reason").asString("")))
                throw new Failure("Ответ OpenRouter обрезан лимитом токенов. Попробуйте другую модель");
            var content = choice.path("message").path("content");
            if (!content.isString() || content.asString().isBlank())
                throw new Failure("OpenRouter вернул пустой или неподдерживаемый ответ");
            return content.asString().trim();
        } catch (Failure e) { throw e;
        } catch (HttpTimeoutException e) {
            throw new Failure("OpenRouter не ответил за " + timeout.toSeconds() + " секунд");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Failure("Запрос OpenRouter прерван");
        } catch (IOException e) {
            throw new Failure("Не удалось подключиться к OpenRouter. Проверьте доступ к сети из backend");
        } catch (RuntimeException e) {
            // Never expose provider bodies, headers or credentials in events or logs.
            throw new Failure("Не удалось обработать ответ OpenRouter");
        }
    }

    private static Failure statusFailure(int status) {
        return new Failure(switch (status) {
            case 401 -> "OpenRouter отклонил API-ключ. Проверьте ключ и срок действия";
            case 402 -> "OpenRouter: недостаточно кредитов или достигнут денежный лимит ключа. Проверьте модель и лимиты";
            case 403 -> "OpenRouter запретил запрос. Проверьте доступ и настройки аккаунта";
            case 400, 404, 422 -> "OpenRouter: модель или параметры запроса недоступны. Проверьте OPENROUTER_MODEL";
            case 408, 504 -> "Превышено время ожидания ответа OpenRouter";
            case 429 -> "Достигнут лимит запросов OpenRouter или провайдера модели. Повторите позже";
            default -> "OpenRouter временно недоступен (HTTP " + status + "). Повторите позже";
        });
    }

    /** Only locally defined, credential-free messages may be exposed to the session. */
    public static class Failure extends RuntimeException {
        public Failure(String message) { super(message); }
    }
}
