package cn.extrasky.zebra.config;

import cn.extrasky.zebra.model.IdStore;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * @description:
 * @author: 田培融
 * @date: 2019-09-26 9:31
 */
@ConfigurationProperties(prefix = "zebra")
@Data
public class IdStoreProperties {

    private final static int DEFAULT_FACTOR = 70;
    private final static int DEFAULT_WASTE_QUOTA = 5;

    private List<IdStore> stores;

    private Integer factor;

    private Integer wasteQuota;


    public void setDefaultIdStore(){
        if(null != stores && stores.size() >0){
            stores.stream().forEach(s ->{
                if(0 == s.getFactor()){
                    s.setFactor(null == factor ? DEFAULT_FACTOR : factor);
                }
                if(0 == s.getWasteQuota()){
                    s.setWasteQuota(null == wasteQuota ? DEFAULT_WASTE_QUOTA : wasteQuota);
                }
            });
        }
    }


}
