package com.leo.demo.rx

import com.leo.demo.rx.PlayState.STANDBY

const val UNKNOWN_VOLUME = -1L


data class VolumeData(
    val timestamp: Long = UNKNOWN_VOLUME,
    val name: String = "音频 $timestamp",
    val readState: ReadState = ReadState.UNREAD,
    val playState: PlayState = STANDBY,
    val playMode: PlayMode = PlayMode.PLAY_MODE_STANDBY
)