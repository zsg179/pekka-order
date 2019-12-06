package com.pekka.order.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.pekka.common.pojo.PekkaResult;
import com.pekka.common.util.JsonUtils;
import com.pekka.jedis.JedisClient;
import com.pekka.mapper.TbItemMapper;
import com.pekka.mapper.TbOrderItemMapper;
import com.pekka.mapper.TbOrderMapper;
import com.pekka.mapper.TbOrderShippingMapper;
import com.pekka.mapper.TbReceivingAddressMapper;
import com.pekka.order.mapper.OrderMapper;
import com.pekka.order.pojo.OrderInfo;
import com.pekka.order.pojo.RedisItem;
import com.pekka.order.service.OrderService;
import com.pekka.pojo.TbItem;
import com.pekka.pojo.TbOrder;
import com.pekka.pojo.TbOrderItem;
import com.pekka.pojo.TbOrderItemExample;
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
	@Autowired
	private TbItemMapper itemMapper;
	@Autowired
	private OrderMapper mapper;

	@Value("${ORDER_ID_GEN_KEY}")
	private String ORDER_ID_GEN_KEY;
	@Value("${ORDER_ID_BEGIN_VALUE}")
	private String ORDER_ID_BEGIN_VALUE;
	@Value("${ORDER_ITEM_ID_GEN_KEY}")
	private String ORDER_ITEM_ID_GEN_KEY;
	@Value("${USER_SESSION}")
	private String USER_SESSION;
	@Value("${SALES_RANKING}")
	private String SALES_RANKING;

	@Override
	public PekkaResult createOrder(OrderInfo orderInfo) {
		List<TbOrderItem> orderItems = orderInfo.getOrderItems();
		// 先查看商品库存
		for (TbOrderItem tbOrderItem : orderItems) {
			String itemId = tbOrderItem.getItemId();
			TbItem tbItem = itemMapper.selectByPrimaryKey(Long.parseLong(itemId));
			if (tbItem.getNum() - tbOrderItem.getNum() < 0) {
				// 商品库存不足
				return PekkaResult.build(500, "商品库存不足！");
			}
		}
		// 购买的商品数量都足够，则存入redis中
		salesRanking(orderItems);

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
		// 用户名和id
		// orderInfo.setBuyerNick(orderInfo.getUserName());
		// orderInfo.setUserId(orderInfo.getUserId());
		// 向订单表插入数据
		orderMapper.insert(orderInfo);

		// 向订单明细表插入数据
		orderShipping(orderItems, orderId, orderInfo);

		// 返回订单号
		return PekkaResult.ok(orderInfo);
	}

	public void orderShipping(List<TbOrderItem> orderItems, String orderId, OrderInfo orderInfo) {
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
	}

	/**
	 * 商品销量存入redis，形成排行榜
	 * 
	 * @param orderItems
	 */
	public void salesRanking(List<TbOrderItem> orderItems) {
		for (TbOrderItem tbOrderItem : orderItems) {
			String itemId = tbOrderItem.getItemId();
			TbItem tbItem = itemMapper.selectByPrimaryKey(Long.parseLong(itemId));
			// 广告分类名称
			String categoryName = tbItem.getCategoryName();
			// 购买数量
			int sales = tbOrderItem.getNum();
			// 商品库存减少
			tbItem.setNum(tbItem.getNum() - sales);
			tbItem.setUpdated(new Date());
			// 持久化
			try {
				itemMapper.updateByPrimaryKeySelective(tbItem);
			} catch (RuntimeException e) {
				e.printStackTrace();
			}
			String itemJson = JsonUtils.objectToJson(new RedisItem(tbItem));
			// 当季热卖即总排行榜
			jedisClient.zincrby(SALES_RANKING + "_HOT_AD", sales, itemJson);
			// 商品销量增加
			switch (categoryName) {
			case "益智玩具":
				jedisClient.zincrby(SALES_RANKING + "_YIZHI_AD", sales, itemJson);
				break;
			case "遥控电动":
				jedisClient.zincrby(SALES_RANKING + "_YAOKONG_AD", sales, itemJson);
				break;
			case "积木拼插":
				jedisClient.zincrby(SALES_RANKING + "_JMPC_AD", sales, itemJson);
				break;
			case "动漫模型":
				jedisClient.zincrby(SALES_RANKING + "_DMMX_AD", sales, itemJson);
				break;
			case "健身玩具":
				jedisClient.zincrby(SALES_RANKING + "_JSWJ_AD", sales, itemJson);
				break;
			case "毛绒玩具":
				jedisClient.zincrby(SALES_RANKING + "_MRWJ_AD", sales, itemJson);
				break;
			case "创意DIY":
				jedisClient.zincrby(SALES_RANKING + "_CYDIY_AD", sales, itemJson);
				break;
			case "乐器":
				jedisClient.zincrby(SALES_RANKING + "_YQ_AD", sales, itemJson);
				break;
			default:
				break;
			}

		}
	}

	@Override
	public TbReceivingAddress getReceiverByUserName(String username) {
		// 根据用户名查询该用户
		TbReceivingAddressExample example = new TbReceivingAddressExample();
		Criteria criteria = example.createCriteria();
		criteria.andUsernameEqualTo(username);
		List<TbReceivingAddress> list = receivingAddressMapper.selectByExample(example);
		// 第一次买东西，无收货地址记录
		if (list.size() == 0) {
			TbReceivingAddress receiver = new TbReceivingAddress();
			return receiver;
		}
		TbReceivingAddress receiver = list.get(0);
		return receiver;
	}

	/**
	 * 保存收货人信息 ，设定每个用户只有一个收货信息
	 * 
	 * @param receiver
	 * @return
	 */
	@Override
	public PekkaResult saveReceiver(TbReceivingAddress receiver) {
		TbReceivingAddressExample example = new TbReceivingAddressExample();
		Criteria criteria = example.createCriteria();
		criteria.andUsernameEqualTo(receiver.getUsername());
		List<TbReceivingAddress> list = receivingAddressMapper.selectByExample(example);
		if (list.size() == 0) {
			// 第一次设置收货地址
			try {
				// 补全pojo
				receiver.setCreated(new Date());
				receiver.setUpdated(new Date());
				receivingAddressMapper.insert(receiver);
			} catch (RuntimeException e) {
				return PekkaResult.build(500, "保存失败");
			}

		} else {
			// 更新收货人呢信息
			try {
				// 补全pojo
				receiver.setUpdated(new Date());
				receivingAddressMapper.updateByExampleSelective(receiver, example);// 使用选择性插入，因为接收到的receiver中没有createtime
			} catch (RuntimeException e) {
				return PekkaResult.build(500, "保存失败");
			}

		}
		return PekkaResult.ok();
	}

	@Override
	public TbOrder getOrderByOrderId(String orderId) {
		return orderMapper.selectByPrimaryKey(orderId);
	}

	@Override
	public TbOrderItem getOrderItemByOrderId(String orderId) {
		TbOrderItemExample example = new TbOrderItemExample();
		com.pekka.pojo.TbOrderItemExample.Criteria criteria = example.createCriteria();
		criteria.andOrderIdEqualTo(orderId);
		List<TbOrderItem> list = orderItemMapper.selectByExample(example);
		return list.get(0);
	}

	@Override
	public void updateOrderStatus(String orderId, Integer status) {
		TbOrder order = orderMapper.selectByPrimaryKey(orderId);
		order.setStatus(status);
		if (status == 2) {
			order.setPaymentTime(new Date());
		} else if (status == 4) {
			order.setConsignTime(new Date());
		} else if (status == 5) {
			order.setEndTime(new Date());
		}
		order.setUpdateTime(new Date());
		orderMapper.updateByPrimaryKeySelective(order);
	}

	@Override
	public List<OrderInfo> getAllOrders(Long userId) {
		List<OrderInfo> allOrders = mapper.getAllOrders(userId);
		for (OrderInfo orderInfo : allOrders) {
			TbOrderItemExample example = new TbOrderItemExample();
			com.pekka.pojo.TbOrderItemExample.Criteria criteria = example.createCriteria();
			criteria.andOrderIdEqualTo(orderInfo.getOrderId());
			int num = orderItemMapper.countByExample(example);
			orderInfo.setItemTpyeNum(num);
		}
		return allOrders;
	}

	@Override
	public PekkaResult deleteOrder(String orderId) {
		try {
			orderMapper.deleteByPrimaryKey(orderId);
		} catch (Exception e) {
			return PekkaResult.build(500, "删除失败");
		}
		return PekkaResult.ok();
	}

	@Override
	public List<OrderInfo> getOrderByStatus(Long userId, int status) {
		List<OrderInfo> list = mapper.getOrderByStatus(userId, status);
		for (OrderInfo orderInfo : list) {
			TbOrderItemExample example = new TbOrderItemExample();
			com.pekka.pojo.TbOrderItemExample.Criteria criteria = example.createCriteria();
			criteria.andOrderIdEqualTo(orderInfo.getOrderId());
			int num = orderItemMapper.countByExample(example);
			orderInfo.setItemTpyeNum(num);
		}
		return list;
	}

	@Override
	public PekkaResult harvest(String orderId) {
		try {
			TbOrder order = orderMapper.selectByPrimaryKey(orderId);
			order.setStatus(5);
			order.setEndTime(new Date());
			order.setUpdateTime(new Date());
			orderMapper.updateByPrimaryKeySelective(order);
		} catch (Exception e) {
			return PekkaResult.build(500, "操作失败");
		}
		return PekkaResult.ok();
	}

	@Override
	public List<OrderInfo> searchOrder(String key) {
		List<OrderInfo> result = new ArrayList<>();
		if (StringUtils.isNumeric(key)) {
			// 纯数字
			// 先查订单
			OrderInfo orderInfo = mapper.searchOrderByOrderId(key);
			if (orderInfo != null) {
				// 有这个订单
				TbOrderItemExample example = new TbOrderItemExample();
				com.pekka.pojo.TbOrderItemExample.Criteria criteria = example.createCriteria();
				criteria.andOrderIdEqualTo(orderInfo.getOrderId());
				int num = orderItemMapper.countByExample(example);
				orderInfo.setItemTpyeNum(num);
				result.add(orderInfo);
				return result;
			}
			// 订单若为空，则查询商品id
			TbOrderItemExample example = new TbOrderItemExample();
			com.pekka.pojo.TbOrderItemExample.Criteria criteria = example.createCriteria();
			criteria.andItemIdEqualTo(key);
			List<TbOrderItem> itemList = orderItemMapper.selectByExample(example);
			if (itemList != null && itemList.size() > 0) {
				for (TbOrderItem tbOrderItem : itemList) {
					orderInfo = mapper.searchOrderByOrderId(tbOrderItem.getOrderId());
					if (orderInfo == null)
						continue;
					example = new TbOrderItemExample();
					criteria = example.createCriteria();
					criteria.andOrderIdEqualTo(orderInfo.getOrderId());
					int num = orderItemMapper.countByExample(example);
					orderInfo.setItemTpyeNum(num);
					result.add(orderInfo);
				}
				return result;
			}
		}
		// 不是纯数字或者是纯数字但没有找到对应的订单
		TbOrderItemExample example = new TbOrderItemExample();
		com.pekka.pojo.TbOrderItemExample.Criteria criteria = example.createCriteria();
		criteria.andTitleLike("%" + key + "%");
		List<TbOrderItem> itemList = orderItemMapper.selectByExample(example);
		if (itemList != null && itemList.size() > 0) {
			for (TbOrderItem tbOrderItem : itemList) {
				OrderInfo orderInfo = mapper.searchOrderByOrderId(tbOrderItem.getOrderId());
				if (orderInfo == null)
					continue;
				example = new TbOrderItemExample();
				criteria = example.createCriteria();
				criteria.andOrderIdEqualTo(orderInfo.getOrderId());
				int num = orderItemMapper.countByExample(example);
				orderInfo.setItemTpyeNum(num);
				result.add(orderInfo);
			}
		}
		return result;
	}

}
