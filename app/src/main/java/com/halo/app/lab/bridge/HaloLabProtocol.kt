package com.halo.app.lab.bridge

/** Hash is generated from protocol/halo-lab-bridge-v1.json and is copied into HaloModelLab. */
internal object HaloLabProtocol {
    const val MAJOR = 1
    const val MINOR = 1
    const val MAX_REQUEST_BYTES = 32 * 1024
    const val MAX_REPLY_BYTES = 64 * 1024
    const val MAX_PAGE_ITEMS = 50
    const val SHA256 = "b23f23b9812bb425862adaa98e5b4ad7549678967291ee6959909bbf6bf97859"
    const val LAB_CLIENT_SCHEMA_SHA256 = "d866967284a236d37c39a720f7c13c46f39b1813f02f560c1857943727bcf231"
    const val FAST_SMOKE_FIXTURE_ID = "fast-smoke-1-short-ready"
    const val FAST_SMOKE_FIXTURE_SHA256 = "59e0652c15e4dff7dd7bfebd7cd7c04b478ddf8824995f9a610e13b9c90dfa40"
    const val BRIDGE_PERMISSION = "com.aistudio.braintool.xkqzps.permission.HALO_LAB_BRIDGE"
    const val LAB_PACKAGE = "com.slafftrosheen.halomodellab"
}
