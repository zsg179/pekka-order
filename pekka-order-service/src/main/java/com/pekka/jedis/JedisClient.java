package com.pekka.jedis;

import java.util.Set;

import com.pekka.pojo.TbItem;

public interface JedisClient {

	String set(String key, String value);
	String get(String key);
	Boolean exists(String key);
	Long expire(String key, int seconds);
	Long ttl(String key);
	Long incr(String key);
	Long hset(String key, String field, String value);
	String hget(String key, String field);	
	Long hdel(String key,String... field);//删除hkey
	Integer zincrby(String key,int sales,String itemJson);
	Set<String> zrevrange(String key,int start,int end); 
	
}
