package com.freshchicken.service;

import com.freshchicken.model.Order;
import com.freshchicken.model.Order.OrderStatus;
import com.freshchicken.repository.OrderRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 주문 서비스
 * 
 * 비즈니스 로직:
 * - 주문 생성/조회/취소
 * - Redis 캐싱 적용
 * - Prometheus 메트릭 수집
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final MeterRegistry meterRegistry;

    /**
     * 주문 생성
     * 
     * @param order 주문 정보
     * @return 생성된 주문
     */
    @Transactional
    public Order createOrder(Order order) {
        log.info("주문 생성 시작: customerName={}, menuItem={}", 
            order.getCustomerName(), order.getMenuItem());
        
        order.setStatus(OrderStatus.PENDING);
        Order savedOrder = orderRepository.save(order);
        
        // Prometheus 메트릭 증가
        Counter.builder("orders.created")
            .description("총 생성된 주문 수")
            .tag("status", OrderStatus.PENDING.name())
            .register(meterRegistry)
            .increment();
        
        log.info("주문 생성 완료: orderId={}", savedOrder.getId());
        return savedOrder;
    }

    /**
     * 주문 ID로 조회 (캐싱 적용)
     * 
     * @param id 주문 ID
     * @return 주문 정보
     */
    @Cacheable(value = "orders", key = "#id")
    public Order getOrderById(Long id) {
        log.info("주문 조회: orderId={}", id);
        return orderRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다: " + id));
    }

    /**
     * 전체 주문 목록 조회
     * 
     * @param pageable 페이징 정보
     * @return 주문 목록
     */
    public Page<Order> getAllOrders(Pageable pageable) {
        log.info("전체 주문 목록 조회: page={}, size={}", 
            pageable.getPageNumber(), pageable.getPageSize());
        return orderRepository.findAll(pageable);
    }

    /**
     * 상태별 주문 조회
     * 
     * @param status 주문 상태
     * @return 주문 목록
     */
    public List<Order> getOrdersByStatus(OrderStatus status) {
        log.info("상태별 주문 조회: status={}", status);
        return orderRepository.findByStatus(status);
    }

    /**
     * 고객명으로 주문 검색
     * 
     * @param customerName 고객명
     * @param pageable 페이징 정보
     * @return 주문 목록
     */
    public Page<Order> searchOrdersByCustomer(String customerName, Pageable pageable) {
        log.info("고객명 검색: customerName={}", customerName);
        return orderRepository.findByCustomerNameContaining(customerName, pageable);
    }

    /**
     * 주문 취소 (캐시 제거)
     * 
     * @param id 주문 ID
     * @return 취소된 주문
     */
    @Transactional
    @CacheEvict(value = "orders", key = "#id")
    public Order cancelOrder(Long id) {
        log.info("주문 취소 시작: orderId={}", id);
        
        Order order = getOrderById(id);
        
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException("이미 취소된 주문입니다");
        }
        
        if (order.getStatus() == OrderStatus.COMPLETED) {
            throw new IllegalStateException("완료된 주문은 취소할 수 없습니다");
        }
        
        order.setStatus(OrderStatus.CANCELLED);
        Order cancelledOrder = orderRepository.save(order);
        
        // Prometheus 메트릭 증가
        Counter.builder("orders.cancelled")
            .description("총 취소된 주문 수")
            .register(meterRegistry)
            .increment();
        
        log.info("주문 취소 완료: orderId={}", id);
        return cancelledOrder;
    }

    /**
     * 주문 상태 변경
     * 
     * @param id 주문 ID
     * @param status 변경할 상태
     * @return 업데이트된 주문
     */
    @Transactional
    @CacheEvict(value = "orders", key = "#id")
    public Order updateOrderStatus(Long id, OrderStatus status) {
        log.info("주문 상태 변경: orderId={}, status={}", id, status);
        
        Order order = getOrderById(id);
        order.setStatus(status);
        return orderRepository.save(order);
    }

    /**
     * 최근 주문 조회
     * 
     * @return 최근 10개 주문
     */
    public List<Order> getRecentOrders() {
        log.info("최근 주문 조회");
        return orderRepository.findTop10ByOrderByCreatedAtDesc();
    }
}
