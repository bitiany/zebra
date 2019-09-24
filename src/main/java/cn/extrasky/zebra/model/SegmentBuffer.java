package cn.extrasky.zebra.model;

import cn.extrasky.zebra.BufferAllocator;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.concurrent.atomic.AtomicLong;

/**
 * @author YangGuodong
 */
@Data
@Accessors(chain = true)
public class SegmentBuffer {

    private String key;

    private int step;

    private long max;

    private int factor;

    private int wasteQuota;

    private int currentPos;

    private long currentValue;

    private boolean nextReady;

    private long nextMax;

    @Data
    public static class Segment{
        private AtomicLong value;
        private volatile long max;
        private BufferAllocator bufferAllocator;

        public Segment(BufferAllocator bufferAllocator) {
            value = new AtomicLong(0);
            this.bufferAllocator = bufferAllocator;
        }

        public long getId(){
            return this.getValue().getAndIncrement();
        }

        public long getIdle(){
            return this.getMax() - getValue().get();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("Segment(")
                    .append("value:").append(value)
                    .append(",max:").append(max)
                    .append(")");
            return sb.toString();
        }
    }
}
