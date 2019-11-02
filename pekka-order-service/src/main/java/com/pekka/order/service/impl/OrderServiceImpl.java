package com.pekka.order.service.impl;

import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.pekka.common.pojo.PekkaResult;
import com.pekka.jedis.JedisClient;
import com.pekka.mapper.TbOrderItemMapper;
import com.pekka.mapper.TbOrderMapper;
import com.pekka.mapper.TbOrderShippingMapper;
import com.pekka.mapper.TbReceivingAddressMapper;
import com.pekka.order.pojo.OrderInfo;
import com.pekka.order.service.OrderService;
import com.pekka.pojo.TbOrderItem;
import com.pekka.pojo.TbOrderShipping;
import com.pekka.pojo.TbReceivingAddress;
import com.pekka.pojo.TbReceivingAddressExample;
import com.pekka.pojo.TbReceivingAddressExample.Criteria;

@Service
public class OrderServiceImpl implements OrderService {

	@Autowired
	private JedisClient jedisClient;
	@Autowired
	private TbOrderMapper orderMapper;
	@Autowired
	private TbOrderItemMapper orderItemMapper;
	@Autowired
	private TbOrderShippingMapper orderShippingMapper;
	@Autowired
	private TbReceivingAddressMapper receivingAddressMapper;

	@Value("${ORDER_ID_GEN_KEY}")
	private String ORDER_ID_GEN_KEY;
	@Value("${ORDER_ID_BEGIN_VALUE}")
	private String ORDER_ID_BEGIN_VALUE;
	@Value("${ORDER_ITEM_ID_GEN_KEY}")
	private String ORDER_ITEM_ID_GEN_KEY;
	@Value("${USER_SESSION}")
	private String USER_SESSION;

	@Override
	public PekkaResult createOrder(OrderInfo orderInfo) {
		// 生成订单号，可以使用redis的incr生成
		if (!jedisClient.exists(ORDER_ID_GEN_KEY)) {
			// 设置初始值
			jedisClient.set(ORDER_ID_GEN_KEY, ORDER_ID_BEGIN_VALUE);
		}
		String orderId = jedisClient.incr(ORDER_ID_GEN_KEY).toString();
		// 向订单表插入数据，需要补全pojo的属性
		orderInfo.setOrderId(orderId);
		// 免邮费
		orderInfo.setPostFee("0");
		// 1.未付款，2.已付款，3.未发货，4.已发货，5，交易成功，6.交易关闭
		orderInfo.setStatus(1);
		// 订单创建时间
		orderInfo.setCreateTime(new Date());
		orderInfo.setUpdateTime(new Date());
		// 向订单表插入数据
		orderMapper.insert(orderInfo);
		// 向订单明细表插入数据
		List<TbOrderItem> orderItems = orderInfo.getOrderItems();
		for (TbOrderItem tbOrderItem : orderItems) {
			// 获得明细主键
			String oid = jedisClient.incr(ORDER_ITEM_ID_GEN_KEY).toString();
			tbOrderItem.setId(oid);
			tbOrderItem.setOrderId(orderId);
			// 插入明细表
			orderItemMapper.insert(tbOrderItem);
		}
		// 向订单物流表插入数据
		TbOrderShipping orderShipping = orderInfo.getOrderShipping();
		orderShipping.setOrderId(orderId);
		orderShipping.setCreated(new Date());
		orderShipping.setUpdated(new Date());
		orderShippingMapper.insert(orderShipping);
		// 返回订单号
		return PekkaResult.ok(orderId);
	}

	@Override
	public TbReceivingAddress getReceiverByUserName(String username) {
		// 根据用户名查询该用户
		TbReceivingAddressExample example = new TbReceivingAddressExample();
		Criteria criteria = example.createCriteria();
		criteria.andUsernameEqualTo(username);
		List<TbReceivingAddress> list = receivingAddressMapper.selectByExample(example);
		TbReceivingAddress receiver = list.get(0);
		return receiver;
	}

}
