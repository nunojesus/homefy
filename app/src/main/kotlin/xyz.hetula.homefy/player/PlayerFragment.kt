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
 *
 */

package xyz.hetula.homefy.player

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.support.design.widget.Snackbar
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import kotlinx.android.synthetic.main.fragment_player.view.*
import xyz.hetula.homefy.R
import xyz.hetula.homefy.Utils
import xyz.hetula.homefy.playlist.PlaylistDialog
import xyz.hetula.homefy.service.Homefy
import xyz.hetula.homefy.service.HomefyService
import java.util.*

/**
 * @author Tuomo Heino
 * @version 1.0
 * @since 1.0
 */
class PlayerFragment : Fragment() {
    private val mPlaybackListener = this::onSongUpdate
    private val mHandler = Handler()
    private var mTxtTitle: TextView? = null
    private var mTxtArtist: TextView? = null
    private var mTxtAlbum: TextView? = null
    private var mTxtLength: TextView? = null
    private var mTxtBuffering: TextView? = null
    private var mBtnPausePlay: ImageButton? = null
    private var mBtnFavorite: ImageButton? = null
    private var mSeekBar: SeekBar? = null
    private val mPositionLoop = Handler()
    private val mUpdateRunnable = this::posQuery
    private var mIsShowing = false


    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val main = inflater!!.inflate(R.layout.fragment_player, container, false) as LinearLayout
        mTxtTitle = main.txt_song_title!!
        mTxtArtist = main.txt_song_artist!!
        mTxtAlbum = main.txt_song_album!!
        mTxtLength = main.txt_song_length!!
        mTxtBuffering = main.txt_buffering!!
        mSeekBar = main.seek_song_length!!
        mBtnPausePlay = main.btn_play_pause!!
        mBtnFavorite = main.btn_favorite!!

        mBtnPausePlay!!.setOnClickListener { _ -> Homefy.player().pauseResume() }

