package com.jumunhasyeotjo.order_to_shipping.common.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;

@Getter
public enum ErrorCode {

    // 400 BAD_REQUEST
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "E001", "잘못된 요청입니다."),
    CREATE_VALIDATE_EXCEPTION(HttpStatus.BAD_REQUEST,"E002", "객체 생성에 실패했습니다."),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "E003", "입력값 검증에 실패했습니다."),
    INVALID_JSON(HttpStatus.BAD_REQUEST, "E004",  "잘못된 JSON 형식입니다."),
    REQUIRED_VALUE_MISSING(HttpStatus.BAD_REQUEST, "E005", "필수 값이 누락되었습니다."),
    INVALID_FORMAT(HttpStatus.BAD_REQUEST, "E006", "잘못된 입력 형식입니다."),
    INVALID_STATUS_TRANSITION(HttpStatus.BAD_REQUEST, "E007", "해당 상태로 전환할 수 없습니다."),
    HUB_ORIGIN_EQUALS_DESTINATION(HttpStatus.BAD_REQUEST, "E008", "도착 허브와 출발 허브가 같을 수 없습니다."),
    INVALID_STATE_FOR_MODIFICATION(HttpStatus.BAD_REQUEST, "E009", "현재 상태에서는 정보를 수정할 수 없습니다."),
    INVALID_STATE_CANCEL(HttpStatus.BAD_REQUEST, "E010", "배송이 이미 시작되어 취소할 수 없습니다."),
    INVALID_MONEY(HttpStatus.BAD_REQUEST, "E011", "유효하지않은 금액입니다."),
    DUPLICATE_PAYMENT_INTENT(HttpStatus.BAD_REQUEST, "E012", "이미 결제요청이 진행되었습니다."),

    EMPTY_ORDER_PRODUCTS(HttpStatus.BAD_REQUEST, "E050", "주문 상품은 1개 이상이어야 합니다."),
    TOTAL_PRICE_MISMATCH(HttpStatus.BAD_REQUEST, "E050", "총 가격은 상품들의 가격의 총합과 동일해야 됩니다."),
    INVALID_STATE_FOR_UPDATE(HttpStatus.BAD_REQUEST, "E050", "주문 수정은 PENDING 상태에서만 가능합니다."),
    ORDER_STATUS_UNCHANGEABLE(HttpStatus.BAD_REQUEST, "E050", "주문 상태는 DONE/CANCELLED 경우 변경 불가능합니다."),
    INVALID_STATE_FOR_CANCEL(HttpStatus.BAD_REQUEST, "E050", "주문 취소는 ORDERED 상태에서만 가능합니다."),
    INVALID_PRODUCT_QUANTITY(HttpStatus.BAD_REQUEST, "E050", "주문상품의 수량은 1 미만이면 안됩니다."),
    INVALID_PRODUCT_PRICE(HttpStatus.BAD_REQUEST, "E050", "주문상품의 가격은 1 미만이면 안됩니다."),
    INVALID_PRODUCT_STOCK(HttpStatus.BAD_REQUEST, "E050", "주문한 상품의 재고가 부족합니다."),
    DUPLICATE_ORDER(HttpStatus.BAD_REQUEST, "E050", "중복된 주문 입니다."),
    INVALID_COUPON_OWNER(HttpStatus.BAD_REQUEST, "E050", "쿠폰 소유자가 일치하지 않습니다."),

    // 401 UNAUTHORIZED
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "EU001", "인증이 필요합니다."),

    // 403 FORBIDDEN
    FORBIDDEN(HttpStatus.FORBIDDEN, "E011", "접근이 거부되었습니다."),
    FORBIDDEN_ORDER_UPDATE(HttpStatus.FORBIDDEN, "E050", "주문 수정은 주문한 업체 담당자만 가능합니다."),
    FORBIDDEN_ORDER_CANCEL(HttpStatus.FORBIDDEN, "E050", "주문을 취소할 권한이 없습니다."),
    FORBIDDEN_ORDER_HUB(HttpStatus.FORBIDDEN, "E050", "해당 소속 허브만 접근 가능합니다."),
    FORBIDDEN_GET_ORDER(HttpStatus.FORBIDDEN, "E050", "주문을 조회할 권한이 없습니다"),

    // 404 NOT_FOUND
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "E050", "존재하지 않는 주문 입니다."),
    COMPANY_NOT_FOUND(HttpStatus.NOT_FOUND, "E050", "존재하지 않는 업체 입니다."),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "E050", "존재하지 않는 상품 입니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "E050", "존재하지 않는 사용자 입니다."),

    // 404 NOT_FOUND
    NOT_FOUND_BY_ID(HttpStatus.NOT_FOUND, "EN001", "해당 ID로 데이터를 찾을 수 없습니다."),
    NOT_FOUND_BY_NAME(HttpStatus.NOT_FOUND, "EN002", "해당 이름로 데이터를 찾을 수 없습니다."),
    NOT_FOUND_HUB_EVENT(HttpStatus.NOT_FOUND, "EN003", "해당 이름로 허브 이벤트를 찾을 수 없습니다."),

    // 500 INTERNAL_SERVER_ERROR
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "EI001", "서버 에러가 발생했습니다."),
    HISTORY_SHIPPING_MISMATCH(HttpStatus.INTERNAL_SERVER_ERROR, "EI002", "배송이력과 배송이 일치하지 않습니다."),
    CONNECTION_NOT_FOUND_BETWEEN_HUBS(HttpStatus.INTERNAL_SERVER_ERROR, "EI003","연결된 허브 간 배송만 가능합니다."),
    NOT_FOUND_HUB_INFO(HttpStatus.INTERNAL_SERVER_ERROR, "EI004","허브 정보를 찾을 수 없습니다. "),

    //Black friday
    OUTBOX_PUBLISH_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "EBF001", "Outbox 이벤트 발행에 실패했습니다."),
    OUTBOX_PROCESSING_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "EBF002", "Outbox 처리에 실패했습니다."),
    OUTBOX_WAIT_INTERRUPTED(HttpStatus.INTERNAL_SERVER_ERROR, "EBF003", "Outbox 대기 중 인터럽트되었습니다."),
    OUTBOX_PROCESSING_TIMEOUT(HttpStatus.INTERNAL_SERVER_ERROR, "EBF004", "Outbox 처리 시간이 초과되었습니다."),
    ORDER_VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "EBF005", "병렬 검증 실패"),

    // Lua Script
    LUA_SCRIPT_EXECUTION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "LUA001", "Lua Script 실행에 실패했습니다."),
    INSUFFICIENT_STOCK(HttpStatus.BAD_REQUEST, "LUA002", "재고가 부족합니다."),
    COUPON_ALREADY_USED(HttpStatus.BAD_REQUEST, "LUA003", "이미 사용한 쿠폰입니다."),

    // Payment
    PAYMENT_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "PAY001", "결제 승인에 실패했습니다."),
    PAYMENT_CONFIRMATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "PAY002", "결제 확인에 실패했습니다."),

    // Final Stage
    FINAL_STAGE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "FST001", "Final Stage 처리에 실패했습니다."),
    FINAL_STAGE_STATUS_UPDATE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "FST002", "Final Stage 상태 갱신에 실패했습니다."),

    // JSON Processing
    JSON_PROCESSING_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "JSN001", "JSON 처리 중 오류가 발생했습니다."),

    // Redis
    REDIS_CONNECTION_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "RDS001", "Redis 연결에 실패했습니다."),
    REDIS_OPERATION_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "RDS002", "Redis 작업 중 오류가 발생했습니다."),

    //BF OUTBOX
    OUTBOX_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, "BFOB001", "OUTBOX 작업 중 오류가 발생했습니다."),
    OUTBOX_NOT_INIT(HttpStatus.INTERNAL_SERVER_ERROR, "BFOB002", "OUTBOX가 snapshot 저장 시점에 초기화 되지 않았습니다.");


    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }
}