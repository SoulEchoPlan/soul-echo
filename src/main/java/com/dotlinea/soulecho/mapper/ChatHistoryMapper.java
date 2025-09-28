package com.dotlinea.soulecho.mapper;

import com.dotlinea.soulecho.entity.ChatHistory;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
* @author lifeng
* @description 针对表【Chat_history(聊天记录表)】的数据库操作Mapper
* @createDate 2025-09-27 14:38:46
* @Entity com.dotlinea.soulecho.entity.ChatHistory
*/
@Mapper
public interface ChatHistoryMapper extends BaseMapper<ChatHistory> {

}




