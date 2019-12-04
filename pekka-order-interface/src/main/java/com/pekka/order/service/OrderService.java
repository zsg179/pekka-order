package com.pekka.order.service;

import java.util.List;

import com.pekka.common.pojo.PekkaResult;
import com.pekka.order.pojo.OrderInfo;
import com.pekka.pojo.TbOrder;
import com.pekka.pojo.TbOrderItem;
import com.pekka.pojo.TbReceivingAddress;

public interface OrderService {
	PekkaResult createOrder(OrderInfo orderInfo);

	TbReceivingAddress getReceiverByUserName(String username);

	PekkaResult saveReceiver(TbReceivingAddress receiver);

	TbOrder getOrderByOrderId(String orderId);

	TbOrderItem getOrderItemByOrderId(String orderId);

	void updateOrderStatus(String orderId, Integer status);

	List<OrderInfo> getAllOrders(Long userId);

	PekkaResult deleteOrder(String orderId);

	List<OrderInfo> getOrderByStatus(Long userId, int status);

	PekkaResult harvest(String orderId);

	List<OrderInfo> searchOrder(String key);

}
