package com.exact.xtra.service

import android.app.DownloadManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.util.Log
import android.util.LongSparseArray
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.exact.xtra.GlideApp
import com.exact.xtra.R
import com.exact.xtra.db.VideosDao
import com.exact.xtra.model.OfflineVideo
import com.exact.xtra.model.clip.Clip
import com.exact.xtra.util.TwitchApiHelper
import dagger.android.AndroidInjection
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

class ClipDownloadService : Service() {

    private companion object {
        const val TAG = "ClipDownloadService"
        const val CHANNEL_ID = "download_channel"
    }

    @Inject
    lateinit var dao: VideosDao
    private lateinit var downloadManager: DownloadManager
    private val clips = LongSparseArray<Clip>()
    private val notificationBuilders = LongSparseArray<NotificationCompat.Builder>()
    private lateinit var notificationManager: NotificationManagerCompat

    override fun onCreate() {
        super.onCreate()
        AndroidInjection.inject(this)
        downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        notificationManager = NotificationManagerCompat.from(this)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?.let {
                    val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    val query = DownloadManager.Query().setFilterById(id)
                    val cursor = downloadManager.query(query)
                    if (cursor.moveToFirst()) {
                        if (cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL) {
                            Log.d(TAG, "Downloading done. Saving video")
                            val currentDate = TwitchApiHelper.getCurrentTimeFormatted(this@ClipDownloadService)
                            val path = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI))
                            val glide = GlideApp.with(this@ClipDownloadService)
                            GlobalScope.launch {
                                with (clips.get(id)) {
                                    val thumbnail = glide.downloadOnly().load(thumbnails.medium).submit().get().absolutePath
                                    val logo = glide.downloadOnly().load(broadcaster.logo).submit().get().absolutePath
                                    dao.insert(OfflineVideo(path, title, broadcaster.name, game, duration.toLong(), currentDate, createdAt, thumbnail, logo))
                                    clips.remove(id)
                                }
                            }
                            val builder = notificationBuilders.get(id).apply {
                                setAutoCancel(true)
                                setContentTitle(getString(R.string.downloaded))
                                setProgress(0, 0, false)
                                setOngoing(false)
                            }
                            notificationManager.notify(id.toInt(), builder.build())
                        }
                    }
                    cursor.close()
                }
            }
        }
        registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        with (intent!!.extras) {
            Log.d(TAG, "Starting download")
            val clip = getParcelable("clip") as Clip
            val url = getString("url")
            val quality = getString("quality")
            val request = DownloadManager.Request(url!!.toUri()).apply {
                setDestinationInExternalFilesDir(this@ClipDownloadService, ".downloads", File.separator + clip.slug + quality)
                setVisibleInDownloadsUi(false)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN)
            }
            val id = downloadManager.enqueue(request)
            val builder = NotificationCompat.Builder(this@ClipDownloadService, CHANNEL_ID).apply {
                setContentTitle(getString(R.string.downloading))
                setContentText(clip.title)
                setSmallIcon(R.drawable.ic_notification)
                setOngoing(true)
                priority = NotificationCompat.PRIORITY_HIGH
            }
            notificationManager.notify(id.toInt(), builder.build())
            notificationBuilders.put(id, builder)
            clips.put(id, clip)
        }
        return super.onStartCommand(intent, flags, startId)
    }
}