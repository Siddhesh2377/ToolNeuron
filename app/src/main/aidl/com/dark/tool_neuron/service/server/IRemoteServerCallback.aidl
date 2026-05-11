package com.dark.tool_neuron.service.server;

interface IRemoteServerCallback {
    void onStateChanged(String snapshotJson);
    void onRequestEvent(String eventJson);
}
