import { registerRootComponent } from "expo";
import { enableScreens, enableFreeze } from "react-native-screens";
import App from "./App";

// Native-screens + freeze: keeps off-screen routes detached and pauses
// their JS work. Big win on low-RAM Android and Android TV — frees the
// JS thread while a user is deep in playback or settings.
enableScreens(true);
enableFreeze(true);

registerRootComponent(App);
