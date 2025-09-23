package com.dotlinea.soulecho.service;

public interface AssistantService {

    String SpeechTranscriberWithMicrophone();

    String SpeechRecognition(String string, Long id, Long CharacterId);
}
