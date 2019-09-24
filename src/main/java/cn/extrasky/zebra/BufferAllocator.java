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

    private int paddingThreshold;
    private volatile int currentPos;
    @Setter
    @Getter
    private volatile boolean nextReady;
    @Getter
    private volatile   Segment[] buffers;

    @Getter
    private volatile AtomicBoolean isOk = new AtomicBoolean(false);
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

    public static BufferAllocator build(SegmentBuffer segmentBuffer, BufferPaddingExecutor bufferPaddingExecutor){
        BufferAllocator allocator = new BufferAllocator(segmentBuffer.getKey(), segmentBuffer.getStep(), segmentBuffer.getFactor(), segmentBuffer.getWasteQuota());
        allocator.currentPos = segmentBuffer.getCurrentPos();
        resumeSegment(allocator.getCurrent(), segmentBuffer.getMax(),(segmentBuffer.getCurrentValue() + segmentBuffer.getStep() * (100 + segmentBuffer.getWasteQuota()))/100);
        if(segmentBuffer.isNextReady()) {
            resumeSegment(allocator.getBuffers()[allocator.nextPos()], segmentBuffer.getNextMax(), segmentBuffer.getNextMax() - segmentBuffer.getStep());
        }
        allocator.setBufferPaddingExecutor(bufferPaddingExecutor);
        allocator.combineSegmentBuffer(segmentBuffer.getCurrentPos(), allocator.getCurrent().getMax(), segmentBuffer.getWasteQuota());
        allocator.setIsOk(true);
        return allocator;
    }

    public static BufferAllocator build(IdStore store, BufferPaddingExecutor bufferPaddingExecutor){
        BufferAllocator allocator = new BufferAllocator(store.getKey(), store.getStep(), store.getFactor(), store.getWasteQuota());
        allocator.bufferPaddingExecutor = bufferPaddingExecutor;
        try {
            allocator.setBufferPaddingExecutor(bufferPaddingExecutor);
            bufferPaddingExecutor.updateAllocator(allocator, 0);
            allocator.setIsOk(true);
        } catch (IdGeneratorException e) {
            e.printStackTrace();
        }
        return allocator;
    }

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
