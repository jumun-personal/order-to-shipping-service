package com.jumunhasyeotjo.order_to_shipping.common.config;

import feign.micrometer.MicrometerObservationCapability;  // ← 추가!
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.stereotype.Component;

@Configuration
@EnableAspectJAutoProxy
public class AutoTracingConfig {


    @Bean
    public ObservedAspect observedAspect(ObservationRegistry registry) {
        return new ObservedAspect(registry);
    }

    @Bean
    public MicrometerObservationCapability micrometerObservationCapability(
            ObservationRegistry observationRegistry) {
        return new MicrometerObservationCapability(observationRegistry);
    }
}

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
class AutoServiceTracing {

    private final Tracer tracer;

    // 모든 @Service 메서드 자동 추적
    @Around("@within(org.springframework.stereotype.Service)")
    public Object traceService(ProceedingJoinPoint pjp) throws Throwable {
        String className = pjp.getSignature().getDeclaringType().getSimpleName();
        String methodName = pjp.getSignature().getName();
        String spanName = className + "." + methodName;

        Span span = tracer.nextSpan().name(spanName);
        try (Tracer.SpanInScope ws = tracer.withSpan(span.start())) {
            log.debug("Starting span: {}", spanName);
            return pjp.proceed();
        } catch (Throwable ex) {
            span.error(ex);
            throw ex;
        } finally {
            span.end();
            log.debug(" Ended span: {}", spanName);
        }
    }

    // 모든 @RestController 메서드 자동 추적
    @Around("@within(org.springframework.web.bind.annotation.RestController)")
    public Object traceController(ProceedingJoinPoint pjp) throws Throwable {
        String methodName = pjp.getSignature().getName();
        String spanName = "Controller." + methodName;

        Span span = tracer.nextSpan().name(spanName);
        try (Tracer.SpanInScope ws = tracer.withSpan(span.start())) {
            return pjp.proceed();
        } catch (Throwable ex) {
            span.error(ex);
            throw ex;
        } finally {
            span.end();
        }
    }

    // 모든 @Repository 메서드 자동 추적
    @Around("@within(org.springframework.stereotype.Repository)")
    public Object traceRepository(ProceedingJoinPoint pjp) throws Throwable {
        String methodName = pjp.getSignature().getName();
        String spanName = "DB." + methodName;

        Span span = tracer.nextSpan().name(spanName);
        try (Tracer.SpanInScope ws = tracer.withSpan(span.start())) {
            return pjp.proceed();
        } catch (Throwable ex) {
            span.error(ex);
            throw ex;
        } finally {
            span.end();
        }
    }
}