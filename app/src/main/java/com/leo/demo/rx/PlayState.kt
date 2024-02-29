package com.leo.demo.rx


enum class PlayState {
    // The volume is ready to play.
    READY,

    // The volume is playing.
    PLAYING,

    // The volume play complete.
    COMPLETED,

    // The volume play failed.
    FAILED,

    // The volume is standby.
    STANDBY
}
