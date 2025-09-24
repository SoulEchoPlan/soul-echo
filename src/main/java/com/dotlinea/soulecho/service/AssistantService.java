package com.dotlinea.soulecho.service;

public interface AssistantService {

    String SpeechTranscriberWithMicrophone();

    byte[] SpeechRecognition(String string, Long id, Long CharacterId);
}
