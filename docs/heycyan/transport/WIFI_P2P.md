> Status: HISTORICAL RESEARCH. Classified 2026-09-18.
>
> Recorded vendor/capture analysis and hypotheses from earlier work. Statements below, including VERIFIED labels and code sketches, retain their original research scope; they are not assertions of current Halo behavior or current-build hardware acceptance. Some findings conflict across reports. Trace artifacts before reuse; current code/wiring is documented in the implementation status page.
>
> [Current reference](../../protocols/heycyan/IMPLEMENTATION_STATUS.md).

# HeyCyan Wi-Fi & P2P

HeyCyan media transfer utilizes a direct Wi-Fi connection to the glasses.

## Connection Flow
1. The host device requests a Wi-Fi or P2P connection to the glasses.
2. Once connected, the host is assigned an IP address, and the glasses expose an HTTP server.
3. The SDK `WifiInfoReq` (Action `0xFC` / `ACTION_OTA_SOC`) allows the host to send URLs or network information to the System on a Chip (SOC) over BLE. 
   - Payload: `02 [Length] [URL String]`

## Halo Implementation
In the Halo Architecture, the Wi-Fi management is delegated to the Host application layer. The Host Android App handles Wi-Fi Direct/P2P negotiation or Hotspot connections and provides the resolved IP address to `DefaultHeyCyanMediaApi`.

*(Extracted from SDK analysis and Halo architecture constraints)*
