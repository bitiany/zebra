package cn.extrasky.zebra;

import cn.extrasky.zebra.cache.RedisClient;
import cn.extrasky.zebra.depository.IdStoreDepository;
import cn.extrasky.zebra.exception.IdGeneratorException;
import cn.extrasky.zebra.model.IdStore;
import cn.extrasky.zebra.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author YangGuodong
 */

@Slf4j
public class BufferAllocatorTemplate {

    private static BufferAllocatorTemplate instance = null;

    private Map<String, BufferAllocator> allocatorMap = new ConcurrentHashMap<>();
    private FilePersistenceExecutor filePersistenceExecutor;
    private BufferPaddingExecutor bufferPaddingExecutor;
    private BufferAllocatorFactory allocatorFactory;

    private String backup;

    private BufferAllocatorTemplate(){
    }

    public static BufferAllocatorTemplate start(RedisClient redisClient, String backup){
        BufferAllocatorTemplate instance = getInstance();
        instance.setBackup(backup).initialize(redisClient);
        return instance;
    }

    public static BufferAllocatorTemplate start(RedisClient redisClient){
        BufferAllocatorTemplate instance = getInstance();
        instance.initialize(redisClient);
        return instance;
    }

    public static BufferAllocatorTemplate getInstance(){
        if(null == instance){
            synchronized (BufferAllocatorTemplate.class){
                if(null == instance){
                    instance = new BufferAllocatorTemplate();
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

    public BufferAllocatorTemplate setBackup(String backup) {
        this.backup = backup;
        return this;
    }

    protected void initialize(RedisClient redisClient){
        filePersistenceExecutor = StringUtils.isBlank(backup) ? new FilePersistenceExecutor() : new FilePersistenceExecutor(backup);
        bufferPaddingExecutor = new BufferPaddingExecutor();
        bufferPaddingExecutor.setRedisClient(redisClient);
        bufferPaddingExecutor.setIdStoreDepository(IdStoreDepository.with(redisClient));
        allocatorFactory = new BufferAllocatorFactory(bufferPaddingExecutor, filePersistenceExecutor);
        //java 进程钩子
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdownHook));
    }


}
