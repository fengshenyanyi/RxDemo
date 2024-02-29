package com.leo.demo.rx

import UNKNOWN_VOLUME
import VolumeData
import android.annotation.SuppressLint
import android.util.Log
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
     *  Play publisher:
     *  first: play mode
     *  second: active volume
     */
    private val playModePublisher = PublishSubject.create<PlayMode>()

    /**
     *  Publisher of mock new volume received.
     */
    private val createVolumesPublisher = PublishSubject.create<List<VolumeData>>()

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
            val list1 = updateReadState(volumes)
            val list2 = updatePlayMode(playMode, activeVolume, list1)
            val list3 = updatePlayState(playMode, activeVolume, list2)
            list3
        }.observeOn(AndroidSchedulers.mainThread()).subscribe({ volumes ->
            // Update UI
            listData.value = volumes

            // Play volume
            play(volumes.find { volume -> volume.playState == READY })

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
            // Play mode is standby, reset play state to standby.
            PLAY_MODE_STANDBY -> {
                volumes.forEachIndexed { index, data ->
                    list[index] = data.copy(playState = STANDBY)
                }
            }

            PLAY_MODE_MANUAL -> {
                volumes.forEachIndexed { index, data ->
                    list[index] = if (data.timestamp == activeVolume.timestamp) {
                        data.copy(playState = activeVolume.playState)
                    } else {
                        data.copy(playState = STANDBY)
                    }
                }
            }

            PLAY_MODE_AUTO -> {
                // Play the first unread volume.
                if (activeVolume.timestamp == UNKNOWN_VOLUME) {
                    val first = volumes.find { it.playMode == PLAY_MODE_AUTO }
                    volumes.forEachIndexed { index, data ->
                        list
                    }
                }
            }
        }
        logd("PlayMode:$playMode,\nActive:$activeVolume,\nLoad data are: $list")
        return list
    }

    private fun listenToActiveVolume(): Observable<VolumeData> {
        return activeVolumePublisher.startWith(Observable.just(VolumeData()))
            .doOnNext { logw("Active volume play state update to ${it.playState}") }
    }

    private fun listenToVolumes(): Observable<List<VolumeData>> {
        // volumes data creator.
        return createInitVolumes().mergeWith(createVolumesPublisher)
            .doOnNext { logw("Load new volumes, size is ${it.size}.") }
    }

    private fun listenToVolumePlayMode(): Observable<PlayMode> {
        return playModePublisher.startWith(Observable.just(PLAY_MODE_STANDBY))
            .doOnNext { logw("Volume play mode update to $it") }
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

    fun addNewVolumes() {
        createVolumesPublisher.onNext(createVolumes(5))
    }

    private var playDisposable: Disposable? = null

    /**
     *  Play the ready volume.
     */
    private fun play(data: VolumeData?) {
        // If no active mode need to play, reset play mode to unknown
        if (data == null) {
            return
        }

        // Add to played list.
        readVolumes.add(data.timestamp)

        playDisposable?.dispose()
        playDisposable = Single.just(data).delay(2, TimeUnit.SECONDS).doOnSubscribe {
            activeVolumePublisher.onNext(data.copy(playState = PLAYING))
        }.subscribe({
            activeVolumePublisher.onNext(data.copy(playState = COMPLETED))
        }, { e ->
            activeVolumePublisher.onNext(data.copy(playState = FAILED))
        })
    }

    private fun createInitVolumes(): Observable<List<VolumeData>> {
        return Observable.just(createVolumes(20))
    }

    private fun createVolumes(size: Int): List<VolumeData> {
        val list = mutableListOf<VolumeData>()
        for (i in 1..size) {
            list.add(VolumeData(timestamp = System.currentTimeMillis() + i))
        }

        return list
    }
}