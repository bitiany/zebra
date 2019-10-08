package cn.extrasky.zebra.config;

import cn.extrasky.zebra.BufferAllocatorTemplate;
import cn.extrasky.zebra.cache.RedisClient;
import cn.extrasky.zebra.cache.RedisClientFactory;
import cn.extrasky.zebra.exception.IdGeneratorException;
import cn.extrasky.zebra.model.IdStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.CollectionUtils;

/**
 * @description:
 * @author: 田培融
 * @date: 2019-09-25 18:42
 */
@Configuration
@ConditionalOnClass({BufferAllocatorTemplate.class})
@EnableConfigurationProperties({IdStoreProperties.class})
public class BufferAllocatorTemplateAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(RedisConnectionFactory.RedisProperties.class)
    @ConfigurationProperties("zebra.redis")
    public RedisConnectionFactory.RedisProperties redisProperties(){
        return new RedisConnectionFactory.RedisProperties();
    }

    @Bean
    @ConditionalOnMissingBean({RedisClientFactory.class})
    public RedisClientFactory redisClientFactory(RedisConnectionFactory.RedisProperties redisProperties){
        return RedisConnectionFactory.with(redisProperties).build();
    }

    @Bean
    @ConditionalOnMissingBean({BufferAllocatorTemplate.class})
    public BufferAllocatorTemplate bufferAllocatorTemplate(IdStoreProperties storeProperties, RedisClientFactory redisClientFactory) throws IdGeneratorException {
        RedisClient redisClient = new RedisClient();
        redisClient.setRedisClientFactory(redisClientFactory::getJedisConnection);
        BufferAllocatorTemplate template = BufferAllocatorTemplate.start(redisClient);
        if (!CollectionUtils.isEmpty(storeProperties.getStores())){
            storeProperties.setDefaultIdStore();
            for (IdStore idStore : storeProperties.getStores()) {
                BufferAllocatorTemplate.build(new IdStore().setKey(idStore.getKey()).setStep(idStore.getStep()).setFactor(idStore.getFactor()).setWasteQuota(idStore.getWasteQuota()));
            }
            return template;
        }
        return template;
    }
}
