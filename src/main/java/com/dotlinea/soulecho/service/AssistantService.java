package com.dotlinea.soulecho.service;

import com.dotlinea.soulecho.dto.TextAndAudioDTO;
import com.dotlinea.soulecho.dto.TextAndAudioVo;

public interface AssistantService {

    String SpeechTranscriberWithMicrophone();

    byte[] SpeechRecognition(String string, Long id, int CharacterId);

    String Simulate(String text, Long id, int characterId);

    TextAndAudioVo AudioStreamingToText(TextAndAudioDTO text);

    String AudioStreamingToText1(TextAndAudioDTO text);

    byte[] AudioStreamingToText2(TextAndAudioDTO text);
}
