package cn.extrasky.zebra.cache;

import redis.clients.jedis.Jedis;
/**
 * @author YangGuodong
 */
public interface RedisClientFactory {
    /**
     *  获取Jedis连接
     * @return
     */
    Jedis getJedisConnection();
}
