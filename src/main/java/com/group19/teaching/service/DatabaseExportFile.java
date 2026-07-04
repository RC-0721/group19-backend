package com.group19.teaching.service;

public record DatabaseExportFile(String filename, byte[] content, String contentType) {
}
