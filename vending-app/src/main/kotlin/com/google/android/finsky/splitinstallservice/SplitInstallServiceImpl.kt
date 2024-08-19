/**
 * SPDX-FileCopyrightText: 2024 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */
package com.google.android.finsky.splitinstallservice

import android.accounts.AccountManager
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.RemoteException
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.android.vending.R
import com.google.android.phonesky.header.PhoneskyHeaderValue.GoogleApiRequest
import com.google.android.phonesky.header.PhoneskyValue
import com.google.android.phonesky.header.RequestLanguagePkg
import com.google.android.play.core.splitinstall.protocol.ISplitInstallService
import com.google.android.play.core.splitinstall.protocol.ISplitInstallServiceCallback
import org.brotli.dec.BrotliInputStream
import org.microg.vending.billing.DEFAULT_ACCOUNT_TYPE
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue

class SplitInstallServiceImpl(private val context: Context) : ISplitInstallService.Stub(){

    override fun startInstall(
        pkg: String,
        splits: List<Bundle>,
        bundle0: Bundle,
        callback: ISplitInstallServiceCallback
    ) {
        Log.i(TAG, "Start install for package: $pkg")
        trySplitInstall(pkg, splits, false)
        taskQueue.put(Runnable {
            try{
                callback.j(1, Bundle())
            }catch (ignored: RemoteException){
            }
        })
        taskQueue.take().run()
    }

    override fun completeInstalls(
        pkg: String,
        sessionId: Int,
        bundle0: Bundle,
        callback: ISplitInstallServiceCallback
    ) {
        Log.i(TAG, "Complete installs not implemented")
    }

    override fun cancelInstall(
        pkg: String,
        sessionId: Int,
        callback: ISplitInstallServiceCallback
    ) {
        Log.i(TAG, "Cancel install not implemented")
    }

    override fun getSessionState(
        pkg: String,
        sessionId: Int,
        callback: ISplitInstallServiceCallback
    ) {
        Log.i(TAG, "getSessionState not implemented")
    }

    override fun getSessionStates(pkg: String, callback: ISplitInstallServiceCallback) {
        Log.i(TAG, "getSessionStates for package: $pkg")
        callback.i(ArrayList<Any?>(1))
    }

    override fun splitRemoval(
        pkg: String,
        splits: List<Bundle>,
        callback: ISplitInstallServiceCallback
    ) {
        Log.i(TAG, "Split removal not implemented")
    }

    override fun splitDeferred(
        pkg: String,
        splits: List<Bundle>,
        bundle0: Bundle,
        callback: ISplitInstallServiceCallback
    ) {
        Log.i(TAG, "Split deferred not implemented")
        callback.d(Bundle())
    }

    override fun getSessionState2(
        pkg: String,
        sessionId: Int,
        callback: ISplitInstallServiceCallback
    ) {
        Log.i(TAG, "getSessionState2 not implemented")
    }

    override fun getSessionStates2(pkg: String, callback: ISplitInstallServiceCallback) {
        Log.i(TAG, "getSessionStates2 not implemented")
    }

    override fun getSplitsAppUpdate(pkg: String, callback: ISplitInstallServiceCallback) {
        Log.i(TAG, "Get splits for app update not implemented")
    }

