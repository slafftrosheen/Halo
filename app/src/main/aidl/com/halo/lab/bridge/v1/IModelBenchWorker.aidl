package com.halo.lab.bridge.v1;

import android.os.IBinder;

/** Private same-app controller for the native :modelbench process. */
interface IModelBenchWorker {
    String attachSupervisor(IBinder supervisorToken, String workerInstanceId);
    String startTextProbe(String attemptId, String modelId, String expectedSha256, long deadlineElapsedRealtimeMs);
    String queryAttempt(String attemptId);
    String requestStop(String attemptId);
}
