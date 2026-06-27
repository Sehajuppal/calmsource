# Manifest Install Regression Gate

## Overview
This regression gate verifies that the Extension Hub and manifest installation flow functions reliably without crashing. The installation of Stremio addons and internal manifests must be safe and user-friendly.

## Critical Risks & Remaining Risks
* **Null Pointer Exceptions on Confirm**: Forcing non-null (`!!`) on extension metadata during the installation confirmation step can crash the app if the network response is malformed.
* **Serialization Failures**: Invalid JSON schemas might throw runtime exceptions during deserialization.
* **Remaining Risk**: Extremely large manifests (over 5MB) could cause OOM or freeze the UI thread if not carefully chunked or bounded during the download phase. 

## Verification Checklist
- [ ] Clicking "Install" on an extension does not crash the app.
- [ ] Null safety checks are enforced during manifest parsing and confirmation.
- [ ] Network exceptions during manifest download are caught and displayed as user-friendly error toasts/dialogs.
- [ ] Malformed manifests are gracefully rejected.
- [ ] Installed extensions appear correctly in the Extension Hub and Universal Search.
