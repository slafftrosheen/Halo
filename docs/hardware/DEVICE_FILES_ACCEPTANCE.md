# Device files — local acceptance for #66

Implementation: [batch 004](../engineering/HALO-DEVICES-066-EXPANSION-004.md). No hardware results are recorded yet.

## COLMI A2 / existing HeyCyan adapter

Use your exact saved A2, connected through its existing verified profile and READY. Names alone do not establish protocol compatibility.

1. Record one photo, a short video and a voice recording on the glasses. Open Devices → A2 → Files on glasses → Browse. Approve the Android Wi-Fi request if shown. Confirm all manifest categories/names; browsing must not silently import or capture anything.
2. Select a photo and video, Copy selected to Library. Observe byte/item progress. Open each saved result via Continue in Halo. For photos, exercise available Studio/Chat/Study destinations with explicit privacy policy. For videos, open using a compatible Android viewer. Compare exported bytes/checksum; do not treat filename or container MIME as playback evidence.
3. Import the voice recording. If its bytes have a recognized container, exercise the compatible local playback/Canvas path. A raw proprietary recording must remain an original file with a format warning; do not claim transcription/codec support from successful transfer alone.
4. Import the same files again and restart Halo. Confirm one canonical file per device+remote ID+digest. Rename/edit the Library title, Trash it, repeat import: no duplicate or implicit restore. Restore from Library and read the same bytes. Delete/replace the source file on the glasses using its own supported UI: only an explicit fresh browse/import may discover changed bytes.
5. Export a 24–100 MiB imported video from its Library file details. Pick a destination. Confirm VERIFIED only after actual destination readback; compare SHA-256 using an independent local tool. Deny the picker, remove read permission, or disconnect destination storage: retain cancelled/failed/unknown outcomes, never report an exported copy or replay a write automatically.
6. Cancel during the second file of a multi-file import. First committed file remains; partial file is absent. Cancelled status comes after cleanup. Toggle Bluetooth/Wi-Fi, turn off the glasses, switch selected device, background/lock Halo and force-stop during transfer. After restart there must be no automatic download/reconnect caused by the lost operation; completed Library files remain and the next explicit operation starts cleanly.
7. Check insufficient phone storage, unknown size, >256 MiB, a changed manifest and a failure during mode restoration. Preserve existing Library files. No guessed device-delete/reset command may be sent. Local Library Trash affects only the copy.
8. Repeat catalog/import/stop via typed then genuinely spoken requests. Capture resolved tool IDs, opaque versions, phases and saved object IDs without storing private media/credentials in Git. Power metadata needs existing sharing approval; export prepare/status tools intentionally reject the remote route.

## Native photo adapters

For an adapter whose `aiPhotoAvailable` is actually true, capture one image into shared review, Save, reopen its Library/Notebook object and use existing attachment actions. Discard an unsaved capture and verify it disappears; discard a saved review and verify the Library object remains. Try an old captureId/assetId after a new capture: it must reject. Frame status support does not supply a camera/file transport.

EyeVue/TuneBuds media-session lifecycle helpers are not Android file adapters. Do not mark their full album download supported based on this batch. XREAL xbx a01+ remains a display endpoint and Huion Inspiroy 2 remains a pen-input endpoint; no device filesystem is inferred for either.

Record APK/commit, Android API, exact device/firmware, sample format and size, expected/actual SHA-256, and pass/fail for each action. Keep #66 open until real-device and voice/provider evidence exists.
