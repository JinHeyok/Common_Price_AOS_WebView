package com.banmal.web

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Message
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.Window
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.messaging.FirebaseMessaging
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.URISyntaxException


class MainActivity : AppCompatActivity() {


    lateinit var mainWebView: WebView
    lateinit var pullToRefresh: SwipeRefreshLayout
    lateinit var permissionArray: Array<String>
    var cameraPath = ""
    var mWebViewImageUpload: ValueCallback<Array<Uri>>? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        pullToRefresh = findViewById(R.id.pullToRefresh)
        mainWebView = findViewById(R.id.main_WebView)

        webView_Setting()


        //취소를 누른 경우 다시 묻지 않음
        if (!NotificationManagerCompat.from(this)
                .areNotificationsEnabled() && shouldShowRequestDialog()
        ) {
            // 알림이 비활성화된 경우, 사용자에게 알림
            AlertDialog.Builder(this)
                .setTitle("알림 권한 필요")
                .setMessage("이 앱의 기능을 완전히 사용하려면 알림 권한이 필요합니다. 설정으로 이동하시겠습니까?")
                .setPositiveButton("설정으로 이동") { dialog, which ->
                    // 알림 설정 화면으로 이동하는 인텐트 생성
                    val intent = Intent().apply {
                        when {
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                                action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                            }

                            else -> {
                                action = "android.settings.APP_NOTIFICATION_SETTINGS"
                                putExtra("app_package", packageName)
                                putExtra("app_uid", applicationInfo.uid)
                            }
                        }
                    }
                    startActivity(intent)
                }
                .setNegativeButton("취소") { dialog, which ->
                    // '취소' 버튼을 누른 경우, 사용자의 선택을 저장합니다.
                    saveUserChoice()
                }
                .show()
        }


        // 버전에 따라 체크할 권한 array 생성
        permissionArray =
            if (Build.VERSION.SDK_INT >= 33) {
                arrayOf(
                    Manifest.permission.READ_MEDIA_AUDIO,
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,

                    )
            } else if (Build.VERSION.SDK_INT >= 31) {
                arrayOf(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
            } else {
                arrayOf(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
            }



        if (Build.VERSION.SDK_INT >= 31) {

            if (permissionArray.all {
                    ContextCompat.checkSelfPermission(
                        this,
                        it
                    ) == PackageManager.PERMISSION_GRANTED
                }) {
                //Toast.makeText(this, "권한이 모두 허용되어 있습니다.", Toast.LENGTH_SHORT).show()
            }
            // 권한 요청
            else {
                ActivityCompat.requestPermissions(
                    this,
                    permissionArray, 1005
                )
            }
        } else if (Build.VERSION.SDK_INT < 31 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            if (permissionArray.all {
                    ContextCompat.checkSelfPermission(
                        this,
                        it
                    ) == PackageManager.PERMISSION_GRANTED
                }) {
                //Toast.makeText(this, "권한이 허용되어 있습니다.", Toast.LENGTH_SHORT).show()
            }
            // 권한 요청
            else {
                ActivityCompat.requestPermissions(
                    this,
                    permissionArray, 1006
                )
            }
        }




        pullToRefresh.setOnRefreshListener {
            //모든 캐시 삭제 - css 적용 느리기 때문에
            //mainWebView.clearHistory();
            mainWebView.clearCache(true);
            mainWebView.clearView();
            mainWebView.loadUrl(mainWebView.url.toString())
            pullToRefresh.isRefreshing = false // 없으면 새로고침 애니메이션 끝나지 않음
        }


    }


    // 사용자가 '취소'를 선택했을 때 호출됩니다.
    fun saveUserChoice() {
        val sharedPref = getSharedPreferences("AppSettingsPrefs", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putBoolean("NotificationPermissionAsked", true)
            apply()
        }
    }

    // 사용자의 선택을 조회합니다.
    fun shouldShowRequestDialog(): Boolean {
        val sharedPref = getSharedPreferences("AppSettingsPrefs", Context.MODE_PRIVATE)
        return !sharedPref.getBoolean("NotificationPermissionAsked", false)
    }


    /**
     * 권한 승인 처리 방법 - requestPermissions를 호출하면 권한 승인 결과를 콜백되는 메서드
     * 사용자가 두번이상 거부한 경우는 묻지않고 PERMISSION_DEFINE 리던
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        // 권한 요청 결과 처리
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            // 모든 권한이 승인된 경우
            Log.e("승인", "모든 권한 승인됨")
        } else {
            // 하나라도 거부된 권한이 있는 경우
            Log.e("거부", "권한 거부됨")
            AlertDialog.Builder(this).apply {
                setMessage("2번 이상 권한승인을 거절한 경우, 직접 '설정'에서 권한을 승인해야 합니다.")
                setCancelable(false)
                setPositiveButton("확인") { dialog, which ->
                    // 어플의 권한 설정 페이지로 이동
                    val intent =
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:$packageName"))
                    startActivity(intent)
                }
                show()
            }
        }
    }


    fun webView_Setting() {

        mainWebView.settings.apply {
            javaScriptEnabled = true // 웹 페이지 내 자바스크립트 실행 허용
            loadWithOverviewMode = true // 메타 태그를 사용한 화면 맞춤
            useWideViewPort = true // 화면에 맞게 WebView가 로드될 콘텐츠 조정
            setSupportZoom(false) // 화면 줌 사용 설정
            layoutAlgorithm = WebSettings.LayoutAlgorithm.SINGLE_COLUMN // 모바일 화면에 맞게 컨텐츠 크기 조정
            cacheMode = WebSettings.LOAD_NO_CACHE // 캐시 사용 방법 설정
            domStorageEnabled = true // DOM 스토리지 API 사용 설정
            setSupportMultipleWindows(true) // 새 창 열기 허용
            javaScriptCanOpenWindowsAutomatically = true // 자바스크립트가 자동으로 창을 열 수 있게 함
            textZoom = 100 // 시스템 폰트 크기 변경에 영향 받지 않음
            userAgentString += " banmal_android" // 커스텀 User-Agent 문자열 추가
        }
        mainWebView.addJavascriptInterface(WebBrideg(this@MainActivity), "banmal")


        //url 이 로드 되려고 할때 제어할 기회
        //tel 클릭시
        //포트원 결제시 intent 처리
        mainWebView.setWebViewClient(object : WebViewClient() {

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                // HTTP 에러 코드를 받았을 때 호출됨
                val statusCode = errorResponse!!.statusCode
                println(statusCode);
                if (statusCode == 503 || statusCode == 502) {
                    // 503,502 Not Found일 경우 앱 종료
                    Toast.makeText(this@MainActivity, "현재 운영중인 상태가 아닙니다.😉", Toast.LENGTH_SHORT).show()
                    finish();
                }
            }

            // SSL 에러 수신 시 처리 방법
            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                handler?.proceed()
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {


                if (request.url.toString().startsWith("tel:")) {
                    val tel = Intent(Intent.ACTION_DIAL, Uri.parse(request.url.toString()));
                    startActivity(tel);
                    return true;
                } else if (request.url.scheme == "intent") {
                    //카카오톡 공유하기 -> 모바일 화면에서는 이쪽으로 옮 ( 그래서 있으면 카카오톡 실행시키고 없으면 카카오톡 플레이스토어로 설치화면으로 보냄
                    //카카오톡 공유하기 -> 태블릿화면에서는 onCreateWindow로 감
                    //https://developers.kakao.com/docs/latest/ko/javascript/hybrid

                    try {
                        // Intent 생성
                        val intent =
                            Intent.parseUri(request.url.toString(), Intent.URI_INTENT_SCHEME)

                        // 실행 가능한 앱이 있으면 앱 실행
                        if (intent.resolveActivity(packageManager) != null) {
                            startActivity(intent)
                            Log.d("debug", "ACTIVITY: ${intent.`package`}")
                            return true
                        } else {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("market://details?id=com.kakao.talk")
                            )
                            startActivity(intent)
                            return true
                        }
                        Log.e("debug", "Could not parse anythings")
                    } catch (e: URISyntaxException) {
                        Log.e("debug", "Invalid intent request", e)
                    }
                } else {
                    //포트원 Intent 결제 
                    request?.url?.let {
                        if (it.scheme == "about") {
                            return true // 이동하지 않음
                        }
                        val urlStr = it.toString()
                        if (!URLUtil.isNetworkUrl(urlStr) && !URLUtil.isJavaScriptUrl(urlStr)) {
                            openPaymentApp(urlStr) // 앱이동
                            return true
                        }
                    }
                    return super.shouldOverrideUrlLoading(view, request)
                }

                return false
            }
        })



        mainWebView.webChromeClient = object : WebChromeClient() {

            //자바 스크립트에서 window.open 했을때 행동
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {

                // set dialog webview
                val dialog = Dialog(this@MainActivity)
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
                dialog.setContentView(R.layout.activity_main)

                val newWebView = dialog.findViewById<WebView>(R.id.main_WebView)

                newWebView.settings.javaScriptEnabled = true
                newWebView.settings.textZoom = 100 //시스템 폰트에 따라 변경되지 않게 설정
                //어플에서 어플접속을 확인하기 위한 AGENTSTRING 수정
                val userAgent: String = newWebView.settings.userAgentString;
                newWebView.settings.userAgentString = userAgent + " banmal"
                newWebView.addJavascriptInterface(WebBrideg(this@MainActivity), "banmal")
                newWebView.loadUrl(view?.url.toString())

                val userAgentString = newWebView!!.settings.getUserAgentString()
                val newUserAgentString = userAgentString.replace("; wv", "")
                    .replace("Android ${Build.VERSION.RELEASE};", "")
                Log.e("debug", newUserAgentString)
                newWebView.settings.userAgentString = newUserAgentString


                // open dialog full screen
                val window = dialog.window
                val wlp: WindowManager.LayoutParams = window!!.attributes
                wlp.gravity = Gravity.CENTER
                wlp.flags = WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                window?.attributes = wlp
                dialog.window?.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT
                )
                dialog.show()
                (resultMsg?.obj as WebView.WebViewTransport).setWebView(newWebView)
                resultMsg.sendToTarget()


                newWebView.webChromeClient = object : WebChromeClient() {
                    override fun onCloseWindow(window: WebView) {

                        dialog.dismiss()
                        window.destroy()
                        newWebView.destroy()
                        //webview.removeView(newWebView)
                    }
                }

                return true

            }


            //파일 업로드 1
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: WebChromeClient.FileChooserParams?
            ): Boolean {

                if (mWebViewImageUpload != null) {
                    mWebViewImageUpload = null
                }

                mWebViewImageUpload = filePathCallback

                Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    startActivityForResult(Intent.createChooser(this, ""), 1)
                }

                return true
            }

        }


        val web_url = resources.getString(R.string.app_url)
        mainWebView.loadUrl(web_url)

    }


    //포트원 결제(2) end
    fun openPaymentApp(url: String) {
        Intent.parseUri(url, Intent.URI_INTENT_SCHEME)?.let { intent: Intent ->
            runCatching {
                startActivity(intent) // 앱 이동
            }.recoverCatching {
                // 앱이동에 실패(미설치)시 앱스토어로 이동
                val packageName = intent.getPackage()
                if (!packageName.isNullOrBlank()) {
                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("market://details?id=$packageName")
                        )
                    )
                }
            }
        }
    }


    //파일 업로드 2
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == RESULT_OK) {
            if (data == null) { // 바로 사진을 찍어서 올리는 경우
                val results = arrayOf(Uri.parse(cameraPath))
                mWebViewImageUpload?.onReceiveValue(results)
            } else { // 사진 앱을 통해 사진을 가져온 경우
                val results = arrayOf(data.data!!)
                mWebViewImageUpload?.onReceiveValue(results)
            }
        } else { // 취소 한 경우 초기화
            mWebViewImageUpload?.onReceiveValue(null)
            mWebViewImageUpload = null
        }
    }


    class WebBrideg(private val mContext: Context) {

        @JavascriptInterface
        fun fcmTokenSend(email: String) {

            FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w("fail", "Fetching FCM registration token failed", task.exception)
                    return@OnCompleteListener
                }

                // Get new FCM registration token
                val token = task.result

                println("email : $email");
                println("fcmToken : $token");


                val fcmToken = FcmToken(
                    email,
                    token
                )

                val retrofit =
                    Retrofit.Builder().baseUrl(mContext.resources.getString(R.string.server_url))
                        .addConverterFactory(GsonConverterFactory.create()).build()


                val service = retrofit.create(RetrofitApi::class.java)
                service.token(fcmToken)?.enqueue(object :
                    Callback<com.banmal.web.Message> {
                    override fun onResponse(
                        call: Call<com.banmal.web.Message>,
                        response: Response<com.banmal.web.Message>
                    ) {


                        if (response.isSuccessful) {
                            var result: String? = response.message()
                            Log.e("debug", result.toString())

                        } else {
                            // 통신이 실패한 경우(응답코드 3xx, 4xx 등)
                            Toast.makeText(mContext, "등록에 실패하였습니다", Toast.LENGTH_SHORT).show()
                            Log.e("debug", "실패")
                        }
                    }

                    override fun onFailure(call: Call<com.banmal.web.Message>, t: Throwable) {
                        // 통신 실패 (인터넷 끊킴, 예외 발생 등 시스템적인 이유)
                        Toast.makeText(mContext, "시스템에 오류가 발생하였습니다", Toast.LENGTH_SHORT).show()
                        Log.d("debug", "에러: " + t.message.toString());
                    }
                })
            })
        }


        @JavascriptInterface
        fun downloadImg(url: String) {
            val fileName = url.substringAfterLast('/')

            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle(fileName)
                .setDescription("Downloading")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)

            val downloadManager =
                mContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadID = downloadManager.enqueue(request)

            // 다운로드 완료 알림 리시버 등록
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mContext.registerReceiver(object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                        if (id == downloadID) {
                            Toast.makeText(context, "$fileName 다운로드 완료", Toast.LENGTH_LONG).show()
                        }
                    }
                }, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), RECEIVER_EXPORTED)
            }
        }


    }


    private var backBtnTime: Long = 0
    override fun onBackPressed() {
        val curTime = System.currentTimeMillis()
        val gapTime = curTime - backBtnTime
        if (mainWebView.canGoBack()) {
            mainWebView.goBack()
        } else if (0 <= gapTime && 2000 >= gapTime) {
            super.onBackPressed()
        } else {
            backBtnTime = curTime
            Toast.makeText(this, "한번 더 누르면 종료됩니다.", Toast.LENGTH_SHORT).show()
        }
    }


}


