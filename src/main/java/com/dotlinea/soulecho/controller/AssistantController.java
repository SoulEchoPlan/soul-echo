package com.dotlinea.soulecho.controller;

import com.dotlinea.soulecho.assistant.SeparateChatAssistant;
import com.dotlinea.soulecho.dto.TextAndAudioDTO;
import com.dotlinea.soulecho.dto.TextAndAudioVo;
import com.dotlinea.soulecho.entity.Characters;
import com.dotlinea.soulecho.entity.Smartvoice;
import com.dotlinea.soulecho.mapper.CharactersMapper;
import com.dotlinea.soulecho.mapper.SmartvoiceMapper;
import com.dotlinea.soulecho.po.ChatForm;
import com.dotlinea.soulecho.service.AssistantService;
import com.dotlinea.soulecho.speechTranscriber.SpeechRecognition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.socket.BinaryMessage;
import reactor.core.publisher.Flux;

@Tag(name = "会话接口")
@RestController
@RequestMapping("/ai")
@Slf4j
public class AssistantController {

    @Autowired
    private SeparateChatAssistant separateChatAssistant;

    @Autowired
    private AssistantService assistantService;

    @Autowired
    private CharactersMapper charactersMapper;

    @Operation(summary = "对话")
    @PostMapping(value = "/char", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestBody ChatForm chatForm){
//        Flux<String> chat = separateChatAssistant.chat(chatForm.getMemoryId(), chatForm.getMessage());
//        return chat;
        Characters byId = charactersMapper.selectById(3);
        String personaPrompt = byId.getPersonaprompt();
        System.out.println(personaPrompt);
        Flux<String> stringFlux = separateChatAssistant.chat(chatForm.getMemoryId(), chatForm.getMessage(), personaPrompt)
                .doOnNext(System.out::println)  // 打印每个元素
                .doOnError(error -> System.err.println("Error: " + error.getMessage()));
        return stringFlux;
    }

    @Operation(summary = "麦克风识别的语音转文字")
    @GetMapping("/SpeechTranscriber")
    public String SpeechTranscriber(){
        String text = assistantService.SpeechTranscriberWithMicrophone();
        return text;
    }

    @Operation(summary = "语音转文字测试")
    @GetMapping("/SpeechRecognition")
    public byte[] SpeechRecognition(@RequestParam Long id, @RequestParam int CharacterId){
        byte[] audiodata = assistantService.SpeechRecognition("D:\\java web\\demo1\\src\\main\\resources\\tts_test.wav", id, CharacterId);
        return audiodata;
    }

    @Operation(summary = "用文字代替语音模拟功能")
    @GetMapping("/Simulate")
    public String SpeechRecognition1(@RequestParam String text, @RequestParam Long id, @RequestParam int CharacterId){
        String simulate = assistantService.Simulate(text, id, CharacterId);
        return simulate;
    }

    //前端传音频流，返回经过处理返回ai生成的回答和其回答的二进制音频流
    @Operation(summary = "音频流转文字")
    @PostMapping("/AudioStreamingToText")
    public TextAndAudioVo AudioStreamingToText(@RequestBody TextAndAudioDTO text){
        TextAndAudioVo textAndAudioVo = assistantService.AudioStreamingToText(text);
        return textAndAudioVo;
    }

    //前端传音频流，返回经过处理返回ai生成的回答文本
    @Operation(summary = "音频流转文字返回文本")
    @PostMapping("/AudioStreamingToText1")
    public String AudioStreamingToText1(@RequestBody TextAndAudioDTO text){
        String message = assistantService.AudioStreamingToText1(text);
        //也可以返回Flux流的数据，流式展示文本
        return message;
    }

    //前端传音频流，返回经过处理返回ai生成的回答流式文本
    @Operation(summary = "音频流转文字返回流式文本")
    @PostMapping("/AudioStreamingToText2")
    public Flux<ServerSentEvent<String>> AudioStreamingToText2(@RequestBody TextAndAudioDTO text){
        Flux<ServerSentEvent<String>> message = assistantService.AudioStreamingToText2(text);
        return message;
    }

    //前端传音频流，返回经过处理返回ai生成的回答文本合成的音频流
    @Operation(summary = "音频流转文字返回二进制音频流")
    @PostMapping("/AudioStreamingToText3")
    public byte[] AudioStreamingToText3(@RequestBody TextAndAudioDTO text){
        byte[] result = assistantService.AudioStreamingToText3(text);
        return result;
    }

}
