package com.dotlinea.soulecho.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dotlinea.soulecho.entity.ChatHistory;
import com.dotlinea.soulecho.service.ChatHistoryService;
import com.dotlinea.soulecho.mapper.ChatHistoryMapper;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
* @author lifeng
* @description 针对表【Chat_history(聊天记录表)】的数据库操作Service实现
* @createDate 2025-09-27 14:38:46
*/
@Service
@AllArgsConstructor
public class ChatHistoryServiceImpl extends ServiceImpl<ChatHistoryMapper, ChatHistory>
    implements ChatHistoryService{

    private final ChatHistoryMapper chatHistoryMapper;

    @Override
    public List<ChatHistory> selectByMemoryId(Long id, String characterType) {
        LambdaQueryWrapper<ChatHistory> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ChatHistory::getMemoryId, id);
        queryWrapper.eq(ChatHistory::getCharacterType, characterType);
        queryWrapper.orderByAsc(ChatHistory::getCreatedAt);
        return chatHistoryMapper.selectList(queryWrapper);
    }
}




