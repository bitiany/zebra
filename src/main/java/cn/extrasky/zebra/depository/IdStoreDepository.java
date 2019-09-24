package cn.extrasky.zebra.depository;

import cn.extrasky.zebra.cache.RedisClient;
import cn.extrasky.zebra.model.IdStore;
import cn.extrasky.zebra.utils.StringUtils;
import com.alibaba.fastjson.JSON;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

/**
 * @author YangGuodong
 * @date: 2019-08-21
 */
@RequiredArgsConstructor(staticName = "with")
public class IdStoreDepository {

    private final static String CACHE_ID_STORE = "id:store";
    @Setter
    @NonNull
    private RedisClient redisClient;

    public void save(IdStore store) throws Exception {
        redisClient.executor("set", jedis -> jedis.hset(CACHE_ID_STORE , store.getKey(), JSON.toJSONString(store)));
    }

    public IdStore queryByStoreKey(String key) throws Exception {
        String result = redisClient.executor("get", jedis -> jedis.hget(CACHE_ID_STORE, key));
        return StringUtils.isNotBlank(result) ? JSON.parseObject(result, IdStore.class) : null;
    }
}
