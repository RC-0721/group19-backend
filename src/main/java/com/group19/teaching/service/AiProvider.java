package com.group19.teaching.service;

public interface AiProvider {

    boolean supports(String provider);

    AiProviderResult chat(AiRequest request);
}
