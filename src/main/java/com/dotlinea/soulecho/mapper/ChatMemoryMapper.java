package com.dotlinea.soulecho.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dotlinea.soulecho.entity.ChatMemory;
import org.apache.ibatis.annotations.Mapper;

/**
* @author lifeng
* @description 针对表【chat_memory】的数据库操作Mapper
* @createDate 2025-09-25 20:47:55
* @Entity com.dotlinea.soulecho.entity.ChatMemory
*/
@Mapper
public interface ChatMemoryMapper extends BaseMapper<ChatMemory> {

    ChatMemory getMessagesId(Object memoryId);

    void updateMessagesId(Object memoryId, String messagesToJson);

    void deleteMessagesId(Object memoryId);
}




