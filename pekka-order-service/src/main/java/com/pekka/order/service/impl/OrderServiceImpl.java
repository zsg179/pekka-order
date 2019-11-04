package com.pekka.order.service.impl;

import java.util.Date;
import java.util.List;

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
import com.pekka.order.pojo.OrderInfo;
import com.pekka.order.service.OrderService;
import com.pekka.pojo.TbItem;
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
	@Autowired
	private TbItemMapper itemMapper;

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
			String itemJson = JsonUtils.objectToJson(tbItem);
			// 广告分类id
			int adId = tbItem.getAdId().intValue();
			// 销量
			int sales = tbOrderItem.getNum();
			// 商品销量增加
			switch (adId) {
			case 0:
				jedisClient.zincrby(SALES_RANKING + "_HOT_AD", sales, itemJson);
				break;
			case 1:
				jedisClient.zincrby(SALES_RANKING + "_YIZHI_AD", sales, itemJson);
				break;
			case 2:
				jedisClient.zincrby(SALES_RANKING + "_YAOKONG_AD", sales, itemJson);
				break;
			case 3:
				jedisClient.zincrby(SALES_RANKING + "_JMPC_AD", sales, itemJson);
				break;
			case 4:
				jedisClient.zincrby(SALES_RANKING + "_DMMX_AD", sales, itemJson);
				break;
			case 5:
				jedisClient.zincrby(SALES_RANKING + "_JSWJ_AD", sales, itemJson);
				break;
			case 6:
				jedisClient.zincrby(SALES_RANKING + "_MRWJ_AD", sales, itemJson);
				break;
			case 7:
				jedisClient.zincrby(SALES_RANKING + "_CYDIY_AD", sales, itemJson);
				break;
			case 8:
				jedisClient.zincrby(SALES_RANKING + "_YQ_AD", sales, itemJson);
				break;
			default:
				break;
			}

			// 商品库存减少
			tbItem.setNum(tbItem.getNum() - tbOrderItem.getNum());
			tbItem.setUpdated(new Date());
			// 持久化
			try {
				itemMapper.updateByPrimaryKeySelective(tbItem);
			} catch (RuntimeException e) {
				e.printStackTrace();
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

}
