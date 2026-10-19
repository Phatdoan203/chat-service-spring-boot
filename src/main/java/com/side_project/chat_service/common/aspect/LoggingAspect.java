package com.side_project.chat_service.common.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.side_project.chat_service.common.util.LogEvent;

/**
 * Log thoi gian cho tang service. KHONG log tham so: chung co the chua
 * noi dung tin nhan. Chi log ten method, thoi gian va ma loi.
 */
@Aspect
@Component
public class LoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(LoggingAspect.class);
    private static final long SLOW_MS = 500;

    @Around("execution(public * com.side_project.chat_service.service..*(..))")
    public Object logService(ProceedingJoinPoint joinPoint) throws Throwable {
        String event = joinPoint.getSignature().getDeclaringType().getSimpleName()
                + "." + joinPoint.getSignature().getName();
        long startedAt = System.nanoTime();
        try {
            Object result = joinPoint.proceed();
            long tookMs = (System.nanoTime() - startedAt) / 1_000_000;
            if (tookMs > SLOW_MS) {
                log.warn(LogEvent.of(event, "durationMs", tookMs));
            } else {
                log.debug(LogEvent.of(event, "durationMs", tookMs));
            }
            return result;
        } catch (Throwable ex) {
            long tookMs = (System.nanoTime() - startedAt) / 1_000_000;
            // WARN: GlobalExceptionHandler moi la noi log ERROR, tranh log hai lan
            log.warn(LogEvent.of(event + ".failed", "durationMs", tookMs, "errorCode", ex.getClass().getSimpleName()));
            throw ex;
        }
    }
}
