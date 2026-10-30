package com.example.stoneworks.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModPayloads {
    private ModPayloads() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(
            ActivateRelayPayload.TYPE,
            ActivateRelayPayload.STREAM_CODEC,
            SurveyRelayPayloadHandler::activate
        );
        registrar.playToClient(
                ResearchProgressPayload.TYPE,
                ResearchProgressPayload.STREAM_CODEC,
                ResearchProgressPayloadHandler::handle
        );
    }
}