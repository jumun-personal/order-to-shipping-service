package com.jumunhasyeotjo.order_to_shipping.common.tracing;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

import java.util.function.Supplier;

public class TracedAsync {

    // 반환값 없는 작업용
    public static void runTraced(String spanName, Context parentContext, Tracer tracer, Runnable runnable) {
        Span span = tracer.spanBuilder(spanName)
                .setParent(parentContext)
                .startSpan();
        try (Scope scope = span.makeCurrent()) {
            runnable.run();
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR);
            throw e;
        } finally {
            span.end();
        }
    }

    // 반환값 있는 작업용
    public static <T> T supplyTraced(String spanName, Context parentContext, Tracer tracer, Supplier<T> supplier) {
        Span span = tracer.spanBuilder(spanName)
                .setParent(parentContext)
                .startSpan();
        try (Scope scope = span.makeCurrent()) {
            return supplier.get();
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR);
            throw e;
        } finally {
            span.end();
        }
    }
}
