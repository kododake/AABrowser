package com.kododake.aabrowser.media

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.SilenceMediaSource
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.kododake.aabrowser.MainActivity
import com.kododake.aabrowser.R

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class WebMediaSessionService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private var mediaSession: MediaSession? = null
    private var currentTitle: String = ""
    private var currentSubtitle: String = ""
    private var webDurationUs: Long = SILENCE_DURATION_US

    private val sessionCallback = object : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon().build()
            val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                .add(Player.COMMAND_PLAY_PAUSE)
                .add(Player.COMMAND_STOP)
                .add(Player.COMMAND_SEEK_TO_NEXT)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setAvailablePlayerCommands(playerCommands)
                .build()
        }
    }

    override fun onCreate() {
        super.onCreate()

        val mediaSourceFactory = object : MediaSource.Factory {
            override fun createMediaSource(mediaItem: MediaItem): MediaSource {
                val durMicroseconds = mediaItem.localConfiguration?.tag as? Long ?: SILENCE_DURATION_US
                val source = SilenceMediaSource.Factory()
                    .setDurationUs(durMicroseconds)
                    .createMediaSource()
                return source
            }
            override fun setDrmSessionManagerProvider(drmSessionManagerProvider: androidx.media3.exoplayer.drm.DrmSessionManagerProvider) = this
            override fun setLoadErrorHandlingPolicy(loadErrorHandlingPolicy: androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy) = this
            override fun getSupportedTypes(): IntArray = intArrayOf(C.CONTENT_TYPE_OTHER)
        }

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(C.AUDIO_CONTENT_TYPE_UNKNOWN)
                    .build(),
                false,
            )
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_OFF
                volume = 0f
            }
        updateMetadata(
            title = getString(R.string.app_name),
            subtitle = getString(R.string.media_notification_content_text),
        )

        val sessionIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            sessionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val forwardingPlayer = object : ForwardingPlayer(player) {
            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .add(Player.COMMAND_PLAY_PAUSE)
                    .add(Player.COMMAND_STOP)
                    .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                    .build()
            }

            override fun isCommandAvailable(command: Int): Boolean {
                return when (command) {
                    Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> true
                    else -> super.isCommandAvailable(command)
                }
            }

            // Trick: Force hasNext/Previous to true so the buttons are active
            override fun hasNextMediaItem(): Boolean = true
            override fun hasPreviousMediaItem(): Boolean = true

            override fun play() {
                broadcastWebCommand(COMMAND_PLAY)
                super.play()
            }

            override fun pause() {
                broadcastWebCommand(COMMAND_PAUSE)
                super.pause()
            }

            override fun stop() {
                broadcastWebCommand(COMMAND_PAUSE)
                super.stop()
            }

            override fun seekToNext() {
                broadcastWebCommand(COMMAND_SKIP_FORWARD)
            }

            override fun seekToPrevious() {
                broadcastWebCommand(COMMAND_SKIP_BACKWARD)
            }

            override fun seekToNextMediaItem() {
                broadcastWebCommand(COMMAND_SKIP_FORWARD)
            }

            override fun seekToPreviousMediaItem() {
                broadcastWebCommand(COMMAND_SKIP_BACKWARD)
            }

            override fun seekForward() {
                val targetPositionMs = (currentPosition + SEEK_INCREMENT_MS).coerceAtLeast(0L)
                broadcastWebCommand(COMMAND_SEEK, targetPositionMs)
            }

            override fun seekBack() {
                val targetPositionMs = (currentPosition - SEEK_INCREMENT_MS).coerceAtLeast(0L)
                broadcastWebCommand(COMMAND_SEEK, targetPositionMs)
            }

            override fun getMediaMetadata(): MediaMetadata {
                return MediaMetadata.Builder()
                    .setTitle(currentTitle.ifBlank { getString(R.string.app_name) })
                    .setArtist(currentSubtitle.ifBlank { getString(R.string.media_notification_content_text) })
                    .setAlbumTitle(currentSubtitle.ifBlank { getString(R.string.media_notification_content_text) })
                    .setIsPlayable(true)
                    .build()
            }

            override fun seekTo(positionMs: Long) {
                broadcastWebCommand(COMMAND_SEEK, positionMs)
                super.seekTo(positionMs)
            }
        }

        mediaSession = MediaSession.Builder(this, forwardingPlayer)
            .setSessionActivity(sessionActivity)
            .setCallback(sessionCallback)
            .build()
        mediaSession?.let { addSession(it) }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SET_ACTIVE -> {
                val active = intent.getBooleanExtra(EXTRA_ACTIVE, false)
                setSessionActive(active)
            }
            ACTION_UPDATE_METADATA -> {
                updateMetadata(
                    title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
                    subtitle = intent.getStringExtra(EXTRA_SUBTITLE).orEmpty(),
                )
            }
            ACTION_UPDATE_STATE -> {
                val positionSec = intent.getDoubleExtra(EXTRA_POSITION, 0.0)
                val durationSec = intent.getDoubleExtra(EXTRA_DURATION, 0.0)
                val isPaused = intent.getBooleanExtra(EXTRA_PAUSED, true)

                val positionMs = (positionSec * 1000).toLong()
                val newDurationUs = if (durationSec > 0) (durationSec * 1000000).toLong() else SILENCE_DURATION_US

                if (Math.abs(webDurationUs - newDurationUs) > 1000000L) { // 1 sec drift allowed
                    webDurationUs = newDurationUs
                    updateMetadata(currentTitle, currentSubtitle)
                }

                if (Math.abs(player.currentPosition - positionMs) > 2000L) { // 2 sec drift allowed
                    player.seekTo(positionMs)
                }
                
                if (!isPaused && !player.isPlaying) {
                    player.playWhenReady = true
                    player.play()
                } else if (isPaused && player.isPlaying) {
                    player.pause()
                }
            }
            ACTION_STOP -> {
                setSessionActive(false)
                stopSelf()
            }
            ACTION_INIT -> {
            }
        }
        return START_STICKY
    }

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        super.onUpdateNotification(session, startInForegroundRequired)
    }

    override fun onDestroy() {
        mediaSession?.let { removeSession(it) }
        mediaSession?.release()
        mediaSession = null
        player.release()
        super.onDestroy()
    }

    private fun setSessionActive(active: Boolean) {
        if (active) {
            player.playWhenReady = true
            player.play()
        } else {
            player.pause()
        }
    }

    private fun updateMetadata(title: String, subtitle: String) {
        currentTitle = title.ifBlank { getString(R.string.app_name) }
        currentSubtitle = subtitle.ifBlank { getString(R.string.media_notification_content_text) }

        val metadata = MediaMetadata.Builder()
            .setTitle(currentTitle)
            .setArtist(currentSubtitle)
            .setAlbumTitle(currentSubtitle)
            .setIsPlayable(true)
            .build()
            
        val currentPosition = player.currentPosition.coerceAtLeast(0L)
        val playWhenReady = player.playWhenReady
        
        player.setPlaylistMetadata(metadata)

        val mediaItem = MediaItem.Builder()
            .setMediaId("webview_silent_session_${System.currentTimeMillis()}")
            .setMediaMetadata(metadata)
            .setTag(webDurationUs)
            .build()

        player.setMediaItem(mediaItem, currentPosition)
        player.prepare()
        player.playWhenReady = playWhenReady
    }

    private fun broadcastWebCommand(commandType: Int, positionMs: Long = 0L) {
        val intent = Intent(ACTION_WEB_MEDIA_COMMAND)
            .setPackage(packageName)
            .putExtra(EXTRA_COMMAND_TYPE, commandType)
            .putExtra(EXTRA_SEEK_POSITION, positionMs)
        sendBroadcast(intent)
    }

    companion object {
        const val ACTION_INIT = "com.kododake.aabrowser.action.MEDIA_INIT"
        const val ACTION_SET_ACTIVE = "com.kododake.aabrowser.action.MEDIA_SET_ACTIVE"
        const val ACTION_UPDATE_METADATA = "com.kododake.aabrowser.action.MEDIA_UPDATE_METADATA"
        const val ACTION_UPDATE_STATE = "com.kododake.aabrowser.action.MEDIA_UPDATE_STATE"
        const val ACTION_STOP = "com.kododake.aabrowser.action.MEDIA_STOP"
        const val ACTION_WEB_MEDIA_COMMAND = "com.kododake.aabrowser.action.WEB_MEDIA_COMMAND"

        const val EXTRA_ACTIVE = "extra_active"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_SUBTITLE = "extra_subtitle"
        const val EXTRA_SHOULD_PLAY = "extra_should_play" // For backwards compat

        const val EXTRA_POSITION = "extra_position"
        const val EXTRA_DURATION = "extra_duration"
        const val EXTRA_PAUSED = "extra_paused"

        const val EXTRA_COMMAND_TYPE = "extra_command_type"
        const val EXTRA_SEEK_POSITION = "extra_seek_position"
        const val COMMAND_PLAY = 1
        const val COMMAND_PAUSE = 2
        const val COMMAND_SKIP_FORWARD = 3
        const val COMMAND_SKIP_BACKWARD = 4
        const val COMMAND_SEEK = 5
        private const val SEEK_INCREMENT_MS = 10_000L

        private const val SILENCE_DURATION_US = 24L * 60L * 60L * 1_000_000L
    }
}
