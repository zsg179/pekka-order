package com.pekka.order.pojo;

import com.pekka.pojo.TbItem;

public class RedisItem {
	private Long id;

	private String title;

	private Long price;

	private String image;

	public RedisItem(TbItem tbItem) {
		this.id = tbItem.getId();
		this.title = tbItem.getTitle();
		this.price = tbItem.getPrice();
		this.image = tbItem.getImage();
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public Long getPrice() {
		return price;
	}

	public void setPrice(Long price) {
		this.price = price;
	}

	public String getImage() {
		return image;
	}

	public void setImage(String image) {
		this.image = image;
	}

}
