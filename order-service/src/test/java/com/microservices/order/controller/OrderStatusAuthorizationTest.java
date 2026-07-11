package com.microservices.order.controller;

import com.microservices.order.dto.OrderDTO;
import com.microservices.order.dto.UpdateOrderStatusRequest;
import com.microservices.order.entity.OrderStatus;
import com.microservices.order.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderStatusAuthorizationTest {

    @Mock
    private OrderService orderService;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private OrderController orderController;

    private UpdateOrderStatusRequest body;

    @BeforeEach
    void setUp() {
        body = new UpdateOrderStatusRequest(OrderStatus.CONFIRMED);
    }

    @Test
    void rejectsCustomerUpdatingStatus() {
        when(request.getAttribute("userId")).thenReturn(1L);
        when(request.getAttribute("role")).thenReturn("CUSTOMER");

        assertThatThrownBy(() -> orderController.updateOrderStatus(9L, body, request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("ADMIN or OPERATOR");
        verify(orderService, never()).updateOrderStatus(any(), any());
    }

    @Test
    void allowsOperatorUpdatingStatus() {
        when(request.getAttribute("userId")).thenReturn(2L);
        when(request.getAttribute("role")).thenReturn("OPERATOR");
        OrderDTO updated = new OrderDTO();
        updated.setId(9L);
        updated.setStatus(OrderStatus.CONFIRMED);
        when(orderService.updateOrderStatus(eq(9L), any())).thenReturn(updated);

        assertThat(orderController.updateOrderStatus(9L, body, request).getBody().getStatus())
                .isEqualTo(OrderStatus.CONFIRMED);
        verify(orderService).updateOrderStatus(eq(9L), any());
    }

    @Test
    void allowsAdminUpdatingStatus() {
        when(request.getAttribute("userId")).thenReturn(3L);
        when(request.getAttribute("role")).thenReturn("ADMIN");
        OrderDTO updated = new OrderDTO();
        updated.setId(9L);
        updated.setStatus(OrderStatus.SHIPPED);
        body.setStatus(OrderStatus.SHIPPED);
        when(orderService.updateOrderStatus(eq(9L), any())).thenReturn(updated);

        assertThat(orderController.updateOrderStatus(9L, body, request).getBody().getStatus())
                .isEqualTo(OrderStatus.SHIPPED);
        verify(orderService).updateOrderStatus(eq(9L), any());
    }
}
