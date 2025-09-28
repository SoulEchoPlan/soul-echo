package com.dotlinea.soulecho.dto;

import lombok.Data;

@Data
public class TextAndAudioDTO {
    private byte[] text;//二进制音频流
    private Long id;//ai聊天会话id
    private int characterId;//角色id
}
