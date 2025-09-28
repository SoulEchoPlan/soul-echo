package com.dotlinea.soulecho.service;

import com.dotlinea.soulecho.dto.TextAndAudioDTO;
import com.dotlinea.soulecho.dto.TextAndAudioVo;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

public interface AssistantService {

    String SpeechTranscriberWithMicrophone();

    byte[] SpeechRecognition(String string, Long id, int CharacterId);

    String Simulate(String text, Long id, int characterId);

    TextAndAudioVo AudioStreamingToText(TextAndAudioDTO text);

    String AudioStreamingToText1(TextAndAudioDTO text);

    Flux<ServerSentEvent<String>> AudioStreamingToText2(TextAndAudioDTO text);

    byte[] AudioStreamingToText3(TextAndAudioDTO text);


}
