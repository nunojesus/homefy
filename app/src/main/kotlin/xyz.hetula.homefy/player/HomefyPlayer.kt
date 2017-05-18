/*
 * MIT License
 *
 * Copyright (c) 2017 Tuomo Heino
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package xyz.hetula.homefy.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
import android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK
import android.media.MediaPlayer
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.PowerManager
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import xyz.hetula.homefy.service.Homefy
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * @author Tuomo Heino
 * @version 1.0
 * @since 1.0
 */
class HomefyPlayer(private var mContext: Context?) {

    private var myNoisyAudioStreamReceiver: BecomingNoisyReceiver? = BecomingNoisyReceiver()
    private val mPlaybackListeners = HashSet<(Song, Int) -> Unit>()
    private val mHandler = Handler()
    private val mRandom = Random()

    private val mPlaylist: MutableList<Song>
    private var mPlayer: MediaPlayer? = null
    var session: MediaSessionCompat? = null
        private set
    private val mController: MediaControllerCompat
    private val mStateBuilder: PlaybackStateCompat.Builder
    private val afChangeListener = AudioManager.OnAudioFocusChangeListener { this.onAudioFocusChange(it) }
    private val mWifiLock: WifiManager.WifiLock

    private var mNowPlaying: Song? = null

    init {
        mPlaylist = ArrayList<Song>()
        val intentFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        mContext!!.registerReceiver(myNoisyAudioStreamReceiver, intentFilter)

        session = MediaSessionCompat(mContext, "Homefy Player")

        session!!.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)

        mStateBuilder = PlaybackStateCompat.Builder().setActions(
                PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PLAY_PAUSE)
        session!!.setPlaybackState(mStateBuilder.build())
        session!!.isActive = true

        mController = session!!.controller

        mPlayer = MediaPlayer()
        mPlayer!!.setAudioStreamType(AudioManager.STREAM_MUSIC)
        mPlayer!!.setAudioAttributes(AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build())
        mPlayer!!.setOnCompletionListener(this::onPlayComplete)
        mPlayer!!.setOnPreparedListener(this::onPrepareComplete)
        mPlayer!!.setOnErrorListener(this::onError)
        mPlayer!!.setOnBufferingUpdateListener(this::onBuffering)
        mPlayer!!.setWakeMode(mContext!!.applicationContext, PowerManager.PARTIAL_WAKE_LOCK)

        mWifiLock = (mContext!!
                .applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
                .createWifiLock(WifiManager.WIFI_MODE_FULL, "HomefyPlayerWifiLock")

        val callback = object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                val am = mContext!!.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                pauseResume()
            }

            override fun onPause() {
                val am = mContext!!.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                pauseResume()
            }

            override fun onSkipToNext() {
                next()
            }

            override fun onSkipToPrevious() {
                previous()
            }

