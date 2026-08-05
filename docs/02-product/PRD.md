# Product Requirements Document (PRD)

## What problem are we solving?
Today's wearable AI relies on walled gardens. Devices are locked to specific providers, clouds, and ecosystems. Halo solves this by decoupling the hardware from the intelligence, placing the orchestrator (the Android phone) firmly under the user's control.

## Target Audience

### Primary
* Engineers and Developers
* IT Admins
* Field Technicians
* Makers and Hardware Hackers
* Power Users

### Secondary
* Accessibility Users
* Professionals
* Students

*Non-Goals*: We are not optimizing for social media, gaming, or general entertainment.

## Success Metrics
Success is measured by efficiency, trust, and speed, not engagement.
* **Wake Word Latency**: < 150ms
* **Time to Answer**: < 1 second for local tasks
* **Battery Drain**: < 1.5% per hour in background
* **Local Inference Rate**: Maximized
* **Percentage of Tasks Completed Offline**: > 80% for system controls
* **User Trust**: Minimal external network requests per day

## Minimum Viable Product (MVP)
The MVP establishes the Runtime, connects to one Driver (HeyCyan), and supports local audio pipeline (Wake Word + STT + TTS) with basic intent routing, before any complex AI integration.
