package com.dotlinea.soulecho.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * @author fanfan187
 * @version v1.0.0
 * @since v1.0.0
 */
@ResponseStatus(HttpStatus.NOT_FOUND) 
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}