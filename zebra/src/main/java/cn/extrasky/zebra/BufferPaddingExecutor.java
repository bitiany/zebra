package cn.extrasky.zebra;

import cn.extrasky.zebra.cache.RedisClient;
import cn.extrasky.zebra.depository.IdStoreDepository;
import cn.extrasky.zebra.exception.IdGeneratorException;
import cn.extrasky.zebra.model.IdStore;
import cn.extrasky.zebra.utils.NamedThreadFactory;
import cn.extrasky.zebra.utils.StopWatch;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author YangGuodong
 */

@Slf4j
public class BufferPaddingExecutor {
    private static final String WORKER_NAME = "SegmentBuffer-Worker";
    private ExecutorService bufferPadExecutors;
    @Setter
    private IdStoreDepository idStoreDepository;
    @Setter
    private RedisClient redisClient;



    public BufferPaddingExecutor() {
        int cores = Runtime.getRuntime().availableProcessors() * 2;
        bufferPadExecutors = new ThreadPoolExecutor(cores, cores, 0L, TimeUnit.SECONDS, new LinkedBlockingDeque<Runnable>(1024),
                new NamedThreadFactory(WORKER_NAME));
    }


    public void loadAllocatorFromCache(BufferAllocator allocator, int pos) throws IdGeneratorException {
        long max = allocator.getSegmentBuffer().getStep();
        try {
            IdStore store = idStoreDepository.queryByStoreKey(allocator.getKey());
            if(store !=null){
                max = allocator.getSegmentBuffer().getStep() + store.getMax();
                store.setMax(max).setTs(System.currentTimeMillis());
            }else{
                store =IdStore.builder().key(allocator.getKey()).max(max)
                        .factor(allocator.getSegmentBuffer().getFactor())
                        .step(allocator.getSegmentBuffer().getStep())
                        .wasteQuota(allocator.getSegmentBuffer().getWasteQuota())
                        .ts(System.currentTimeMillis()).build();
            }
            idStoreDepository.save(store);
            allocator.combineSegmentBuffer(null, max, store.getWasteQuota());
            allocator.resetMax(allocator.getBuffers()[pos], max);
        } catch (Exception e) {
            throw new IdGeneratorException("load allocator error", e);
        }
    }

    /**
     * 通过分布式进行号段申请
     * @param allocator
     * @param pos
     * @throws IdGeneratorException
     */
    public void updateAllocator(BufferAllocator allocator, int pos) throws IdGeneratorException {
        StopWatch stopWatch = StopWatch.createUnstarted().start(allocator.getKey());
        String lockVal = String.valueOf(System.currentTimeMillis());
        try {
            long nanoTime = System.nanoTime();
            long _timeout = TimeUnit.NANOSECONDS.convert(3, TimeUnit.SECONDS);
            while(System.nanoTime() - nanoTime < _timeout){
                if(redisClient.distributedLock(allocator.getKey(), lockVal, 5)){
                    loadAllocatorFromCache(allocator, pos);
                    return;
                }
                Thread.sleep(10);
            }
            log.warn("get lock timeout:{}", allocator.getKey());
        } catch (Exception e) {
            log.error("error:{}",  e);
            throw new IdGeneratorException("update allocator error", e);
        }finally {
            try {
                redisClient.releaseDistributedLock(allocator.getKey(), lockVal);
            } catch (Exception e) {
                throw new IdGeneratorException("load allocator error", e);
            }
            log.debug("Allocate id task success:\n" +allocator.getSegmentBuffer()+ "," + allocator.getBuffers()[0] + " - " + allocator.getBuffers()[1] + " \n- " + stopWatch.stop());
        }
    }

    public void asyncUpdate(BufferAllocator allocator){
        bufferPadExecutors.execute(()->{
            try {
                updateAllocator(allocator, allocator.nextPos());
                allocator.setNextReady(true);
            } catch (IdGeneratorException e) {
                log.warn("update  next buffer for allocator error:{}", e);
                allocator.setNextReady(false);
            }finally {
                allocator.getIsRunning().set(false);
            }
        });
    }
}
