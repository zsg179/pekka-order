package com.pekka.order.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.pekka.order.pojo.OrderInfo;

public interface OrderMapper {
	List<OrderInfo> getAllOrders(Long userId);

	List<OrderInfo> getOrderByStatus(@Param("userId") Long userId, @Param("status") int status);

	OrderInfo searchOrderByOrderId(String OrderId);

}
