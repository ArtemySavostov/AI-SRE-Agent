package ru.savostov.sre_platform.logs;

import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.savostov.sre_platform.metrics.MetricsErrorHandler;


@RestControllerAdvice(assignableTypes = LogsController.class)
public class LogsErrorHandler extends MetricsErrorHandler {}
