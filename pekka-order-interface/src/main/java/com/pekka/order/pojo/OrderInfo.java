package com.pekka.order.pojo;

import java.io.Serializable;
import java.util.List;

import com.pekka.pojo.TbOrder;
import com.pekka.pojo.TbOrderItem;
import com.pekka.pojo.TbOrderShipping;

public class OrderInfo extends TbOrder implements Serializable {
	private List<TbOrderItem> orderItems;
	private TbOrderShipping orderShipping;
	private Integer itemTpyeNum;

	public Integer getItemTpyeNum() {
		return itemTpyeNum;
	}

	public void setItemTpyeNum(Integer itemTpyeNum) {
		this.itemTpyeNum = itemTpyeNum;
	}

	/*
	 * public OrderInfo(TbOrder tbOrder) { super();
	 * this.setOrderId(tbOrder.getOrderId());
	 * this.setPayment(tbOrder.getPayment());
	 * this.setPaymentType(tbOrder.getPaymentType());
	 * this.setPostFee(tbOrder.getPostFee());
	 * this.setStatus(tbOrder.getStatus());
	 * this.setCreateTime(tbOrder.getCreateTime());
	 * this.setUpdateTime(tbOrder.getUpdateTime());
	 * this.setPaymentTime(tbOrder.getPaymentTime());
	 * this.setConsignTime(tbOrder.getConsignTime());
	 * this.setEndTime(tbOrder.getEndTime());
	 * this.setCloseTime(tbOrder.getCloseTime());
	 * this.setShippingName(tbOrder.getShippingName());
	 * this.setShippingCode(tbOrder.getShippingCode());
	 * this.setUserId(tbOrder.getUserId());
	 * this.setBuyerMessage(tbOrder.getBuyerMessage());
	 * this.setBuyerNick(tbOrder.getBuyerNick());
	 * this.setBuyerRate(tbOrder.getBuyerRate()); }
	 */
	public List<TbOrderItem> getOrderItems() {
		return orderItems;
	}

	public void setOrderItems(List<TbOrderItem> orderItems) {
		this.orderItems = orderItems;
	}

	public TbOrderShipping getOrderShipping() {
		return orderShipping;
	}

	public void setOrderShipping(TbOrderShipping orderShipping) {
		this.orderShipping = orderShipping;
	}
}
