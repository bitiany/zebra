package cn.extrasky.zebra;

import cn.extrasky.zebra.exception.IdGeneratorException;
import cn.extrasky.zebra.model.IdStore;
import cn.extrasky.zebra.model.Result;
import cn.extrasky.zebra.model.SegmentBuffer;
import cn.extrasky.zebra.model.SegmentBuffer.Segment;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author YangGuodong
 */

@Slf4j
public class BufferAllocator implements IdGenerator {
    @Getter
    private SegmentBuffer segmentBuffer;
    /**
     * Buffer中空闲ID阈值
     */
    private int paddingThreshold;
    /**
     * 当前Buffer的index
     */
    private volatile int currentPos;
    /**
     * 下一个segment是否处于可切换状态
     */
    @Setter
    @Getter
    private volatile boolean nextReady;
    /**
     * 双Buffer
     */
    @Getter
    private volatile   Segment[] buffers;

    @Getter
    private volatile AtomicBoolean isOk = new AtomicBoolean(false);
    /**
     * 线程是否在运行中
     */
    @Getter
    private volatile AtomicBoolean isRunning = new AtomicBoolean(false);

    private volatile ReadWriteLock lock = new ReentrantReadWriteLock();
    @Setter
    public BufferPaddingExecutor bufferPaddingExecutor;

    public BufferAllocator(){
        this.currentPos = 0;
        this.nextReady = false;
        segmentBuffer = new SegmentBuffer();
        buffers = new Segment[]{new Segment(this), new Segment(this)};
    }

    public BufferAllocator(String key, int step, int factor, int wasteQuota){
        this();
        segmentBuffer.setKey(key).setStep(step)
                .setCurrentPos(this.currentPos)
                .setMax(step)
                .setFactor(factor)
                .setWasteQuota(wasteQuota)
                .setNextReady(this.nextReady);
        this.paddingThreshold = segmentBuffer.getStep() * factor /100;
    }

    /***
     * 从保存的segmentBuffer副本恢复ID分配器
     * @param segmentBuffer
     * @param bufferPaddingExecutor
     * @return
     */
    public static BufferAllocator build(SegmentBuffer segmentBuffer, BufferPaddingExecutor bufferPaddingExecutor){
        BufferAllocator allocator = new BufferAllocator(segmentBuffer.getKey(), segmentBuffer.getStep(), segmentBuffer.getFactor(), segmentBuffer.getWasteQuota());
        allocator.currentPos = segmentBuffer.getCurrentPos();
        resumeSegment(allocator.getCurrent(), segmentBuffer.getMax(),(segmentBuffer.getCurrentValue() +(segmentBuffer.getStep() * segmentBuffer.getWasteQuota())/100));
        if(segmentBuffer.isNextReady()) {
            resumeSegment(allocator.getBuffers()[allocator.nextPos()], segmentBuffer.getNextMax(), segmentBuffer.getNextMax() - segmentBuffer.getStep());
        }
        allocator.getSegmentBuffer().setCurrentValue(allocator.getCurrent().getValue().get())
                .setCurrentPos(allocator.currentPos)
                .setMax(allocator.getCurrent().getMax());
        allocator.setBufferPaddingExecutor(bufferPaddingExecutor);
        allocator.combineSegmentBuffer(segmentBuffer.getCurrentPos(), allocator.getCurrent().getMax(), segmentBuffer.getWasteQuota());
        allocator.setIsOk(true);
        return allocator;
    }

    /**
     * 初始化或从缓存中恢复ID分配器
     * @param store
     * @param bufferPaddingExecutor
     * @return
     */
    public static BufferAllocator build(IdStore store, BufferPaddingExecutor bufferPaddingExecutor){
        BufferAllocator allocator = new BufferAllocator(store.getKey(), store.getStep(), store.getFactor(), store.getWasteQuota());
        allocator.bufferPaddingExecutor = bufferPaddingExecutor;
        try {
            bufferPaddingExecutor.updateAllocator(allocator, 0);
            allocator.getSegmentBuffer().setCurrentValue(allocator.getCurrent().getValue().get())
                    .setCurrentPos(allocator.currentPos)
                    .setMax(allocator.getCurrent().getMax());
            allocator.setIsOk(true);
        } catch (IdGeneratorException e) {
            e.printStackTrace();
        }
        return allocator;
    }

    /**
     *  恢复Segment
     * @param segment
     * @param max
     * @param value
     */
    private static void resumeSegment(Segment segment, long max, long value){
        segment.setMax(max);
        segment.setValue(new AtomicLong(value));
    }

    @Override
    public Result get() {
        if(!isOk.get()){
            return Result.fail();
        }
        Segment buffer = getCurrent();
        try{
            lock.writeLock().lock();
            if(!this.nextReady && buffer.getIdle() < paddingThreshold && this.isRunning.compareAndSet(false, true)){
                bufferPaddingExecutor.asyncUpdate(this);
            }
            long value = buffer.getId();
            if(value <= buffer.getMax()){
                this.segmentBuffer.setCurrentValue(value);
                return Result.ok(value);
            }
        }finally {
            lock.writeLock().unlock();
        }
        waitAndSleep();
        try{
            lock.writeLock().lock();
            //当下个Buffer可切换时，切换Buffer
            if(this.nextReady){
                setNextReady(false);
                switchPos();
                this.segmentBuffer.setMax(this.getCurrent().getMax());
                this.segmentBuffer.setCurrentPos(this.currentPos);
            }
        }finally {
            lock.writeLock().unlock();
        }
        return get();
    }

    public void combineSegmentBuffer(Integer pos, long max, int wasteQuota){
        if(null != pos) {
            this.segmentBuffer.setCurrentPos(pos);
        }
        this.segmentBuffer.setMax(max).setWasteQuota(wasteQuota);
    }

    public void resetMax(Segment buffer, long max){
        buffer.setMax(max);
        buffer.getValue().set(max - segmentBuffer.getStep() + 1);
    }

    public int nextPos() {
        return (currentPos + 1) % 2;
    }

    public void switchPos() {
        currentPos = nextPos();
        this.segmentBuffer.setCurrentPos(currentPos);
    }

    public String getKey(){
        return this.segmentBuffer.getKey();
    }

    public Segment getCurrent() {
        return buffers[currentPos];
    }

    public void setIsOk(boolean isOk) {
        this.isOk.set(isOk);
    }

    private void waitAndSleep() {
        int roll = 0;
        while (this.isRunning.get()) {
            roll += 1;
            if(roll > 10000) {
                try {
                    TimeUnit.MILLISECONDS.sleep(10 + roll / 100 );
                    break;
                } catch (InterruptedException e) {
                    log.warn("Thread {} Interrupted",Thread.currentThread().getName());
                    break;
                }
            }
        }
    }
}