    override fun completeInstallAppUpdate(pkg: String, callback: ISplitInstallServiceCallback) {
        Log.i(TAG, "Complete install for app update not implemented")
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    override fun languageSplitInstall(
        pkg: String,
        splits: List<Bundle>,
        bundle0: Bundle,
        callback: ISplitInstallServiceCallback
    ) {
        Log.i(TAG, "Language split installation requested for $pkg")
        trySplitInstall(pkg, splits, true)
        taskQueue.take().run()
    }

    override fun languageSplitUninstall(
        pkg: String,
        splits: List<Bundle>,
        callback: ISplitInstallServiceCallback
    ) {
        Log.i(TAG, "Language split uninstallation requested but app not found, package: %s$pkg")
    }

    @SuppressLint("StringFormatMatches")
    private fun trySplitInstall(pkg: String, splits: List<Bundle>, isLanguageSplit: Boolean) {
        Log.d(TAG, "trySplitInstall: $splits")
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    "splitInstall",
                    "Split Install",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
        val builder = NotificationCompat.Builder(context, "splitInstall")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(context.getString(R.string.split_install, context.getString(R.string.app_name)))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
        notificationManager.notify(NOTIFY_ID, builder.build())
        if (isLanguageSplit) {
            requestSplitsPackage(
                pkg, splits.map { bundle: Bundle -> bundle.getString("language") }.toTypedArray(),
                arrayOfNulls(0)
            )
        } else {
            requestSplitsPackage(
                pkg,
                arrayOfNulls(0),splits.map { bundle: Bundle -> bundle.getString("module_name") }.toTypedArray())
        }
    }

    private fun requestSplitsPackage(
        packageName: String,
        langName: Array<String?>,
        splitName: Array<String?>
    ): Boolean {
        Log.d(TAG,"requestSplitsPackage packageName: " + packageName + " langName: " + langName.contentToString() + " splitName: " + splitName.contentToString())
        if(langName.isEmpty() && splitName.isEmpty()){
            return false
        }

        val packageManager = context.packageManager
        var versionCode: Long = 0
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode // For API level 28 and above
            } else {
                packageInfo.versionCode.toLong() // For API level 27 and below
            }
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e("SplitInstallServiceImpl", "Error getting package info", e)
        }
        val downloadUrls = getDownloadUrls(packageName, langName, splitName, versionCode)
        Log.d(TAG, "requestSplitsPackage download url size : " + downloadUrls.size)
        if (downloadUrls.isEmpty()){
            Log.w(TAG, "requestSplitsPackage download url is empty")
            return false
        }
        try {
            val downloadTempFile = File(context.filesDir, "phonesky-download-service/temp")
            if (!downloadTempFile.parentFile.exists()) {
                downloadTempFile.parentFile.mkdirs()
            }
            val language:String? = if (langName.isNotEmpty()) {
                langName[0]
            } else {
                null
            }
            for (downloadUrl in downloadUrls) {
                taskQueue.put(Runnable {
                    installSplitPackage(downloadUrl, downloadTempFile, packageName, language)
                })
            }

            return true
        } catch (e: Exception) {
            Log.e("SplitInstallServiceImpl", "Error downloading split", e)
            return false
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun downloadSplitPackage(downloadUrl: String, downloadTempFile: File) : Boolean{
        Log.d(TAG, "downloadSplitPackage downloadUrl:$downloadUrl")
        val url = URL(downloadUrl)
        val connection = url.openConnection() as HttpURLConnection
        connection.readTimeout = 30000
        connection.connectTimeout = 30000
        connection.requestMethod = "GET"
        if (connection.responseCode == HttpURLConnection.HTTP_OK) {
            val tempFile: OutputStream =
                BufferedOutputStream(Files.newOutputStream(downloadTempFile.toPath()))
            val inputStream: InputStream = BufferedInputStream(connection.inputStream)
            val buffer = ByteArray(4096)
            var bytesRead: Int

            while ((inputStream.read(buffer).also { bytesRead = it }) != -1) {
                Log.d(TAG, "downloadSplitPackage: $bytesRead")
                tempFile.write(buffer, 0, bytesRead)
            }
            inputStream.close()
            tempFile.close()
        }
        Log.d(TAG, "downloadSplitPackage code: " + connection.responseCode)
        return connection.responseCode == HttpURLConnection.HTTP_OK
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun installSplitPackage(
        downloadUrl: String,
        downloadTempFile: File,
        packageName: String,
        language: String?
    ) {
        try {
            Log.d(TAG, "installSplitPackage downloadUrl:$downloadUrl")
            if (downloadSplitPackage(downloadUrl, downloadTempFile)) {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(NOTIFY_ID)
                val packageInstaller: PackageInstaller
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    packageInstaller = context.packageManager.packageInstaller
                    val params = PackageInstaller.SessionParams(
                        PackageInstaller.SessionParams.MODE_INHERIT_EXISTING
                    )
                    params.setAppPackageName(packageName)
                    params.setAppLabel(packageName + "Subcontracting")
                    params.setInstallLocation(PackageInfo.INSTALL_LOCATION_INTERNAL_ONLY)
                    try {
                        @SuppressLint("PrivateApi") val method =
                            PackageInstaller.SessionParams::class.java.getDeclaredMethod(
                                "setDontKillApp",
                                Boolean::class.javaPrimitiveType
                            )
                        method.invoke(params, true)
                    } catch (e: Exception) {
                        Log.w(TAG, "Error setting dontKillApp", e)
                    }

                    val sessionId: Int
                    try {
                        sessionId = packageInstaller.createSession(params)
                        val session = packageInstaller.openSession(sessionId)
                        try {
                            val buffer = ByteArray(4096)
                            var bytesRead: Int
                            BrotliInputStream(FileInputStream(downloadTempFile)).use { `in` ->
                                session.openWrite("MGSplitPackage", 0, -1).use { out ->
                                    while ((`in`.read(buffer).also { bytesRead = it }) != -1) {
                                        out.write(buffer, 0, bytesRead)
                                    }
                                    session.fsync(out)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error installing split", e)
                        }

                        val intent = Intent(context, InstallResultReceiver::class.java)
                        intent.putExtra("pkg", packageName)
                        intent.putExtra("language", language)
                        val pendingIntent = PendingIntent.getBroadcast(context,sessionId, intent, 0)
                        session.commit(pendingIntent.intentSender)
                        Log.d(TAG, "installSplitPackage commit")
                    } catch (e: IOException) {
                        Log.w(TAG, "Error installing split", e)
                    }
                }
            } else {
                taskQueue.clear();
                Log.w(TAG, "installSplitPackage download failed")
            }
        } catch (e: Exception) {
            Log.w(TAG, "downloadSplitPackage: ", e)
        }
    }

    private fun getDownloadUrls(
        packageName: String,
        langName: Array<String?>,
        splitName: Array<String?>,
        versionCode: Long
    ): ArrayList<String> {
        Log.d(TAG, "getDownloadUrls: ")
        val downloadUrls = ArrayList<String>()
        try {
            val requestUrl = StringBuilder(
                "https://play-fe.googleapis.com/fdfe/delivery?doc=" + packageName + "&ot=1&vc=" + versionCode + "&bvc=" + versionCode +
                        "&pf=1&pf=2&pf=3&pf=4&pf=5&pf=7&pf=8&pf=9&pf=10&da=4&bda=4&bf=4&fdcf=1&fdcf=2&ch="
            )
            for (language in langName) {
                requestUrl.append("&mn=config.").append(language)
            }
            for (split in splitName) {
                requestUrl.append("&mn=").append(split)
            }
            val accounts = AccountManager.get(this.context).getAccountsByType(DEFAULT_ACCOUNT_TYPE)
            if (accounts.isEmpty()) {
                Log.w(TAG, "getDownloadUrls account is null")
                return downloadUrls
            }
            val googleApiRequest =
                GoogleApiRequest(
                    requestUrl.toString(), "GET", accounts[0], context,
                    PhoneskyValue.Builder().languages(
                        RequestLanguagePkg.Builder().language(langName.filterNotNull()).build()
                    ).build()
                )
            val response = googleApiRequest.sendRequest(null)
            val pkgs = response?.fdfeApiResponseValue?.splitReqResult?.pkgList?.pkgDownlaodInfo
            if (pkgs != null) {
                for (item in pkgs) {
                    for (lang in langName) {
                        if (("config.$lang") == item.splitPkgName) {
                            downloadUrls.add(item.slaveDownloadInfo!!.url!!)
                        }
                    }
                    Log.d(TAG, "requestSplitsPackage: $splitName")
                    for (split in splitName) {
                        if (split != null && split == item.splitPkgName) {
                            downloadUrls.add(item.slaveDownloadInfo!!.url!!)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error getting download url", e)
        }
        return downloadUrls
    }

    class InstallResultReceiver : BroadcastReceiver() {
        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        override fun onReceive(context: Context, intent: Intent) {
            val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
            Log.d(TAG, "onReceive status: $status")
            try {
                when (status) {
                    PackageInstaller.STATUS_SUCCESS -> {
                        if (taskQueue.isNotEmpty()) {
                            taskQueue.take().run()
                        }
                        if(taskQueue.size <= 1) NotificationManagerCompat.from(context).cancel(1)
                        sendCompleteBroad(context, intent.getStringExtra("pkg")?:"", intent.getStringExtra("language"))
                    }

                    PackageInstaller.STATUS_FAILURE -> {
                        taskQueue.clear();
                        val errorMsg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                        Log.d("InstallResultReceiver", errorMsg ?: "")
                    }

                    PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                        val intent0 =
                            intent.extras!![Intent.EXTRA_INTENT] as Intent?
                        intent0!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        ContextCompat.startActivity(context, intent0, null)
                    }

                    else -> {
                        taskQueue.clear()
                        NotificationManagerCompat.from(context).cancel(1)
                        Log.w(TAG, "onReceive: install fail")
                    }
                }
            } catch (e: Exception) {
                taskQueue.clear()
                NotificationManagerCompat.from(context).cancel(1)
                Log.w(TAG, "Error handling install result", e)
            }
        }

        private fun sendCompleteBroad(context: Context, pkg: String, split: String?) {
            Log.d(TAG, "sendCompleteBroad: $pkg")
            val extra = Bundle()
            extra.putInt("status", 5)
            extra.putLong("total_bytes_to_download", 99999)
            extra.putString("languages", split)
            extra.putInt("error_code", 0)
            extra.putInt("session_id", 0)
            extra.putLong("bytes_downloaded", 99999)
            val intent = Intent("com.google.android.play.core.splitinstall.receiver.SplitInstallUpdateIntentService")
            intent.setPackage(pkg)
            intent.putExtra("session_state", extra)
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            intent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            context.sendBroadcast(intent)
        }
    }

    companion object {
        private val taskQueue: BlockingQueue<Runnable> = LinkedBlockingQueue()
        val TAG: String = SplitInstallServiceImpl::class.java.simpleName
        const val NOTIFY_ID = 111
    }
}

