package cn.extrasky.zebra;

import cn.extrasky.zebra.cache.RedisClient;
import cn.extrasky.zebra.depository.IdStoreDepositoryImpl;
import cn.extrasky.zebra.exception.IdGeneratorException;
import cn.extrasky.zebra.model.IdStore;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author YangGuodong
 */

@Slf4j
public class SingleBufferAllocator {

    private static SingleBufferAllocator instance = null;

    private Map<String, BufferAllocator> allocatorMap = new ConcurrentHashMap<>();
    private FilePersistenceExecutor filePersistenceExecutor;
    private BufferPaddingExecutor bufferPaddingExecutor;
    private BufferAllocatorFactory allocatorFactory;

    private SingleBufferAllocator(){
    }

    public static SingleBufferAllocator start(RedisClient redisClient){
        SingleBufferAllocator instance = getInstance();
        instance.initialize(redisClient);
        return instance;
    }

    public static SingleBufferAllocator getInstance(){
        if(null == instance){
            synchronized (SingleBufferAllocator.class){
                if(null == instance){
                    instance = new SingleBufferAllocator();
                }
            }
        }
        return instance;
    }

    public static void build(IdStore idStore) throws IdGeneratorException {
        putIfNotPresent(getInstance().allocatorFactory.build(idStore));

    }


    public static void putIfNotPresent(BufferAllocator allocator){
        getInstance().allocatorMap.put(allocator.getKey(), allocator);
    }

    public static BufferAllocator getAllocator(String key){
        return getInstance().allocatorMap.get(key);
    }


    public void shutdownHook(){
        if(null != allocatorMap && allocatorMap.size() >0){
            try {
                filePersistenceExecutor.put(allocatorMap);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    protected void initialize(RedisClient redisClient){
        filePersistenceExecutor = new FilePersistenceExecutor();
        bufferPaddingExecutor = new BufferPaddingExecutor();
        bufferPaddingExecutor.setRedisClient(redisClient);
        bufferPaddingExecutor.setIdStoreDepository(IdStoreDepositoryImpl.with(redisClient));
        allocatorFactory = new BufferAllocatorFactory(bufferPaddingExecutor, filePersistenceExecutor);
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdownHook));
    }


}
