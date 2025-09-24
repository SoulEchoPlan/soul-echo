package com.dotlinea.soulecho.controller;


import com.dotlinea.soulecho.assistant.SeparateChatAssistant;

import com.dotlinea.soulecho.po.ChatForm;
import com.dotlinea.soulecho.service.AssistantService;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.TokenStream;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;

@Tag(name = "会话接口")
@RestController
@RequestMapping("/ai")
public class AssistantController {

    @Autowired
    private SeparateChatAssistant separateChatAssistant;

    @Autowired
    private AssistantService assistantService;

    @Operation(summary = "对话")
    @PostMapping(value = "/char", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestBody ChatForm chatForm){
//        Flux<String> chat = separateChatAssistant.chat(chatForm.getMemoryId(), chatForm.getMessage());
//        return chat;
        return separateChatAssistant.chat(chatForm.getMemoryId(), chatForm.getMessage())
                .doOnNext(System.out::println)  // 打印每个元素
                .doOnError(error -> System.err.println("Error: " + error.getMessage()));
    }

    @Operation(summary = "麦克风识别的语音转文字")
    @GetMapping("/SpeechTranscriber")
    public String SpeechTranscriber(){
        String text = assistantService.SpeechTranscriberWithMicrophone();
        return text;
    }

    @Operation(summary = "语音转文字")
    @GetMapping("/SpeechRecognition")
    public byte[] SpeechRecognition(@RequestParam Long id, @RequestParam Long CharacterId){
        byte[] audiodata = assistantService.SpeechRecognition("D:\\java web\\demo1\\src\\main\\resources\\tts_test.wav", id, CharacterId);
        return audiodata;
    }
}
