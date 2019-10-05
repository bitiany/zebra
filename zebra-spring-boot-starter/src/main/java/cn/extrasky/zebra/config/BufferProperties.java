package cn.extrasky.zebra.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;


/**
 * @description:
 * @author: 田培融
 * @date: 2019-09-25 19:48
 */
//@ConfigurationProperties(prefix = "buffer")
@Data
public class BufferProperties {

    private String host;

    private Integer database;

    private Integer port;

    private String password;

    private String prefix;

    private List<IdStoreProperties> idStoreList;



}
