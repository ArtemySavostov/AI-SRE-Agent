package ru.savostov.sre_platform.agentSession;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import ru.savostov.sre_platform.service.agentSession.execution.OpenRouterClient;
import java.net.http.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OpenRouterClientTests {
    private final HttpClient http = mock(HttpClient.class);
    private final OpenRouterClient client = new OpenRouterClient(JsonMapper.builder().build(), http, "test-secret", "openrouter/free", 30);

    @SuppressWarnings("unchecked")
    private void response(int status, String body) throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
    }

    @Test void openRouterConfigurationSelectsOnlyRealRunner() {
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withUserConfiguration(OpenRouterClient.class,
                        ru.savostov.sre_platform.service.agentSession.execution.OpenRouterAgentSessionRunner.class,
                        ru.savostov.sre_platform.service.agentSession.execution.MockAgentSessionRunner.class)
                .withBean(tools.jackson.databind.ObjectMapper.class, () -> JsonMapper.builder().build())
                .withBean(ru.savostov.sre_platform.service.agentSession.AgentSessionService.class,
                        () -> mock(ru.savostov.sre_platform.service.agentSession.AgentSessionService.class))
                .withPropertyValues("sre.agent-session.provider=openrouter")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertEquals(1, context.getBeansOfType(
                            ru.savostov.sre_platform.service.agentSession.execution.AgentSessionRunner.class).size());
                    assertInstanceOf(ru.savostov.sre_platform.service.agentSession.execution.OpenRouterAgentSessionRunner.class,
                            context.getBean(ru.savostov.sre_platform.service.agentSession.execution.AgentSessionRunner.class));
                });
    }

    @Test void returnsModelTextAndUsesAuthenticatedBoundedRequest() throws Exception {
        response(200, "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\" План диагностики \"}}]}");
        assertEquals("План диагностики", client.generate());
        var capture = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(capture.capture(), any());
        var request = capture.getValue();
        assertEquals("https://openrouter.ai/api/v1/chat/completions", request.uri().toString());
        assertEquals("Bearer test-secret", request.headers().firstValue("Authorization").orElseThrow());
        assertEquals(30, request.timeout().orElseThrow().toSeconds());
    }

    @Test void missingKeyDoesNotSendRequest() {
        var empty = new OpenRouterClient(JsonMapper.builder().build(), http, " ", "openrouter/free", 30);
        assertTrue(assertThrows(OpenRouterClient.Failure.class, empty::generate).getMessage().contains("OPENROUTER_API_KEY"));
        verifyNoInteractions(http);
    }

    @Test void providerErrorsNeverExposeTheirBodies() throws Exception {
        for (int status : new int[]{400, 401, 402, 403, 404, 429, 500, 503}) {
            response(status, "test-secret: sensitive provider details");
            String message = assertThrows(OpenRouterClient.Failure.class, client::generate).getMessage();
            assertFalse(message.contains("test-secret"));
            assertFalse(message.contains("sensitive"));
        }
    }

    @Test void rejectsMalformedEmptyTruncatedAndEmbeddedErrors() throws Exception {
        for (String body : new String[]{"not json", "{}", "{\"choices\":[{\"message\":{\"content\":\" \"}}]}",
                "{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"partial\"}}]}",
                "{\"error\":{\"code\":429,\"message\":\"test-secret\"}}"}) {
            response(200, body);
            assertFalse(assertThrows(OpenRouterClient.Failure.class, client::generate).getMessage().contains("test-secret"));
        }
    }

    @Test void timeoutIsReportedWithoutRetry() throws Exception {
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new HttpTimeoutException("test-secret"));
        assertTrue(assertThrows(OpenRouterClient.Failure.class, client::generate).getMessage().contains("30 секунд"));
        verify(http, times(1)).send(any(), any());
    }
}
