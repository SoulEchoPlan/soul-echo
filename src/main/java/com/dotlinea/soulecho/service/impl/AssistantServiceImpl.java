package com.dotlinea.soulecho.service.impl;


import com.dotlinea.soulecho.assistant.SeparateChatAssistant;
import com.dotlinea.soulecho.entity.Characters;
import com.dotlinea.soulecho.entity.Smartvoice;
import com.dotlinea.soulecho.mapper.CharactersMapper;
import com.dotlinea.soulecho.mapper.SmartvoiceMapper;
import com.dotlinea.soulecho.po.ChatForm;
import com.dotlinea.soulecho.service.AssistantService;
import com.dotlinea.soulecho.speechTranscriber.SpeechRecognition;
import com.dotlinea.soulecho.speechTranscriber.SpeechTranscriberWithMicrophone;
import com.dotlinea.soulecho.speechTranscriber.Synthesis;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class AssistantServiceImpl implements AssistantService {

    @Autowired
    private SeparateChatAssistant separateChatAssistant;

    @Autowired
    private CharactersMapper charactersMapper;

    @Autowired
    private SmartvoiceMapper smartvoiceMapper;

    @Override
    public String SpeechTranscriberWithMicrophone() {
        Smartvoice smartvoice = smartvoiceMapper.selectById(1);
        SpeechTranscriberWithMicrophone demo = new SpeechTranscriberWithMicrophone(smartvoice.getAppkey(), smartvoice.getToken());
        demo.process();
        demo.shutdown();
        return "";
    }

    @Override
    public byte[] SpeechRecognition(String file, Long id, int CharacterId) {
        Smartvoice smartvoice = smartvoiceMapper.selectById(1);
        SpeechRecognition demo = new SpeechRecognition(smartvoice.getAppkey(), smartvoice.getToken(),"");
        demo.process(file,16000);
        log.info("用户说的话:"+demo.finalRecognizedText);
        demo.shutdown();
        ChatForm chatForm = new ChatForm();
        chatForm.setMemoryId(id);
        chatForm.setMessage(demo.finalRecognizedText);
        Characters characters = charactersMapper.selectById(CharacterId);
        //和大模型对话的结果
        Flux<String> chat = separateChatAssistant.chat(chatForm.getMemoryId(), chatForm.getMessage(),characters.getPersonaprompt());
//        log.info("大模型回答的话:"+chat.subscribe(word -> System.out.println("-> " + word)));
        //拼接Flux中的字符串
        Mono<String> combinedMono = chat
                .collect(
                        () -> new StringBuilder(), // 初始化一个 StringBuilder
                        (sb, str) -> sb.append(str)  // 对每个字符串执行 append 操作
                )
                .map(StringBuilder::toString);
        String text = combinedMono.block();

        if (characters == null) {
            throw new RuntimeException("不存在该角色");
        }
        //语音合成
        Synthesis synthesis = new Synthesis(smartvoice.getAppkey(), smartvoice.getToken(),"");
        byte[] data = synthesis.process(text,characters.getVoiceid());
        synthesis.shutdown();
        return data;
    }
}

