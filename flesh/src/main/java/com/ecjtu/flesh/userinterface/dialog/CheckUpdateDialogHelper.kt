package com.ecjtu.flesh.userinterface.dialog

import android.Manifest
import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AlertDialog
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.ecjtu.flesh.BuildConfig
import com.ecjtu.flesh.R
import com.ecjtu.flesh.model.models.UpdateBean
import com.ecjtu.netcore.Constants
import com.google.gson.Gson
import okhttp3.*
import java.io.IOException

class CheckUpdateDialogHelper(context: Context) : BaseDialogHelper(context) {

    private lateinit var okHttpClient: OkHttpClient
    private var call: Call? = null
    private var content: View? = null
    override fun onCreateDialog(): AlertDialog? {
        content = LayoutInflater.from(getContext()).inflate(R.layout.layout_progress, null)
        getBuilder()?.setTitle(R.string.check_update)
                ?.setMessage("正在获取更新信息...")
                ?.setView(content)
                ?.setNegativeButton(R.string.cancel, null)
                ?.setPositiveButton(R.string.ok, object : DialogInterface.OnClickListener {
                    override fun onClick(dialog: DialogInterface?, which: Int) {

                    }
                })

        return getBuilder()?.create()
    }

    override fun onDialogShow(dialog: AlertDialog) {
        super.onDialogShow(dialog)
        getDialog()?.getButton(DialogInterface.BUTTON_POSITIVE)?.isEnabled = false
        okHttpClient = OkHttpClient()
        val request = Request.Builder()
                .url(Constants.CHECK_UPDATE_URL)
                .get()
                .build()
        call = okHttpClient.newCall(request)
        call?.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                getHandler().post {
                    val proBar = dialog.findViewById<View>(R.id.progress_bar)
                    proBar?.visibility = View.GONE
                    val parent = getContentLayout()
                    val params = parent?.layoutParams
                    params?.height = 0
                    parent?.layoutParams = params
                    parent?.requestLayout()
                    Toast.makeText(getContext(), "check update failed", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                getHandler().post {
                    val proBar = dialog.findViewById<View>(R.id.progress_bar)
                    proBar?.visibility = View.GONE
                    val parent = getContentLayout()
                    val params = parent?.layoutParams
                    params?.height = 0
                    parent?.layoutParams = params
                    parent?.requestLayout()
                }
                try {
                    val body = response.body()?.string()
                    if (!TextUtils.isEmpty(body)) {
                        val updateBean = Gson().fromJson(body, UpdateBean::class.java)
                        if (updateBean != null) {
                            needUpdate(updateBean.versionCode, updateBean.url)
                        }
                    }
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            }
        })

    }

    override fun onDialogCancel(dialog: AlertDialog) {
        super.onDialogCancel(dialog)
        call?.cancel()
    }

    private fun needUpdate(version: Int, url: String) {
        getHandler().post {
            if (version > BuildConfig.VERSION_CODE || true) {
                getDialog()?.getButton(DialogInterface.BUTTON_POSITIVE)?.isEnabled = true
                download(url)
            }
        }
    }

    private fun getContentLayout(): ViewGroup? {
        return content?.parent?.parent as ViewGroup?
    }

    private fun download(url: String) {
        if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            if (getContext() is Activity) {
                ActivityCompat.requestPermissions(getContext() as Activity, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE), 100)
            }
            Toast.makeText(getContext(), "unable to access write external storage", Toast.LENGTH_SHORT).show()
            return
        }
        try {
//            val packageName = "com.android.providers.downloads";
//            val intent =  Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
//            intent.setData(Uri.parse("package:" + packageName));
//            getContext().startActivity(intent)

            //创建下载任务,downloadUrl就是下载链接
            val request = DownloadManager.Request(Uri.parse(url))
            request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
            //指定下载路径和下载文件名
            val name = url.substring(url.lastIndexOf("/") + 1)
            request.setDestinationInExternalFilesDir(getContext(), Environment.DIRECTORY_DOWNLOADS, name)
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setVisibleInDownloadsUi(true)
            //大于11版本手机允许扫描
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                //表示允许MediaScanner扫描到这个文件，默认不允许。
                request.allowScanningByMediaScanner();
            }

            // 设置一些基本显示信息
            request.setTitle(name)
            request.setDescription("下载完后请点击更新")
            request.setMimeType("application/vnd.android.package-archive")
            //获取下载管理器
            val downloadManager = getContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            //将下载任务加入下载队列，否则不会进行下载
            downloadManager.enqueue(request)
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

}