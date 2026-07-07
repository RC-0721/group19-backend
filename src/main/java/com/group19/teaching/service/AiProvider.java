package com.group19.teaching.service;

public interface AiProvider {

    boolean supports(String provider);

    AiProviderResult chat(AiRequest request);

    default AiProviderStreamResult stream(AiRequest request, AiStreamHandler handler) {
        AiProviderResult result = chat(request);
        if (handler != null) {
            handler.onDelta(result.content());
        }
        return new AiProviderStreamResult(
                result.model(),
                result.content(),
                result.tokenInput(),
                result.tokenOutput(),
                result.durationMs());
    }
}
