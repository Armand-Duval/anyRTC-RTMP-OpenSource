package io.anyrtc.liveplayer

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.load
import io.anyrtc.live.ArLiveDef
import io.anyrtc.live.ArLiveDef.ArLiveVideoResolution
import io.anyrtc.live.ArLiveEngine
import io.anyrtc.live.ArLivePusherObserver
import io.anyrtc.liveplayer.databinding.ActivityPushBinding
import org.loka.screensharekit.EncodeBuilder
import org.loka.screensharekit.ScreenShareKit
import org.loka.screensharekit.callback.AudioCallBack
import org.loka.screensharekit.callback.RGBACallBack
import org.loka.screensharekit.callback.StartCaptureCallback

class PushActivity : BaseActivity() {
    private val binding by lazy { ActivityPushBinding.inflate(layoutInflater) }
    private val liveEngine by lazy { ArLiveEngine.create(this)}
    private val pusher by lazy { liveEngine.createArLivePusher() }
    private var pushUrl = ""
    private var pushType = 0
    companion object {
        const val PUSH_REQUEST_CODE_BLUETOOTH_CONNECT = 2 // 定义请求码
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        immersive(darkMode = false)
        PUSH_REQUEST_CODE_BLUETOOTH_CONNECT
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT), // 使用 arrayOf 代替 new String[]{...}
                PUSH_REQUEST_CODE_BLUETOOTH_CONNECT
            )
        }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PUSH_REQUEST_CODE_BLUETOOTH_CONNECT) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 权限被授予，可以执行需要BLUETOOTH_CONNECT权限的操作
                startBluetoothFunctionality()
            } else {
                // 权限被拒绝，可以提示用户或者关闭某些功能
                showPermissionDeniedMessage()
            }
        }
    }

    // 这个方法用于启动需要BLUETOOTH_CONNECT权限的功能
    private fun startBluetoothFunctionality() {
        // 这里添加启动蓝牙相关功能的代码
        pushType = intent.getIntExtra("pushType",0);
        val resolution = intent.getIntExtra("resolution",0)
        pushUrl = intent.getStringExtra("url").toString()
        when(resolution){
            0-> {
                pusher.setVideoQuality(ArLiveDef.ArLiveVideoEncoderParam(ArLiveVideoResolution.ArLiveVideoResolution1280x720))
            }
            1->{
                pusher.setVideoQuality(ArLiveDef.ArLiveVideoEncoderParam(ArLiveVideoResolution.ArLiveVideoResolution960x540))
            }
            2->{
                pusher.setVideoQuality(ArLiveDef.ArLiveVideoEncoderParam(ArLiveVideoResolution.ArLiveVideoResolution640x360))
            }
            3->{
                pusher.setVideoQuality(ArLiveDef.ArLiveVideoEncoderParam(ArLiveVideoResolution.ArLiveVideoResolution1920x1080))
            }
        }

        if (pushType == 0){
            pusher.setRenderView(binding.videoView)
            pusher.startCamera(true)
            pusher.startMicrophone()
            pusher.startPush(pushUrl)
        }else if (pushType == 1){
            binding.ivBeauty.visibility = View.GONE
            val imageLoader = ImageLoader.Builder(this)
                .componentRegistry {
                    if (SDK_INT >= 28) {
                        add(ImageDecoderDecoder(this@PushActivity))
                    } else {
                        add(GifDecoder())
                    }
                }
                .build()
            binding.ivGif.load("https://gimg2.baidu.com/image_search/src=http%3A%2F%2Fhbimg.b0.upaiyun.com%2F60ed9921abb8a968651aae697626dc816624cc4770c32-uwUmhP_fw658&refer=http%3A%2F%2Fhbimg.b0.upaiyun.com&app=2002&size=f9999,10000&q=a80&n=0&g=0n&fmt=jpeg?sec=1645166781&t=ff39058ea3e782746361d8b2bea68511",imageLoader)
            pusher.startScreenCapture()
            pusher.startMicrophone()
            pusher.startPush(pushUrl)
        }else{//自定义音视频采集
            pusher.enableCustomAudioCapture(true)
            pusher.enableCustomVideoCapture(true)
            ScreenShareKit.init(this).config(screenDataType = EncodeBuilder.SCREEN_DATA_TYPE.RGBA, audioCapture = true)
                .onRGBA(object :RGBACallBack{
                    override fun onRGBA(
                        rgba: ByteArray,
                        width: Int,
                        height: Int,
                        stride: Int,
                        rotation: Int,
                        rotationChanged: Boolean
                    ) {
                        pusher.sendCustomVideoFrame(ArLiveDef.ArLiveVideoFrame().apply {
                            pixelFormat = ArLiveDef.ArLivePixelFormat.ArLivePixelFormatBGRA32
                            bufferType = ArLiveDef.ArLiveBufferType.ArLiveBufferTypeByteArray
                            data = rgba
                            this.width = width
                            this.height = height
                            this.rotation = rotation
                            this.stride = stride*4
                        })
                    }

                })
                .onAudio(object :AudioCallBack{
                    override fun onAudio(buffer: ByteArray?, ts: Long) {
                        pusher.sendCustomAudioFrame(ArLiveDef.ArLiveAudioFrame().apply {
                            data = buffer
                            sampleRate = 16000
                            channel = 2
                        })
                    }

                })
                .onStart(object :StartCaptureCallback{
                    override fun onStart() {
                        pusher.startPush(pushUrl)
                    }

                })
                .start()

        }
        initView()
    }

    // 这个方法用于显示权限被拒绝的消息
    private fun showPermissionDeniedMessage() {
        // 这里添加提示用户权限被拒绝的消息，例如使用Toast或Snackbar
        Toast.makeText(this, "BLUETOOTH_CONNECT权限被拒绝，某些功能将不可用。", Toast.LENGTH_LONG).show()
    }
    private fun initView(){
        binding.run {
            tvUrl.text = pushUrl
            ivExit.setOnClickListener {
                if (pushType==2){
                    ScreenShareKit.stop()
                }
                ArLiveEngine.release()
                finish()
            }
            ivSwitch.setOnClickListener {
                pusher.deviceManager.switchCamera()
            }
            tvUrl.setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip: ClipData = ClipData.newPlainText("pushUrl",pushUrl)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this@PushActivity, "已复制", Toast.LENGTH_SHORT).show()
            }
            ivBeauty.setOnClickListener {
                ivBeauty.isSelected = !ivBeauty.isSelected
                pusher.beautyManager.setBeautyEffect(ivBeauty.isSelected)
            }
            ivAudio.setOnClickListener {
                ivAudio.isSelected = !ivAudio.isSelected
                if (ivAudio.isSelected){
                    pusher.pauseAudio()
                }else{
                    pusher.resumeAudio()
                }
            }
            ivVideo.setOnClickListener {
                ivVideo.isSelected = !ivVideo.isSelected
                if (ivVideo.isSelected){
                    pusher.pauseVideo()
                }else{
                    pusher.resumeVideo()
                }
            }

            ivMirro.setOnClickListener {
                ivMirro.isSelected = !ivMirro.isSelected
                pusher.setRenderMirror(if (ivMirro.isSelected)ArLiveDef.ArLiveMirrorType.ArLiveMirrorTypeEnable else ArLiveDef.ArLiveMirrorType.ArLiveMirrorTypeDisable)
                pusher.setEncoderMirror(ivMirro.isSelected)
            }

            pusher.setObserver(object :ArLivePusherObserver(){
                override fun onPushStatusUpdate(
                    status: ArLiveDef.ArLivePushStatus?,
                    msg: String?,
                    extraInfo: Bundle?
                ) {
                    super.onPushStatusUpdate(status, msg, extraInfo)
                    when(status){
                        ArLiveDef.ArLivePushStatus.ArLivePushStatusConnecting->{
                            tvState.setText("连接中....")
                        }
                        ArLiveDef.ArLivePushStatus.ArLivePushStatusConnectSuccess->{
                            tvState.setText("连接成功....")
                        }
                        ArLiveDef.ArLivePushStatus.ArLivePushStatusDisconnected->{
                            tvState.setText("连接断开....")
                        }
                        ArLiveDef.ArLivePushStatus.ArLivePushStatusReconnecting->{
                            tvState.setText("重连中....")
                        }
                    }
                }
            })

        }
    }

    override fun onBackPressed() {
        if (pushType==2){
            ScreenShareKit.stop()
        }
        ArLiveEngine.release()
        finish()
    }



}