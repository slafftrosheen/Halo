# Modern Voice Animation for Home Page

Implement a sophisticated, state-driven voice animation for the `HaloStateOrb` component, adhering to the `HALO-GLASS-DESIGN.md` specification.

## User Review Required

> [!IMPORTANT]
> The animations are strictly functional and follow the specific patterns defined in the Halo Glass design system. No decorative "glow" or "particles" will be added, as per the repo guidelines.

## Proposed Changes

### [Component Name]

#### [MODIFY] [HaloStateOrb.kt](file:///C:/Users/slavd/Halo/app/src/main/java/com/halo/app/ui/glass/components/HaloStateOrb.kt)
Complete overhaul of the `HaloStateOrb` component to implement the following state visualizations:
- **IDLE**: Thin ring with a subtle 4s breath animation.
- **LISTENING**: Filled central dot with a 1.5s pulsing outer ring.
- **WAKEWORD**: Brief emphatic pulse.
- **CAPTURING**: Solid dot with a simulated waveform effect.
- **RECOGNIZING**: Outer ring with three orbiting dots (2s cycle).
- **REASONING**: Central dot with a rapid 0.8s scale pulse.
- **PLANNING**: Central dot with outer ring segments filling clockwise.
- **EXECUTING**: Central dot with a rotating progress ring.
- **SPEAKING**: Central dot with multiple outward sonar pulses.
- **RECOVERING**: Amber ring with a slow 4s breath.
- **STOPPED**: A subtle cross visualization that fades out.

The implementation will use Compose `Canvas` for precise control and `rememberInfiniteTransition` for synchronized animations.

## Verification Plan

### Automated Tests
- Build the project to ensure no regressions in `HomeScreen` or `HaloFloatingMic`.
- (Optional) Create a temporary `Preview` to verify animations in the IDE if `render_compose_preview` is used.

### Manual Verification
- Deploy to a device/emulator and interact with the home page microphone button.
- Cycle through states in the `HomeViewModel` (mocked or real) to see each animation phase.