        main.btn_stop!!.setOnClickListener { _ -> Homefy.player().stop() }
        main.btn_next!!.setOnClickListener { _ ->
            Homefy.player().next()
            val song = Homefy.player().nowPlaying()
            if (song != null) {
                updateSongInfo(song)
            }
        }
        main.btn_previous.setOnClickListener({ _ ->
            Homefy.player().previous()
            val song = Homefy.player().nowPlaying()
            if (song != null) {
                updateSongInfo(song)
            }
        })
        main.btn_favorite.setOnClickListener {
            val song = Homefy.player().nowPlaying() ?: return@setOnClickListener
            Homefy.playlist().favorites.toggle(song)
            updateFavIco(song)
        }
        main.btn_add_to_playlist.setOnClickListener {
            val song = Homefy.player().nowPlaying() ?: return@setOnClickListener
            PlaylistDialog.addToPlaylist(context, song) {
                Snackbar.make(main, R.string.playlist_dialog_added,
                        Snackbar.LENGTH_SHORT).show()
            }
        }
        main.btn_shutdown.setOnClickListener {
            doShutdown()
        }
        main.seek_song_length.setOnSeekBarChangeListener(SeekListener(this))
        main.btn_playback!!.setOnClickListener(this::onPlaybackModeClick)
        return main
    }

    override fun onResume() {
        super.onResume()
        mIsShowing = true
        val song = Homefy.player().nowPlaying()
        if (song != null) {
            updateSongInfo(song)
        }
        Homefy.player().registerPlaybackListener(mPlaybackListener)
        mPositionLoop.post(mUpdateRunnable)
        // Enable title scrolling...
        mTxtTitle!!.isSelected = true
    }

    override fun onPause() {
        super.onPause()
        mIsShowing = false
        Log.d(TAG, "Paused!")
        if (Homefy.isAlive) {
            Homefy.player().unregisterPlaybackListener(mPlaybackListener)
        }
        mPositionLoop.removeCallbacks(mUpdateRunnable)
        mTxtBuffering!!.visibility = View.INVISIBLE
    }

    private fun onSongUpdate(song: Song?, state: Int, param: Int) {
        when (state) {
            HomefyPlayer.STATE_BUFFERING -> onBuffer(param)
            HomefyPlayer.STATE_PLAY -> onDurUpdate(false)
            HomefyPlayer.STATE_PAUSE -> onDurUpdate(true)
            HomefyPlayer.STATE_STOP -> clear()
            HomefyPlayer.STATE_RESUME -> onDurUpdate(false)
        }
        if (song != null && state != HomefyPlayer.STATE_BUFFERING) {
            updateSongInfo(song)
        }
    }

    private fun onBuffer(buffered: Int) {
        Log.d(TAG, "Buffering $buffered")
        if (buffered >= 100) {
            mTxtBuffering!!.visibility = View.INVISIBLE
            mTxtBuffering!!.text = context.getString(R.string.buffering, 0)
        } else {
            mTxtBuffering!!.visibility = View.VISIBLE
            mTxtBuffering!!.text = context.getString(R.string.buffering, buffered)
        }
    }

    private fun onDurUpdate(paused: Boolean) {
        if (!mIsShowing) return
        if (paused) {
            mPositionLoop.removeCallbacks(mUpdateRunnable)
        } else {
            mPositionLoop.post(mUpdateRunnable)
        }
    }

    private fun clear() {
        mBtnPausePlay!!.setImageResource(R.drawable.ic_play_circle)
        mTxtLength!!.text = Utils.parseTime(0, 0)
    }

    private fun updateSongInfo(now: Song) {
        if (now.track >= 0) {
            mTxtTitle!!.text = String.format(Locale.getDefault(),
                    "%d - %s", now.track, now.title)
        } else {
            mTxtTitle!!.text = now.title
        }
        mTxtTitle!!.isSelected = true

        val position = Homefy.player().queryPosition().toLong()

        mTxtArtist!!.text = now.artist
        mTxtAlbum!!.text = now.album
        mTxtLength!!.text = Utils.parseTime(position, now.length)

        if (Homefy.player().isPaused) {
            mBtnPausePlay!!.setImageResource(R.drawable.ic_play_circle)
        } else if (Homefy.player().isPlaying) {
            mBtnPausePlay!!.setImageResource(R.drawable.ic_pause_circle)
        }
        updateFavIco(now)
    }

    private fun updateFavIco(song: Song) {
        if (Homefy.playlist().isFavorite(song)) {
            mBtnFavorite!!.setImageResource(R.drawable.ic_favorite_large)
        } else {
            mBtnFavorite!!.setImageResource(R.drawable.ic_not_favorite_large)
        }
    }

    private fun posQuery() {
        if (!Homefy.isAlive) return
        val song = Homefy.player().nowPlaying()
        var pos: Long
        val dur: Long
        if (song != null) {
            pos = Homefy.player().queryPosition().toLong()
            dur = song.length
        } else {
            pos = 0
            dur = 0
        }
        if (pos > dur) {
            pos = dur
        }
        mSeekBar!!.max = dur.toInt()
        mSeekBar!!.progress = pos.toInt()
        mTxtLength!!.text = Utils.parseTime(pos, dur)
        if (song != null && mIsShowing) {
            mPositionLoop.postDelayed(mUpdateRunnable, 750)
        }
    }

    private fun onPlaybackModeClick(v: View) {
        val button = v as ImageButton
        val mode = Homefy.player().cyclePlaybackMode()
        val imgRes: Int
        imgRes = when (mode) {
            PlaybackMode.NORMAL -> R.drawable.ic_repeat_off
            PlaybackMode.REPEAT -> R.drawable.ic_repeat
            PlaybackMode.REPEAT_SINGLE -> R.drawable.ic_repeat_one
            PlaybackMode.RANDOM -> R.drawable.ic_shuffle
        }
        button.setImageDrawable(ContextCompat.getDrawable(context, imgRes))
    }

    private fun doShutdown() {
        Log.d(TAG, "Shutting down!")
        activity.finishAndRemoveTask()
        val context = activity.applicationContext
        mHandler.postDelayed({ context.stopService(Intent(context, HomefyService::class.java)) }, 500)
    }

    private class SeekListener(val player: PlayerFragment) : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            if (!fromUser || !Homefy.isAlive) {
                return
            }
            Homefy.player().seekTo(progress)
        }

        override fun onStartTrackingTouch(seekBar: SeekBar?) {
            player.mPositionLoop.removeCallbacks(player.mUpdateRunnable)
        }

        override fun onStopTrackingTouch(seekBar: SeekBar?) {
            player.mPositionLoop.post(player.mUpdateRunnable)
        }
    }

    companion object {
        val TAG = "PlayerFragment"
    }
}
