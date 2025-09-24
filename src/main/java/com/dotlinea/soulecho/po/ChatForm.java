package com.dotlinea.soulecho.po;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatForm {

    private Long memoryId;
    private String message;
}
