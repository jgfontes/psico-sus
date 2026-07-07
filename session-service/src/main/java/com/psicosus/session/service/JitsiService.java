package com.psicosus.session.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class JitsiService {

    private final String baseUrl;
    private final String roomPrefix;

    public JitsiService(@Value("${psicosus.jitsi.base-url}") String baseUrl,
                         @Value("${psicosus.jitsi.room-prefix}") String roomPrefix) {
        this.baseUrl = baseUrl;
        this.roomPrefix = roomPrefix;
    }

    /**
     * Generates a unique room name and the Jitsi access link.
     * The room name is based on the session UUID to guarantee uniqueness.
     */
    public JitsiRoomDTO generateRoom(UUID sessionId) {
        String roomName = roomPrefix + sessionId;
        String link = baseUrl + roomName;
        return new JitsiRoomDTO(roomName, link);
    }

    public record JitsiRoomDTO(String roomName, String jitsiLink) {
    }
}
