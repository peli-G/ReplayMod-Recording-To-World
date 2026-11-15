package com.pelig.replaytoworld;

final class VersionSupport {

    private VersionSupport() {}

    static boolean accepts(String recordingVersionName) {
        if (recordingVersionName == null || recordingVersionName.isBlank()) return true;
        String current = net.minecraft.SharedConstants.getCurrentVersion().name();
        return current.equals(recordingVersionName) || "1.21.10".equals(recordingVersionName);
    }
}
