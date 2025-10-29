package com.freshchicken.controller;

import com.freshchicken.model.Order;
import com.freshchicken.model.Order.OrderStatus;
import com.freshchicken.service.OrderService;
import io.micrometer.core.annotation.Timed;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 주문 REST API Controller
 * 
 * Endpoints:
 * - POST   /api/orders          : 주문 생성
 * - GET    /api/orders/{id}     : 주문 조회
 * - GET    /api/orders          : 주문 목록 조회
 * - DELETE /api/orders/{id}     : 주문 취소
 * - PATCH  /api/orders/{id}     : 주문 상태 변경
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private final OrderService orderService;

    /**
     * 주문 생성
     * 
     * @param order 주문 정보
     * @return 생성된 주문
     */
    @PostMapping
    @Timed(value = "api.orders.create", description = "주문 생성 API 응답 시간")
    public ResponseEntity<Map<String, Object>> createOrder(@Valid @RequestBody Order order) {
        log.info("POST /api/orders - 주문 생성 요청: {}", order);
        
        Order createdOrder = orderService.createOrder(order);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "주문이 성공적으로 생성되었습니다");
        response.put("data", createdOrder);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 주문 ID로 조회
     * 
     * @param id 주문 ID
     * @return 주문 정보
     */
    @GetMapping("/{id}")
    @Timed(value = "api.orders.get", description = "주문 조회 API 응답 시간")
    public ResponseEntity<Map<String, Object>> getOrder(@PathVariable Long id) {
        log.info("GET /api/orders/{} - 주문 조회 요청", id);
        
        Order order = orderService.getOrderById(id);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", order);
        
        return ResponseEntity.ok(response);
    }

    /**
     * 주문 목록 조회 (페이징)
     * 
     * @param page 페이지 번호 (default: 0)
     * @param size 페이지 크기 (default: 10)
     * @param sort 정렬 기준 (default: createdAt,desc)
     * @return 주문 목록
     */
    @GetMapping
    @Timed(value = "api.orders.list", description = "주문 목록 조회 API 응답 시간")
    public ResponseEntity<Map<String, Object>> getAllOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        
        log.info("GET /api/orders - 주문 목록 조회: page={}, size={}", page, size);
        
        String[] sortParams = sort.split(",");
        Sort.Direction direction = sortParams.length > 1 && sortParams[1].equalsIgnoreCase("asc") 
            ? Sort.Direction.ASC : Sort.Direction.DESC;
        
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortParams[0]));
        Page<Order> orders = orderService.getAllOrders(pageable);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", orders.getContent());
        response.put("totalElements", orders.getTotalElements());
        response.put("totalPages", orders.getTotalPages());
        response.put("currentPage", orders.getNumber());
        
        return ResponseEntity.ok(response);
    }

    /**
     * 상태별 주문 조회
     * 
     * @param status 주문 상태
     * @return 주문 목록
     */
    @GetMapping("/status/{status}")
    @Timed(value = "api.orders.status", description = "상태별 주문 조회 API 응답 시간")
    public ResponseEntity<Map<String, Object>> getOrdersByStatus(@PathVariable OrderStatus status) {
        log.info("GET /api/orders/status/{} - 상태별 주문 조회", status);
        
        List<Order> orders = orderService.getOrdersByStatus(status);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", orders);
        response.put("count", orders.size());
        
        return ResponseEntity.ok(response);
    }

    /**
     * 고객명으로 주문 검색
     * 
     * @param customerName 고객명
     * @param page 페이지 번호
     * @param size 페이지 크기
     * @return 주문 목록
     */
    @GetMapping("/search")
    @Timed(value = "api.orders.search", description = "주문 검색 API 응답 시간")
    public ResponseEntity<Map<String, Object>> searchOrders(
            @RequestParam String customerName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        log.info("GET /api/orders/search - 고객명 검색: customerName={}", customerName);
        
        Pageable pageable = PageRequest.of(page, size);
        Page<Order> orders = orderService.searchOrdersByCustomer(customerName, pageable);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", orders.getContent());
        response.put("totalElements", orders.getTotalElements());
        
        return ResponseEntity.ok(response);
    }

    /**
     * 최근 주문 조회
     * 
     * @return 최근 10개 주문
     */
    @GetMapping("/recent")
    @Timed(value = "api.orders.recent", description = "최근 주문 조회 API 응답 시간")
    public ResponseEntity<Map<String, Object>> getRecentOrders() {
        log.info("GET /api/orders/recent - 최근 주문 조회");
        
        List<Order> orders = orderService.getRecentOrders();
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", orders);
        
        return ResponseEntity.ok(response);
    }

    /**
     * 주문 취소
     * 
     * @param id 주문 ID
     * @return 취소된 주문
     */
    @DeleteMapping("/{id}")
    @Timed(value = "api.orders.cancel", description = "주문 취소 API 응답 시간")
    public ResponseEntity<Map<String, Object>> cancelOrder(@PathVariable Long id) {
        log.info("DELETE /api/orders/{} - 주문 취소 요청", id);
        
        Order cancelledOrder = orderService.cancelOrder(id);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "주문이 취소되었습니다");
        response.put("data", cancelledOrder);
        
        return ResponseEntity.ok(response);
    }

    /**
     * 주문 상태 변경
     * 
     * @param id 주문 ID
     * @param status 변경할 상태
     * @return 업데이트된 주문
     */
    @PatchMapping("/{id}/status")
    @Timed(value = "api.orders.update", description = "주문 상태 변경 API 응답 시간")
    public ResponseEntity<Map<String, Object>> updateOrderStatus(
            @PathVariable Long id,
            @RequestParam OrderStatus status) {
        
        log.info("PATCH /api/orders/{}/status - 상태 변경: status={}", id, status);
        
        Order updatedOrder = orderService.updateOrderStatus(id, status);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "주문 상태가 변경되었습니다");
        response.put("data", updatedOrder);
        
        return ResponseEntity.ok(response);
    }

    /**
     * 예외 처리
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e) {
        log.error("IllegalArgumentException: {}", e.getMessage());
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", e.getMessage());
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException e) {
        log.error("IllegalStateException: {}", e.getMessage());
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", e.getMessage());
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
}
