package com.ruoyi.wechat.mapper;

import com.ruoyi.wechat.entity.WechatHosting;
import com.ruoyi.wechat.entity.WechatMessageLog;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface WechatHostingMapper {

    // --- 托管配置 ---
    @Select("SELECT * FROM wechat_hosting ORDER BY create_time DESC")
    List<WechatHosting> listAll();

    @Select("SELECT * FROM wechat_hosting WHERE enabled='1'")
    List<WechatHosting> listEnabled();

    @Insert("INSERT INTO wechat_hosting(name,chat_id,chat_type,enabled,interval_seconds,auto_reply,reply_prompt,library_id,work_hours_only,create_by) " +
            "VALUES(#{name},#{chatId},#{chatType},'0',#{intervalSeconds},#{autoReply},#{replyPrompt},#{libraryId},#{workHoursOnly},#{createBy})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(WechatHosting h);

    @Update("UPDATE wechat_hosting SET name=#{name},interval_seconds=#{intervalSeconds},auto_reply=#{autoReply}," +
            "reply_prompt=#{replyPrompt},library_id=#{libraryId},work_hours_only=#{workHoursOnly},update_time=NOW() WHERE id=#{id}")
    int update(WechatHosting h);

    @Update("UPDATE wechat_hosting SET library_id=#{libraryId},update_time=NOW() WHERE id=#{id}")
    int setLibrary(@Param("id") Long id, @Param("libraryId") Long libraryId);

    @Update("UPDATE wechat_hosting SET enabled=#{enabled},update_time=NOW() WHERE id=#{id}")
    int toggle(@Param("id") Long id, @Param("enabled") String enabled);

    @Delete("DELETE FROM wechat_hosting WHERE id=#{id}")
    int deleteById(Long id);

    // --- 消息日志 ---
    @Select("SELECT * FROM wechat_message_log WHERE hosting_id=#{hostingId} ORDER BY create_time DESC LIMIT #{limit}")
    List<WechatMessageLog> getLogs(@Param("hostingId") Long hostingId, @Param("limit") int limit);

    @Insert("INSERT INTO wechat_message_log(hosting_id,msg_id,from_user,msg_type,content,ai_reply,direction,status) " +
            "VALUES(#{hostingId},#{msgId},#{fromUser},#{msgType},#{content},#{aiReply},#{direction},#{status})")
    int insertLog(WechatMessageLog log);

    @Update("UPDATE wechat_message_log SET ai_reply=#{aiReply},direction=#{direction},status=#{status} WHERE id=#{id}")
    int updateLog(WechatMessageLog log);
}
