# Release Candidate Blockers

There are currently **0** active release blockers.

## Resolved Blockers during Gate:
- **Compile Error:** `core:database` was missing mockito test dependency. (Fixed)
- **TV Focus Trap:** Settings TextFields trapped the D-pad due to multi-line input support. (Fixed)
- **Secret Leak:** Deleting an extension left its secure API keys orphaned in the Android Keystore/SharedPreferences without being wiped. (Fixed)
- **UI Freeze:** Searching and loading massive IPTV EPG maps blocked the Main thread causing ANRs. (Fixed)
