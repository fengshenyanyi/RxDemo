package com.leo.demo.rx

import UNKNOWN_VOLUME
import VolumeData
import android.annotation.SuppressLint
import androidx.compose.runtime.mutableStateOf
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.subjects.PublishSubject
import com.leo.demo.rx.PlayState.*
import com.leo.demo.rx.PlayMode.*

import java.util.concurrent.TimeUnit

class MainViewModel {
    val listData = mutableStateOf(emptyList<VolumeData>())

    /**
     *  Publish the play mode [PLAY_MODE_MANUAL] or [PLAY_MODE_AUTO]. start with [STANDBY]
     */
    private val playModePublisher = PublishSubject.create<PlayMode>()

    /**
     *  The volumes has been played
     */
    private val readVolumes = mutableSetOf<Long>()

    /**
     *  Active volume play state
     */

    private val activeVolumePublisher = PublishSubject.create<VolumeData>()

    @SuppressLint("CheckResult")
    fun setupListener() {
        Observable.combineLatest(
            listenToVolumes(), listenToVolumePlayMode(), listenToActiveVolume()
        ) { volumes, playMode, activeVolume ->
            // The volumes there is no play info,
            // so we need to update the volumes item play state and
            // play mode with the second and third observable source.
            //
            // 1: update the volume with read state, if the volume is read, it will not play in auto mode.
            // 2: update the volume with play mode, help to find the next play volume in auto mode.
            // 3: update the volume with play state, to show in ui.
            val readStateList = updateReadState(volumes)
            val playModeList = updatePlayMode(playMode, activeVolume, readStateList)
            val playStateList = updatePlayState(playMode, activeVolume, playModeList)
            playStateList
        }.observeOn(AndroidSchedulers.mainThread()).subscribe({ volumes ->
            // Update UI
            listData.value = volumes

            // Play volume
            volumes.find { READY == it.playState }?.let(::play)
        }, { e -> loge("Update ui failed, ${e.message}") })
    }

    /**
     *  Update the volumes with played list.
     */
    private fun updateReadState(volumes: List<VolumeData>): List<VolumeData> {
        val list = volumes.toMutableList()
        volumes.forEachIndexed { index, volumeData ->
            val state =
                if (readVolumes.contains(volumeData.timestamp)) ReadState.READ else ReadState.UNREAD
            list[index] = volumeData.copy(readState = state)
        }

        return list
    }

    private fun updatePlayMode(
        playMode: PlayMode, activeVolume: VolumeData, volumes: List<VolumeData>
    ): List<VolumeData> {
        val list = volumes.toMutableList()

        when (playMode) {
            PLAY_MODE_STANDBY -> {
                volumes.forEachIndexed { index, data ->
                    list[index] = data.copy(playMode = PLAY_MODE_STANDBY)
                }
            }

            PLAY_MODE_AUTO -> {
                volumes.forEachIndexed { index, data ->
                    list[index] = data.copy(
                        playMode = if (readVolumes.contains(data.timestamp)) {
                            PLAY_MODE_STANDBY
                        } else {
                            PLAY_MODE_AUTO
                        }
                    )
                }
            }

            PLAY_MODE_MANUAL -> {
                volumes.forEachIndexed { index, data ->
                    list[index] = data.copy(
                        playMode = if (data.timestamp == activeVolume.timestamp) {
                            PLAY_MODE_MANUAL
                        } else {
                            PLAY_MODE_STANDBY
                        }
                    )
                }
            }
        }

        return list
    }

