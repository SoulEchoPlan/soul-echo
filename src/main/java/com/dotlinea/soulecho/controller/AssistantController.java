package com.dotlinea.soulecho.controller;


import com.dotlinea.soulecho.assistant.SeparateChatAssistant;

import com.dotlinea.soulecho.po.ChatForm;
import com.dotlinea.soulecho.service.AssistantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Tag(name = "会话接口")
@RestController
@RequestMapping("/ai")
public class AssistantController {

    @Autowired
    private SeparateChatAssistant separateChatAssistant;

    @Autowired
    private AssistantService assistantService;

    @Operation(summary = "对话")
    @PostMapping("/char")
    public String chat(@RequestBody ChatForm chatForm){
        String chat = separateChatAssistant.chat(chatForm.getMemoryId(), chatForm.getMessage());
        return chat;
    }

    @Operation(summary = "麦克风识别的语音转文字")
    @GetMapping("/SpeechTranscriber")
    public String SpeechTranscriber(){
        String text = assistantService.SpeechTranscriberWithMicrophone();
        return text;
    }

    @Operation(summary = "语音转文字")
    @GetMapping("/SpeechRecognition")
    public String SpeechRecognition(@RequestParam Long id, @RequestParam Long CharacterId){
        String s = assistantService.SpeechRecognition("D:\\java web\\demo1\\src\\main\\resources\\tts_test.wav", id,CharacterId);
        return s;
    }
}
