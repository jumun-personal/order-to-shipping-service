package com.jumunhasyeotjo.order_to_shipping.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;

@Slf4j
@Component
public class StartupMonitor {

    private static final long JVM_START_TIME = ManagementFactory.getRuntimeMXBean().getStartTime();

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        long currentTime = System.currentTimeMillis();
        long startupTime = currentTime - JVM_START_TIME;

        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        long heapUsed = memoryBean.getHeapMemoryUsage().getUsed() / (1024 * 1024);
        long heapMax = memoryBean.getHeapMemoryUsage().getMax() / (1024 * 1024);

        log.info("╔═══════════════════════════════════════════════════════════╗");
        log.info("║           🚀 애플리케이션 시작 완료                           ║");
        log.info("╠═══════════════════════════════════════════════════════════╣");
        log.info("║ ⏱️  시작 시간: {} ms ({} seconds)                    ",
                String.format("%5d", startupTime),
                String.format("%.2f", startupTime / 1000.0));
        log.info("║ 💾 메모리 사용: {} MB / {} MB                         ",
                String.format("%4d", heapUsed),
                String.format("%4d", heapMax));
        log.info("║ 🌐 포트: {}                                              ",
                System.getProperty("server.port", "8080"));
        log.info("╚═══════════════════════════════════════════════════════════╝");

        // Slack 알림 등 추가 가능
        if (startupTime < 3000) {
            log.info("🎉 목표 시작 시간 달성! (< 3초)");
        }
    }
}
