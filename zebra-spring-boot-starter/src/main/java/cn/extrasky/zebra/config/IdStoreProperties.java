package cn.extrasky.zebra.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @description:
 * @author: 田培融
 * @date: 2019-09-26 9:31
 */
@ConfigurationProperties(prefix = "store")
@Data
public class IdStoreProperties {

    private String key;

    private Integer step;

    private Integer factor;

    private Integer wasteQuota;
}
