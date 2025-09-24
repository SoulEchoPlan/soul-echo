package com.dotlinea.soulecho.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Data

public class ApiServiceConfig {

    @Value("${custom.app-key}")
    private String appKey;

    @Value("${custom.token}")
    private String token;
}