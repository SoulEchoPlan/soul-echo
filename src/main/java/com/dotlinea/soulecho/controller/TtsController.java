//package com.dotlinea.soulecho.controller;
//
//import com.dotlinea.soulecho.config.ApiServiceConfig;
//import com.dotlinea.soulecho.speechTranscriber.Synthesis;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.core.io.InputStreamResource;
//import org.springframework.http.MediaType;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RequestParam;
//import org.springframework.web.bind.annotation.RestController;
//
//import java.io.ByteArrayInputStream;
//
//@RestController
//@RequestMapping("/tts")
//public class TtsController {
//
//
//
//    @Autowired
//    private ApiServiceConfig apiServiceConfig;
//
//    @GetMapping("/stream")
//    public ResponseEntity<InputStreamResource> streamTts(@RequestParam String text) {
//
//        // 初始化 Synthesis（需注入 appKey/token/url）
//        Synthesis syn = new Synthesis(apiServiceConfig.getAppKey(), apiServiceConfig.getToken(),"");
//        syn.process(text);
//
//        return ResponseEntity.ok()
//                .contentType(MediaType.parseMediaType("audio/wav"))
//                .body(new InputStreamResource(new ByteArrayInputStream(syn.getNextAudioChunk())));
//    }
//}
