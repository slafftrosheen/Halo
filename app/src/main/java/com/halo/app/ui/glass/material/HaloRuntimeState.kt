package com.halo.app.ui.glass.material

/**
 * Runtime state enumeration for the Halo State Orb.
 * Provides clear visual feedback for each cognitive pipeline stage.
 * 
 * Design ref: HALO-GLASS-DESIGN.md
 */
enum class HaloRuntimeState {
    /** Idle (○) - Thin ring, subtle 4s breath animation */
    Idle,
    
    /** Listening (◉) - Filled dot, pulse ring 1.5s */
    Listening,
    
    /** Wakeword (◉) - Filled dot, brief pulse */
    Wakeword,
    
    /** Capturing (●) - Solid filled, waveform from center */
    Capturing,
    
    /** Recognizing (◌) - Ring, 3 orbiting dots animation 2s */
    Recognizing,
    
    /** Reasoning (◉) - Dot, subtle 0.8s scale pulse */
    Reasoning,
    
    /** Planning (◉) - Dot, ring segments fill clockwise */
    Planning,
    
    /** Executing (◉) - Dot, rotating progress ring */
    Executing,
    
    /** Speaking (◉) - Dot, outward sonar pulse rings */
    Speaking,
    
    /** Recovering (◌) - Amber ring, slow 4s breath */
    Recovering,
    
    /** Stopped (✕) - Cross, fade to idle */
    Stopped
}