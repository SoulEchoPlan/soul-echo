package com.dotlinea.soulecho.dto;

import lombok.Data;
import reactor.core.publisher.Flux;

@Data
public class TextAndAudioVo {
    private String text;//id的回答
    private byte[] audio;//二进制音频流
}
