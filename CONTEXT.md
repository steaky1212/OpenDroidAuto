# OpenDroidAuto

An app that runs on an Android 4 car head unit and shows the phone's Android Auto session on the car's screen and speakers.

## Language

**OPA**:
This app (OpenDroidAuto), as the owner calls it.
_Avoid_: HU app, the project

**Head unit**:
The car's infotainment device that OPA runs on (here, a Honda Jazz unit running Android 4).
_Avoid_: Car, device, tablet

**Phone**:
The Android phone that runs Android Auto and sends video and audio to OPA.
_Avoid_: Client, source

**Audio channel**:
One of the separate audio streams the phone sends to OPA: Media, Speech or System.
_Avoid_: Audio stream, track

**Media stream**:
The Audio channel carrying music and other media playback.
_Avoid_: Music channel

**Speech channel**:
The Audio channel carrying assistant and navigation voice prompts. Phone calls do not use it: they stop OPA.

**System channel**:
The Audio channel carrying short system sounds such as notifications.

**Smooth audio**:
Audio with no audible stutter or dropout. This is how the owner judges audio quality, by ear on the head unit.
_Avoid_: Good audio, low latency
