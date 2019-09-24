package cn.extrasky.zebra.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * @author YangGuodong
 */
@Accessors(chain = true)
@NoArgsConstructor
@Data
public class IdStore {
    private Integer id;

    private String key;
    private String name;
    private long max;
    private int step;

    private int factor;

    private int wasteQuota;

    private Long ts;

    private IdStore(Builder builder) {
        setId(builder.id);
        setKey(builder.key);
        setName(builder.name);
        setMax(builder.max);
        setStep(builder.step);
        setFactor(builder.factor);
        setWasteQuota(builder.wasteQuota);
        setTs(builder.ts);
    }

    public static Builder builder(){
        return new Builder();
    }

    public static final class Builder {
        private Integer id;
        private String key;
        private String name;
        private long max;
        private int step;
        private int factor;
        private int wasteQuota;
        private Long ts;

        public Builder() {
        }

        public Builder id(Integer val) {
            id = val;
            return this;
        }

        public Builder key(String val) {
            key = val;
            return this;
        }

        public Builder name(String val) {
            name = val;
            return this;
        }

        public Builder max(long val) {
            max = val;
            return this;
        }

        public Builder step(int val) {
            step = val;
            return this;
        }

        public Builder factor(int val) {
            factor = val;
            return this;
        }

        public Builder wasteQuota(int val){
            wasteQuota = val;
            return this;
        }

        public Builder ts(Long val) {
            ts = val;
            return this;
        }

        public IdStore build() {
            return new IdStore(this);
        }
    }
}
