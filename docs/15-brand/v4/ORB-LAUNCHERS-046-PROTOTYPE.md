# Orb launchers — implementation prototype

The supplied Open Ring stays unchanged. Its action disclosure is local to the
existing Home presence, Screen chrome and Mini Halo presence. It starts quiet;
there is no floating radial menu, orbiting animation, overlay service or AOD claim.
All targets are at least48dp. Choosing a group opens the same #45 catalog and its
fresh Use resolver; disclosure does not execute, request permission or call a provider.

| State | Orb action | Discovery and cancellation |
| --- | --- | --- |
| Collapsed / idle | Open minimal chooser | A separate labelled disclosure remains reachable. |
| Open, spacious tablet | In-place bounded group chips | Create, Find, Notebook and Phone can be chosen; All tools remains. |
| Open,360/412dp, short landscape or200% text | Accessible scrolling native sheet | The same linear controls replace the in-place chips. |
| Actual microphone capture | Stop through existing voice owner | Only actual SpeechInputStatus.capturing projects a live microphone. |
| Capture requested / speech recognition / active work | Stop | Requested input says Getting ready; recognition says Turning voice into text. |
| Speaking | Interrupt reply and talk | The chooser also exposes a separate Stop. |
| Approval / clarification | Open Activity review | Active work retains separate Stop. No inline approval bypass. |
| Failure / offline | Keep real state and existing voice setup | Discovery can show actual catalog setup/limitations; no invented success. |
| Reduced motion / TalkBack | Linear native sheet | Existing static-state art/motion rules remain; no radial spatial dependency. |
| Background / primary-action transition | Collapse transient disclosure | No deferred choice executes on resume. |
| Process recreation | Start collapsed; restore shortcut preference | No command, execution result or pending action is persisted by the launcher. |

The saved shortcut preference lives in the existing AppearancePreferences owner.
Only bounded enum names are decoded, including an explicit empty set; unknown
names cannot become routes. It is independent of the existing theme setting.
All tools and the voice/review/Stop entry remain available when no groups are selected.

Home, Screen and Mini use HomeOrbControl over the same HomeViewModel/runtime.
Notebook targets the existing work.notebook descriptor; other groups select only
their canonical catalog group. Mini no longer submits a separate open-camera text
shortcut. Screen keeps its controls visible while input/work is active. Group
navigation preserves the Home draft and does not select a provider or consent route.

Source fixtures cover the360/200% Home header, collapsed/open/capture/approval/error/
offline launchers,412/200%, an800dp inline tablet and the reduced-motion tablet list.
Mini's existing320/240dp, EN/LV/RU and enlarged-text fixtures include the shared
control. These are prototype/host evidence until actually recorded and inspected;
they are not phone, TalkBack, microphone, provider or network qualification.
Named device acceptance and any remaining failures belong in #43.
