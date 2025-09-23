package com.dotlinea.soulecho.service.impl;


import com.dotlinea.soulecho.assistant.SeparateChatAssistant;
import com.dotlinea.soulecho.config.ApiServiceConfig;
import com.dotlinea.soulecho.po.ChatForm;
import com.dotlinea.soulecho.service.AssistantService;
import com.dotlinea.soulecho.speechTranscriber.SpeechRecognition;
import com.dotlinea.soulecho.speechTranscriber.SpeechTranscriberWithMicrophone;
import com.dotlinea.soulecho.speechTranscriber.Synthesis;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AssistantServiceImpl implements AssistantService {

    @Autowired
    private SeparateChatAssistant separateChatAssistant;

    @Autowired
    private ApiServiceConfig apiServiceConfig;

    @Override
    public String SpeechTranscriberWithMicrophone() {
        SpeechTranscriberWithMicrophone demo = new SpeechTranscriberWithMicrophone(apiServiceConfig.getAppKey(), apiServiceConfig.getToken());
        demo.process();
        demo.shutdown();
        return "";
    }

    @Override
    public String SpeechRecognition(String file, Long id, Long CharacterId) {
        SpeechRecognition demo = new SpeechRecognition(apiServiceConfig.getAppKey(), apiServiceConfig.getToken(),"");
        demo.process(file,16000);
        log.info("用户说的话"+demo.finalRecognizedText);
        demo.shutdown();
        ChatForm chatForm = new ChatForm();
        chatForm.setMemoryId(id);
        chatForm.setMessage(demo.finalRecognizedText);
        //和大模型对话的结果
        String chat = separateChatAssistant.chat(chatForm.getMemoryId(), chatForm.getMessage());
        log.info("大模型回答的话"+chat);
        //语言合成
        Synthesis synthesis = new Synthesis(apiServiceConfig.getAppKey(), apiServiceConfig.getToken(),"");
        synthesis.process(chat);
        return chat;
    }
}

