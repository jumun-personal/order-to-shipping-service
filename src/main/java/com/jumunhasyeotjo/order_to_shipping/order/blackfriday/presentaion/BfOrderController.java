package com.jumunhasyeotjo.order_to_shipping.order.blackfriday.presentaion;

import com.jumunhasyeotjo.order_to_shipping.order.blackfriday.applicatiion.BFOrderOrchestrator;
import com.jumunhasyeotjo.order_to_shipping.order.application.command.*;
import com.jumunhasyeotjo.order_to_shipping.order.domain.entity.Order;
import com.jumunhasyeotjo.order_to_shipping.order.presentation.docs.*;
import com.jumunhasyeotjo.order_to_shipping.order.presentation.dto.request.CreateOrderReq;
import com.jumunhasyeotjo.order_to_shipping.order.presentation.dto.response.CreateOrderRes;
import com.library.passport.annotation.PassportAuthorize;
import com.library.passport.annotation.PassportUser;
import com.library.passport.entity.ApiRes;
import com.library.passport.entity.PassportUserRole;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static com.library.passport.proto.PassportProto.Passport;

@Tag(name = "Bf-Order", description = "주문 관리 (등록, 수정, 삭제, 조회) API")
@RestController
@RequestMapping("/api/v1/orders/bf")
@RequiredArgsConstructor
@Slf4j
public class BfOrderController {

    private final BFOrderOrchestrator orderOrchestrator;

    @ApiDocCreateOrder
    @PassportAuthorize(allowedRoles = PassportUserRole.COMPANY_MANAGER)
    @PostMapping
    public ResponseEntity<ApiRes<CreateOrderRes>> createOrder(@RequestBody @Valid CreateOrderReq req,
                                                              @RequestHeader("x-idempotency-key") String idempotencyKey,
                                                             @PassportUser Passport passport) {
        CreateOrderCommand command = new CreateOrderCommand(
                passport.getUserId(),
                UUID.fromString(passport.getBelong()),
                req.requestMessage(),
                req.orderProducts(),
                idempotencyKey,
                req.couponId(),
                req.tossPaymentKey(),
                req.tossOrderId());
        Order order = orderOrchestrator.createOrder(command);
        CreateOrderRes res = new CreateOrderRes(order.getId(), order.getStatus());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiRes.success(res));
    }
}
