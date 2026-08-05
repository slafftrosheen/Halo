package com.halo.runtime.kernel.bus

import com.halo.icl.ontology.Observation

interface PerceptionBus : SystemBus<Observation> {
    override val name: String
        get() = "PerceptionBus"
}