            override fun onStop() {
                val am = mContext!!.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                stop()
            }
        }
    }

    fun release() {
        mPlaybackListeners.clear()

        session!!.release()
        session = null

        mPlayer!!.release()
        mPlayer = null

        if (mWifiLock.isHeld) {
            mWifiLock.release()
        }

        // Just in case as Android does not behave nicely when unregistering non-registered
        // receivers. Should never happen, but it is better to catch it than crash whole app.
        try {
            mContext!!.unregisterReceiver(myNoisyAudioStreamReceiver)
        } catch (ex: IllegalStateException) {
            Log.w(TAG, "Releasing unregistered Noisy Receiver!", ex)
        }

        myNoisyAudioStreamReceiver = null
        mContext = null
    }

    fun nowPlaying(): Song? {
        return mNowPlaying
    }

    fun play(song: Song, playlist: List<Song>?) {
        if (setupPlay(song) && playlist != null) {
            mPlaylist.clear()
            mPlaylist.addAll(playlist)
        }
    }

    fun pauseResume() {
        if (mNowPlaying == null) return
        if (mPlayer!!.isPlaying) {
            mPlayer!!.pause()
            for (listener in mPlaybackListeners) {
                listener(nowPlaying()!!, STATE_PAUSE)
            }
        } else {
            mPlayer!!.start()
            for (listener in mPlaybackListeners) {
                listener(nowPlaying()!!, STATE_RESUME)
            }
        }
    }

    val isPlaying: Boolean
        get() = mNowPlaying != null && mPlayer!!.isPlaying

    val isPaused: Boolean
        get() = mNowPlaying != null && !mPlayer!!.isPlaying

    fun stop() {
        abandonAudioFocus()
        for (listener in mPlaybackListeners) {
            listener(nowPlaying()!!, STATE_STOP)
        }
        mPlayer!!.stop()
    }

    fun previous() {}

    fun next() {
        if (mPlaylist.isEmpty()) return
        // Normal Playback mode implementation
        // This needs own classes/methods etc for proper impl
        // TODO Implement Playback modes
//        var id = mPlaylist.indexOf(mNowPlaying)
//        if (id == -1) return
//        id++
//        if (id >= mPlaylist.size) return
        val id = mRandom.nextInt(mPlaylist.size)
        setupPlay(mPlaylist[id])
    }

    private fun setupPlay(song: Song): Boolean {
        val am = mContext!!.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        // Request audio focus for playback
        val result = am.requestAudioFocus(afChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN)

        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Log.w(TAG, "No AudioFocus Granted!")
            return false
        }
        try {
            mWifiLock.acquire()
            val uri = Uri.parse(Homefy.library().getPlayPath(song))
            mPlayer!!.reset()
            val headers = HashMap<String, String>()
            Homefy.protocol().addAuthHeader(headers)
            mPlayer!!.setDataSource(mContext!!, uri, headers)
            mPlayer!!.prepareAsync()

            mNowPlaying = song
            return true
        } catch (e: IOException) {
            Log.e(TAG, "Error when playing", e)
            return false
        }

    }

    private fun onPrepareComplete(mp: MediaPlayer) {
        mp.start()
        for (listener in mPlaybackListeners) {
            listener(nowPlaying()!!, STATE_PLAY)
        }
    }

    private fun onPlayComplete(mp: MediaPlayer) {
        abandonAudioFocus()
        next()
    }

    private fun onError(mp: MediaPlayer, what: Int, extra: Int): Boolean {
        Log.e(TAG, "Playback Error: $what extra: $extra")
        abandonAudioFocus()
        for (listener in mPlaybackListeners) {
            listener(nowPlaying()!!, STATE_STOP)
        }
        return true
    }

    private fun onBuffering(mediaPlayer: MediaPlayer, i: Int) {
        if (i < 100) return
        Log.d(TAG, "Fully buffered!")
        if (mWifiLock.isHeld) {
            mWifiLock.release()
        }
    }

    private fun abandonAudioFocus() {
        val am = mContext!!.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.abandonAudioFocus(afChangeListener)
        if (mWifiLock.isHeld) {
            mWifiLock.release()
        }
    }

    private fun onAudioFocusChange(focusChange: Int) {
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
            mController.transportControls.pause()
            mHandler.postDelayed({ mController.transportControls.stop() },
                    TimeUnit.SECONDS.toMillis(30))
        } else if (focusChange == AUDIOFOCUS_LOSS_TRANSIENT) {
            mController.transportControls.pause()
        } else if (focusChange == AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
            mPlayer!!.setVolume(0.1f, 0.1f)
        } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
            mPlayer!!.setVolume(1f, 1f)
            // TODO deside if continuing playback is wanted here.
            // ducking wont pause, so receiving messages doesn't cause problems
            // Phone calls etc can cause problems if headset is removed during call
            // after call end playback can possibly continue from wrong speaker.
        }
    }

    fun unregisterPlaybackListener(mPlaybackListener: (Song, Int) -> Unit) {
        mPlaybackListeners.remove(mPlaybackListener)
    }

    fun registerPlaybackListener(mPlaybackListener: (Song, Int) -> Unit) {
        mPlaybackListeners.add(mPlaybackListener)
    }

    private inner class BecomingNoisyReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY == intent.action) {
                if (mNowPlaying == null) return
                mPlayer!!.pause()
            }
        }
    }

    companion object {
        private val TAG = "HomefyPlayer"

        /*
         * Playback Codes
         */
        val STATE_PLAY = 0
        val STATE_PAUSE = 1
        val STATE_RESUME = 2
        val STATE_STOP = 3
    }
}
