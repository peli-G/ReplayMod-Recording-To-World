package com.pelig.replaytoworld;

final class VersionSupport {

    private VersionSupport() {}

    static boolean accepts(String recordingVersionName) {
        if (recordingVersionName == null || recordingVersionName.isBlank()) return true;
        return net.minecraft.SharedConstants.getCurrentVersion().name().equals(recordingVersionName);
    }
}
