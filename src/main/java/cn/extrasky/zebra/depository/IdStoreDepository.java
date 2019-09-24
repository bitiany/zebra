package cn.extrasky.zebra.depository;

import cn.extrasky.zebra.model.IdStore;

/**
 * @author YangGuodong
 * @date: 2019-08-21
 */
public interface IdStoreDepository {

    /**
     *  保存
     * @param store
     * @throws Exception
     */
    void save(IdStore store) throws Exception;

    /**
     *  查询
     * @param key
     * @return
     * @throws Exception
     */
    IdStore queryByStoreKey(String key) throws Exception;
}