    private fun updatePlayState(
        playMode: PlayMode, activeVolume: VolumeData, volumes: List<VolumeData>
    ): List<VolumeData> {
        val list = volumes.toMutableList()

        when (playMode) {
            // Standby mode:
            //
            // change all volumes play state to STANDBY.
            PLAY_MODE_STANDBY -> {
                volumes.forEachIndexed { index, data ->
                    list[index] = data.copy(playState = STANDBY)
                }
            }

            // Manual mode:
            //
            // only update the active volume play state, others change to STANDBY.
            PLAY_MODE_MANUAL -> {
                volumes.forEachIndexed { index, data ->
                    list[index] = if (data.timestamp == activeVolume.timestamp) {
                        data.copy(playState = activeVolume.playState)
                    } else {
                        data.copy(playState = STANDBY)
                    }
                }
            }

            // Auto mode:
            //
            // 1. when there is no active volume, make the first unread volume play state
            // to READY, others change to STANDBY.
            //
            // 2. when the active volume play state is COMPLETE/FAILED find then next unread volume,
            // if the next is found, make the next volume play state to READY, else all the volumes read.
            // when the active volume play state is STANDBY/READY/PLAYING update the list.
            PLAY_MODE_AUTO -> {
                // 1. Make the first unread volume play state to READY.
                if (activeVolume.timestamp == UNKNOWN_VOLUME) {
                    val first = volumes.find { it.playMode == PLAY_MODE_AUTO }
                    if (first != null) {
                        volumes.forEachIndexed { index, data ->
                            list[index] = data.copy(
                                playState = if (data.timestamp == first.timestamp) {
                                    READY
                                } else {
                                    STANDBY
                                }
                            )
                        }
                    } else {
                        logw("There is no unread volume.")
                    }
                }
                // 2. Find next unread volume.
                else {
                    val next = volumes.find { it.playMode == PLAY_MODE_AUTO }
                    // If the next is null, auto play complete, make all volumes play state to STANDBY.
                    if (next == null) {
                        volumes.forEachIndexed { index, data ->
                            list[index] = data.copy(
                                playState = if (data.timestamp == activeVolume.timestamp) {
                                    activeVolume.playState
                                } else {
                                    STANDBY
                                }
                            )
                        }
                    }
                    // If the active volume play complete, make the next volume play state to STANDBY.
                    else if (activeVolume.playState in setOf(COMPLETED, FAILED)) {
                        volumes.forEachIndexed { index, data ->
                            list[index] = data.copy(
                                playState = if (data.timestamp == next.timestamp) {
                                    READY
                                } else {
                                    STANDBY
                                }
                            )
                        }
                    }
                    // If the active volume in play state, update the lis.
                    else if (activeVolume.playState in setOf(READY, PLAYING, STANDBY)) {
                        volumes.forEachIndexed { index, data ->
                            list[index] = data.copy(
                                playState = if (data.timestamp == activeVolume.timestamp) {
                                    activeVolume.playState
                                } else {
                                    STANDBY
                                }
                            )
                        }
                    }
                    // Set all volumes play state to STANDBY.
                    else {
                        volumes.forEachIndexed { index, data ->
                            list[index] = data.copy(playState = STANDBY)
                        }
                    }
                }
            }
        }
        logd("PlayMode:$playMode,\nActive:$activeVolume,\nLoad data are: $list")
        return list
    }

    private fun listenToVolumes(): Observable<List<VolumeData>> {
        return Observable.just(createVolumes())
    }

    private fun listenToActiveVolume(): Observable<VolumeData> {
        return activeVolumePublisher.startWith(Observable.just(VolumeData()))
    }

    private fun listenToVolumePlayMode(): Observable<PlayMode> {
        return playModePublisher.startWith(Observable.just(PLAY_MODE_STANDBY))
    }

    fun manualPlay(data: VolumeData) {
        logd("User click manual play button. $data")

        playModePublisher.onNext(PLAY_MODE_MANUAL)
        activeVolumePublisher.onNext(data.copy(playState = READY))
    }

    fun autoPlay() {
        logd("User click auto play button.")

        playModePublisher.onNext(PLAY_MODE_AUTO)
        activeVolumePublisher.onNext(VolumeData())
    }

    private var playDisposable: Disposable? = null

    /**
     *  Only play the volume who's play state is [READY].
     *
     *  1: when subscribe publish [PLAYING] state to listener.
     *  2: when play complete publish [COMPLETED] to listener.
     *  3: when play failed, publish [FAILED] to listener.
     */
    private fun play(data: VolumeData) {
        // Add to played list.
        // In your project, this value should save to database to help make the read info persistent.
        readVolumes.add(data.timestamp)

        playDisposable?.dispose()
        playDisposable = Single.just(data)
            // Mock play volume
            .delay(2, TimeUnit.SECONDS)
            // Publish PLAYING state to listener.
            .doOnSubscribe {
                activeVolumePublisher.onNext(data.copy(playState = PLAYING))
            }.subscribe({
                activeVolumePublisher.onNext(data.copy(playState = COMPLETED))
            }, { e ->
                loge("Play volume failed, ${e.message}")
                activeVolumePublisher.onNext(data.copy(playState = FAILED))
            })
    }

    private fun createVolumes(size: Int = 20): List<VolumeData> {
        val list = mutableListOf<VolumeData>()
        for (i in 1..size) {
            list.add(VolumeData(timestamp = System.currentTimeMillis() + i))
        }

        return list
    }
}