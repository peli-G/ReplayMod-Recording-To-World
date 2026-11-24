package com.pelig.replaytoworld;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class VersionSupport {

    private static final Pattern RELEASE = Pattern.compile("^(\\d+)\\.(\\d+)(?:\\.\\d+)?$");

    private VersionSupport() {}

    static boolean accepts(String recordingVersionName) {
        if (recordingVersionName == null || recordingVersionName.isBlank()) return true;
        String current = net.minecraft.SharedConstants.getCurrentVersion().name();
        if (current.equals(recordingVersionName)) return true;
        Matcher a = RELEASE.matcher(recordingVersionName);
        Matcher b = RELEASE.matcher(current);
        return a.matches() && b.matches() && a.group(1).equals(b.group(1)) && a.group(2).equals(b.group(2));
    }
}
