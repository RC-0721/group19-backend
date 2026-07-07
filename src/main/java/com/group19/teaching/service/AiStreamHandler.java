package com.group19.teaching.service;

@FunctionalInterface
public interface AiStreamHandler {

    void onDelta(String content);
}
