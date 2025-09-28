package com.dotlinea.soulecho.service;

import com.dotlinea.soulecho.entity.ChatHistory;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
* @author lifeng
* @description 针对表【Chat_history(聊天记录表)】的数据库操作Service
* @createDate 2025-09-27 14:38:46
*/
public interface ChatHistoryService extends IService<ChatHistory> {

    List<ChatHistory> selectByMemoryId(Long id, String characterType);
}
