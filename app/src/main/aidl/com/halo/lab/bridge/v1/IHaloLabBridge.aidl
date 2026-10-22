package com.halo.lab.bridge.v1;

/** Narrow versioned JSON envelope transport. Every method applies protocol bounds server-side. */
interface IHaloLabBridge {
    String handshake(String requestJson);
    String listModels(String requestJson);
    String acquireArtifactLease(String requestJson);
    String renewArtifactLease(String requestJson);
    String releaseArtifactLease(String requestJson);
    String submitCase(String requestJson);
    String queryCase(String requestJson);
    String cancelCase(String requestJson);
    String readEvents(String requestJson);
    String acknowledgeEvents(String requestJson);
    String acquireModel(String requestJson);
    String queryAcquisition(String requestJson);
    String cancelAcquisition(String requestJson);
}
