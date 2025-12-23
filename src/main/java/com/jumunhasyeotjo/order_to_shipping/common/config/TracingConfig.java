package com.jumunhasyeotjo.order_to_shipping.common.config;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

@Configuration
public class TracingConfig {

    // ===============================
    // OpenTelemetry SDK
    // ===============================
    @Bean
    public OpenTelemetry openTelemetry() {
        OtlpHttpSpanExporter exporter =
                OtlpHttpSpanExporter.builder()
                        .setEndpoint("http://tempo:4318/v1/traces")
                        .build();

        SdkTracerProvider tracerProvider =
                SdkTracerProvider.builder()
                        .addSpanProcessor(
                                BatchSpanProcessor.builder(exporter)
                                        .setScheduleDelay(Duration.ofMillis(100))
                                        .build()
                        )
                        .setResource(
                                Resource.create(
                                        Attributes.builder()
                                                .put("service.name", "order-to-shipping-service")
                                                .build()
                                )
                        )
                        .build();

        return OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(
                        ContextPropagators.create(
                                W3CTraceContextPropagator.getInstance()
                        )
                )
                .build();
    }

    @Bean
    public Tracer tracer(OpenTelemetry openTelemetry) {

        OtelCurrentTraceContext currentTraceContext =
                new OtelCurrentTraceContext();

        // 보통 traceId/spanId 외에 넘길 게 없으면 빈 리스트
        List<String> remoteFields = List.of();
        List<String> tagFields = List.of();

        return new OtelTracer(
                openTelemetry.getTracer("micrometer-tracer"),
                currentTraceContext,
                event -> {}
        );
    }
}

