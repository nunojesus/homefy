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

package xyz.hetula.homefy.service

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.IBinder
import android.support.v4.app.NotificationCompat
import android.support.v4.media.session.MediaButtonReceiver
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import xyz.hetula.homefy.R
import xyz.hetula.homefy.player.HomefyPlayer
import xyz.hetula.homefy.player.Song

/**
 * @author Tuomo Heino
 * @version 1.0
 * @since 1.0
 */
class HomefyService : Service() {
    private val mPlaybackListener = this::onPlay
    private var mSession: MediaSessionCompat? = null

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (mSession != null) {
            MediaButtonReceiver.handleIntent(mSession, intent)
        }
        if (isReady) return Service.START_STICKY
        isReady = true

        Log.d(TAG, "Starting HomefyService")
        Homefy.initialize(applicationContext)

        createNotification()
        Homefy.player().registerPlaybackListener(mPlaybackListener)
        mSession = Homefy.player().mSession

        return Service.START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Destroying Homefy Service")
        isReady = false
        Homefy.player().unregisterPlaybackListener(mPlaybackListener)
        Homefy.destroy()
        stopForeground(true)
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private fun createNotification() {
        startForeground(NOTIFICATION_ID, setupNotification())
    }

    private fun updateNotification() {
        val nM = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nM.notify(NOTIFICATION_ID, setupNotification())
    }

    private fun setupNotification(): Notification {
        val song = Homefy.player().nowPlaying()
        val mediaSession = Homefy.player().mSession
        val largeIcon = BitmapFactory.decodeResource(resources, R.drawable.ic_album_big)
        val builder = NotificationCompat.Builder(applicationContext)
        builder.setLargeIcon(largeIcon)
                .setSmallIcon(R.drawable.ic_music_notification)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setOngoing(true)
                .setShowWhen(false)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(mediaSession!!.controller.sessionActivity)

        if (song != null) {
            builder.addAction(NotificationCompat.Action(
                    R.drawable.ic_pause_notification, "Pause",
                    MediaButtonReceiver.buildMediaButtonPendingIntent(this,
                            PlaybackStateCompat.ACTION_PLAY)))
                    .addAction(NotificationCompat.Action(
                            R.drawable.ic_skip_next_notification, "Next",
                            MediaButtonReceiver.buildMediaButtonPendingIntent(this,
                                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT)))
                    .addAction(NotificationCompat.Action(
                            R.drawable.ic_stop_notification, "Stop",
                            MediaButtonReceiver.buildMediaButtonPendingIntent(this,
                                    PlaybackStateCompat.ACTION_STOP)))
                    .setStyle(android.support.v7.app.NotificationCompat.MediaStyle()
                            .setMediaSession(mediaSession.sessionToken)
                            .setShowActionsInCompactView(0, 1, 2))
                    .setContentTitle(song.title)
                    .setContentText("${song.artist} - ${song.album}")
        } else {
            builder.setContentTitle("Homefy")
                    .setContentText("Nothing is playing")
        }

        return builder.build()
    }

    private fun onPlay(song: Song?, state: Int, param: Int) {
        if (state != HomefyPlayer.STATE_PLAY) return
        updateNotification()
    }

    companion object {
        private val TAG = "HomefyService"
        private val NOTIFICATION_ID = 444

        var isReady = false
            private set
    }
}
