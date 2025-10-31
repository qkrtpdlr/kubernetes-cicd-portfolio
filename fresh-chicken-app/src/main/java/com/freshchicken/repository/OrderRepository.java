package com.freshchicken.repository;

import com.freshchicken.model.Order;
import com.freshchicken.model.Order.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 주문 Repository
 * 
 * Spring Data JPA를 사용한 데이터 액세스 레이어
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * 상태별 주문 조회
     */
    List<Order> findByStatus(OrderStatus status);

    /**
     * 고객명으로 주문 조회
     */
    Page<Order> findByCustomerNameContaining(String customerName, Pageable pageable);

    /**
     * 기간별 주문 조회
     */
    @Query("SELECT o FROM Order o WHERE o.createdAt BETWEEN :startDate AND :endDate")
    List<Order> findOrdersByDateRange(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    /**
     * 상태별 주문 개수 카운트
     */
    long countByStatus(OrderStatus status);

    /**
     * 최근 주문 조회
     */
    List<Order> findTop10ByOrderByCreatedAtDesc();
}
