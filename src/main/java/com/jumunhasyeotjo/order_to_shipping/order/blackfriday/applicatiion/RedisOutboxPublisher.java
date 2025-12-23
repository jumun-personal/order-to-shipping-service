package com.jumunhasyeotjo.order_to_shipping.order.blackfriday.applicatiion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jumunhasyeotjo.order_to_shipping.common.exception.BusinessException;
import com.jumunhasyeotjo.order_to_shipping.common.exception.ErrorCode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class RedisOutboxPublisher {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private DefaultRedisScript<String> luaScript;

    public RedisOutboxPublisher(
            @Qualifier("BfredisTemplate") RedisTemplate<String, String> bfRedisTemplate,
            ObjectMapper objectMapper) {
        this.redisTemplate = bfRedisTemplate;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        luaScript = new DefaultRedisScript<>();
        luaScript.setLocation(new ClassPathResource("lua/order-outbox.lua"));
        luaScript.setResultType(String.class);  //  String 타입
    }

    public String publishWithLuaScript(
            UUID orderId,
            String idempotencyKey,
            List<Map<String, Object>> productList,
            UUID couponId,
            OutboxEventPayload eventPayload) {

        try {
            log.info("[Lua Script 실행 시작] orderId: {}, idempotencyKey: {}", orderId, idempotencyKey);

            // 1. KEYS 준비
            List<String> keys = prepareKeys();

            // 2. ARGV 준비
            List<String> argv = prepareArgv(orderId, idempotencyKey, productList, couponId, eventPayload);

            // 3. Lua Script 실행 - String 반환
            String resultJson = redisTemplate.execute(
                    luaScript,
                    keys,
                    argv.toArray()
            );

            log.debug("[Lua Script 원본 결과] {}", resultJson);
            //  원본 결과 로그 (디버깅용)
            log.info("[Lua Script 원본 결과] type: {}, content: '{}'",
                    resultJson != null ? resultJson.getClass().getSimpleName() : "null",
                    resultJson);

            // 4. JSON 검증
            if (resultJson == null || resultJson.trim().isEmpty()) {
                log.error("[Lua Script 결과 없음] orderId: {}", orderId);
                throw new BusinessException(ErrorCode.LUA_SCRIPT_EXECUTION_FAILED);
            }

            // 5. JSON 문자열을 Map으로 파싱
            Map<String, Object> result;
            try {
                result = objectMapper.readValue(
                        resultJson,
                        new TypeReference<Map<String, Object>>() {}
                );
            } catch (JsonProcessingException e) {
                log.error("[JSON 파싱 실패] orderId: {}, resultJson: '{}', error: {}",
                        orderId, resultJson, e.getMessage());
                throw new BusinessException(ErrorCode.JSON_PROCESSING_ERROR);
            }

            // 5. 결과 처리
            return handleLuaScriptResult(result, orderId);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Lua Script 실행 실패] orderId: {}, error: {}", orderId, e.getMessage(), e);
            throw new BusinessException(ErrorCode.OUTBOX_PUBLISH_FAILED);
        }
    }

    private List<String> prepareKeys() {
        return Arrays.asList(
                "outbox:order-created",
                "idempotency:"
        );
    }

    private List<String> prepareArgv(
            UUID orderId,
            String idempotencyKey,
            List<Map<String, Object>> productList,
            UUID couponId,
            OutboxEventPayload eventPayload) {

        try {
            List<String> argv = new ArrayList<>();

            argv.add(orderId.toString());
            argv.add(objectMapper.writeValueAsString(productList));
            argv.add(couponId != null ? couponId.toString() : "nil");
            argv.add("0");
            argv.add(idempotencyKey);
            argv.add(objectMapper.writeValueAsString(eventPayload));

            log.debug("[ARGV 준비 완료] size: {}", argv.size());

            return argv;

        } catch (JsonProcessingException e) {
            log.error("[JSON 변환 실패] orderId: {}", orderId, e);
            throw new BusinessException(ErrorCode.JSON_PROCESSING_ERROR);
        }
    }

    private String handleLuaScriptResult(Map<String, Object> result, UUID orderId) {
        if (result == null) {
            throw new BusinessException(ErrorCode.LUA_SCRIPT_EXECUTION_FAILED);
        }

        log.debug("[Lua Script 파싱 결과] {}", result);

        // 에러 체크
        if (result.containsKey("err")) {
            String errorCode = (String) result.get("err");
            log.error("[Lua Script 에러] orderId: {}, errorCode: {}, result: {}",
                    orderId, errorCode, result);

            switch (errorCode) {
                case "DUPLICATE_REQUEST" ->
                        throw new BusinessException(ErrorCode.DUPLICATE_ORDER);
                case "INSUFFICIENT_STOCK" -> {
                    log.error("[재고 부족] productId: {}, required: {}, available: {}",
                            result.get("productId"),
                            result.get("required"),
                            result.get("available"));
                     throw new BusinessException(ErrorCode.INVALID_PRODUCT_STOCK);
                }
                case "COUPON_ALREADY_USED" -> {
                    log.error("[쿠폰 이미 사용] couponId: {}", result.get("couponId"));
                     throw new BusinessException(ErrorCode.COUPON_ALREADY_USED);
                }
                case "JSON_PARSE_ERROR" -> {
                    log.error("[JSON 파싱 에러] details: {}", result.get("details"));
                     throw new BusinessException(ErrorCode.JSON_PROCESSING_ERROR);
                }
                default ->
                        throw new BusinessException(ErrorCode.LUA_SCRIPT_EXECUTION_FAILED);
            }
        }

        // 성공 처리
        if ("SUCCESS".equals(result.get("ok"))) {
            String messageId = (String) result.get("messageId");
            log.info("[Lua Script 실행 성공] orderId: {}, messageId: {}", orderId, messageId);
            return messageId;
        }

        throw new BusinessException(ErrorCode.LUA_SCRIPT_EXECUTION_FAILED);
    }
}