package com.freshchicken.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 주문 엔티티
 * 
 * MySQL 테이블: orders
 * Redis 캐싱: order:{id}
 */
@Entity
@Table(name = "orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "고객 이름은 필수입니다")
    @Column(nullable = false, length = 100)
    private String customerName;

    @NotBlank(message = "메뉴는 필수입니다")
    @Column(nullable = false, length = 200)
    private String menuItem;

    @Min(value = 1, message = "수량은 최소 1개 이상이어야 합니다")
    @Column(nullable = false)
    private Integer quantity;

    @Min(value = 0, message = "가격은 0원 이상이어야 합니다")
    @Column(nullable = false)
    private Integer totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(length = 500)
    private String notes;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 주문 상태
     */
    public enum OrderStatus {
        PENDING("주문 대기"),
        CONFIRMED("주문 확인"),
        PREPARING("조리 중"),
        READY("픽업 대기"),
        COMPLETED("완료"),
        CANCELLED("취소");

        private final String description;

        OrderStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}
