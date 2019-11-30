package com.pekka.order.service;

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

}
