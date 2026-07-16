package com.ruoyi.wechat.mapper;

import com.ruoyi.wechat.entity.WechatConfig;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface WechatConfigMapper {

    @Select("SELECT * FROM wechat_config ORDER BY create_time DESC")
    List<WechatConfig> listAll();

    @Select("SELECT * FROM wechat_config WHERE id=#{id}")
    WechatConfig getById(Long id);

    @Select("SELECT * FROM wechat_config WHERE status='1' LIMIT 1")
    WechatConfig getActive();

    @Insert("INSERT INTO wechat_config(corp_id,corp_secret,agent_id,token,encoding_aes_key,webhook_url,status,create_by) " +
            "VALUES(#{corpId},#{corpSecret},#{agentId},#{token},#{encodingAesKey},#{webhookUrl},'0',#{createBy})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(WechatConfig config);

    @Update("UPDATE wechat_config SET corp_id=#{corpId},corp_secret=#{corpSecret},agent_id=#{agentId}," +
            "token=#{token},encoding_aes_key=#{encodingAesKey},webhook_url=#{webhookUrl},update_time=NOW() WHERE id=#{id}")
    int update(WechatConfig config);

    @Update("UPDATE wechat_config SET status=#{status},update_time=NOW() WHERE id=#{id}")
    int toggleStatus(@Param("id") Long id, @Param("status") String status);

    @Delete("DELETE FROM wechat_config WHERE id=#{id}")
    int deleteById(Long id);
}
