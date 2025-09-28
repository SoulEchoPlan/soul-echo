package com.dotlinea.soulecho.service.impl;


import com.dotlinea.soulecho.assistant.SeparateChatAssistant;
import com.dotlinea.soulecho.dto.TextAndAudioDTO;
import com.dotlinea.soulecho.dto.TextAndAudioVo;
import com.dotlinea.soulecho.entity.Characters;
import com.dotlinea.soulecho.entity.ChatHistory;
import com.dotlinea.soulecho.entity.Smartvoice;
import com.dotlinea.soulecho.mapper.CharactersMapper;
import com.dotlinea.soulecho.mapper.ChatHistoryMapper;
import com.dotlinea.soulecho.mapper.SmartvoiceMapper;
import com.dotlinea.soulecho.po.ChatForm;
import com.dotlinea.soulecho.service.AssistantService;
import com.dotlinea.soulecho.speechTranscriber.SpeechRecognition;
import com.dotlinea.soulecho.speechTranscriber.SpeechTranscriber;
import com.dotlinea.soulecho.speechTranscriber.SpeechTranscriberWithMicrophone;
import com.dotlinea.soulecho.speechTranscriber.Synthesis;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@Slf4j
@AllArgsConstructor
public class AssistantServiceImpl implements AssistantService {

    @Autowired
    private SeparateChatAssistant separateChatAssistant;

    @Autowired
    private CharactersMapper charactersMapper;

    @Autowired
    private SmartvoiceMapper smartvoiceMapper;

    private final ChatHistoryMapper chatHistoryMapper;

    @Override
    public String SpeechTranscriberWithMicrophone() {
        Smartvoice smartvoice = smartvoiceMapper.selectById(1);
        SpeechTranscriberWithMicrophone demo = new SpeechTranscriberWithMicrophone(smartvoice.getAppkey(), smartvoice.getToken());
        String process = demo.process();
        demo.shutdown();
        return process;
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

    @Override
    public String Simulate(String text, Long id, int characterId) {
        Smartvoice smartvoice = smartvoiceMapper.selectById(1);
        Characters characters = charactersMapper.selectById(characterId);
        byte[] bytes = TextToSpeech(text, smartvoice.getAppkey(), smartvoice.getToken(), characters.getVoiceid());
        String message = SpeechToText(bytes, smartvoice.getAppkey(), smartvoice.getToken());
        String a = AIConversation(id, message, characters.getPersonaprompt());
        byte[] result = TextToSpeech(a, smartvoice.getAppkey(), smartvoice.getToken(), characters.getVoiceid());
        return a;
    }

    @Override
    public TextAndAudioVo AudioStreamingToText(TextAndAudioDTO text) {
        Smartvoice smartvoice = smartvoiceMapper.selectById(1);
        Characters characters = charactersMapper.selectById(text.getCharacterId());
        String message = SpeechToText(text.getText(), smartvoice.getAppkey(), smartvoice.getToken());
        String a = AIConversation(text.getId(), message, characters.getPersonaprompt());
        byte[] result = TextToSpeech(a, smartvoice.getAppkey(), smartvoice.getToken(), characters.getVoiceid());
        TextAndAudioVo textAndAudioVo = new TextAndAudioVo();
        textAndAudioVo.setText(a);
        textAndAudioVo.setAudio(result);
        return textAndAudioVo;
    }

    @Override
    public String AudioStreamingToText1(TextAndAudioDTO text) {
        Smartvoice smartvoice = smartvoiceMapper.selectById(1);
        Characters characters = charactersMapper.selectById(text.getCharacterId());
        String message = SpeechToText(text.getText(), smartvoice.getAppkey(), smartvoice.getToken());
        String a = AIConversation(text.getId(), message, characters.getPersonaprompt());
        return a;
    }

    @Override
    public byte[] AudioStreamingToText2(TextAndAudioDTO text) {
        Smartvoice smartvoice = smartvoiceMapper.selectById(1);
        Characters characters = charactersMapper.selectById(text.getCharacterId());
        String message = SpeechToText(text.getText(), smartvoice.getAppkey(), smartvoice.getToken());
        String a = AIConversation(text.getId(), message, characters.getPersonaprompt());
        byte[] result = TextToSpeech(a, smartvoice.getAppkey(), smartvoice.getToken(), characters.getVoiceid());
        return result;
    }

    //语音转文字
    public String SpeechToText(byte[] data,String appKey, String token){
        SpeechTranscriber speechTranscriber = new SpeechTranscriber(appKey,token,"");
        String message = speechTranscriber.process(data);
        speechTranscriber.shutdown();
        return message;
    }

    //ai对话
    public String AIConversation(Long memoryId,String message,String personaprompt){
        ChatHistory userchatHistory = new ChatHistory();
        //记录用户想问的问题
        userchatHistory.setMemoryId(memoryId);
        userchatHistory.setMessage(message);
        userchatHistory.setCharacterType("user");
        chatHistoryMapper.insert(userchatHistory);
        Flux<String> chat = separateChatAssistant.chat(memoryId,message,personaprompt);
        Mono<String> combinedMono = chat
                .collect(
                        () -> new StringBuilder(), // 初始化一个 StringBuilder
                        (sb, str) -> sb.append(str)  // 对每个字符串执行 append 操作
                )
                .map(StringBuilder::toString);
        String text = combinedMono.block();
        //记录ai回答的问题
        ChatHistory aichatHistory = new ChatHistory();
        aichatHistory.setMemoryId(memoryId);
        aichatHistory.setMessage(text);
        aichatHistory.setCharacterType("ai");
        chatHistoryMapper.insert(aichatHistory);
        return text;
    }

    //文字转语音
    public byte[] TextToSpeech(String text,String appKey, String token,String voice){
        Synthesis synthesis = new Synthesis(appKey, token,"");
        byte[] data = synthesis.process(text,voice);
        synthesis.shutdown();
        return data;
    }
}

