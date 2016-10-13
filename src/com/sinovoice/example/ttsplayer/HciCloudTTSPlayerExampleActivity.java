package com.sinovoice.example.ttsplayer;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.sinovoice.example.AccountInfo;
import com.sinovoice.hcicloudsdk.android.tts.player.TTSPlayer;
import com.sinovoice.hcicloudsdk.api.HciCloudSys;
import com.sinovoice.hcicloudsdk.common.AuthExpireTime;
import com.sinovoice.hcicloudsdk.common.HciErrorCode;
import com.sinovoice.hcicloudsdk.common.InitParam;
import com.sinovoice.hcicloudsdk.common.asr.AsrInitParam;
import com.sinovoice.hcicloudsdk.common.hwr.HwrInitParam;
import com.sinovoice.hcicloudsdk.common.tts.TtsConfig;
import com.sinovoice.hcicloudsdk.common.tts.TtsInitParam;
import com.sinovoice.hcicloudsdk.player.TTSCommonPlayer;
import com.sinovoice.hcicloudsdk.player.TTSCommonPlayer.PlayerEvent;
import com.sinovoice.hcicloudsdk.player.TTSPlayerListener;

public class HciCloudTTSPlayerExampleActivity extends Activity {
	public static final String TAG = "HciCloudTTSPlayerExampleActivity";

    /**
     * 加载用户信息工具类
     */
    private AccountInfo mAccountInfo;

    
	private EditText editText = null;

	private TtsConfig ttsConfig = null;
	private TTSPlayer mTtsPlayer = null;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		editText = (EditText) findViewById(R.id.editText1);

        mAccountInfo = AccountInfo.getInstance();
        boolean loadResult = mAccountInfo.loadAccountInfo(this);
        if (loadResult) {
            // 加载信息成功进入主界面
			Toast.makeText(getApplicationContext(), "加载灵云账号成功",
					Toast.LENGTH_SHORT).show();
        } else {
            // 加载信息失败，显示失败界面
			Toast.makeText(getApplicationContext(), "加载灵云账号失败！请在assets/AccountInfo.txt文件中填写正确的灵云账户信息，账户需要从www.hcicloud.com开发者社区上注册申请。",
					Toast.LENGTH_SHORT).show();
            return;
        }

        // 加载信息,返回InitParam, 获得配置参数的字符串
        InitParam initParam = getInitParam();
        String strConfig = initParam.getStringConfig();
        Log.i(TAG,"\nhciInit config:" + strConfig);
        
        //此函数为系统初始化，最好放到主线程中，多次循环的时候也不要多次进行调用。
        int errCode = HciCloudSys.hciInit(strConfig, this);
        if (errCode != HciErrorCode.HCI_ERR_NONE && errCode != HciErrorCode.HCI_ERR_SYS_ALREADY_INIT) {
        	Toast.makeText(getApplicationContext(), "hciInit error: " + HciCloudSys.hciGetErrorInfo(errCode),Toast.LENGTH_SHORT).show();
            return;
        } 
        
        // 获取授权/更新授权文件 :
        errCode = checkAuthAndUpdateAuth();
        if (errCode != HciErrorCode.HCI_ERR_NONE) {
            // 由于系统已经初始化成功,在结束前需要调用方法hciRelease()进行系统的反初始化
        	Toast.makeText(getApplicationContext(), "CheckAuthAndUpdateAuth error: " + HciCloudSys.hciGetErrorInfo(errCode),Toast.LENGTH_SHORT).show();
            HciCloudSys.hciRelease();
            return;
        }
        

        //传入了capKey初始化TTS播发器
		boolean isPlayerInitSuccess = initPlayer();
		if (!isPlayerInitSuccess) {
			Toast.makeText(this, "播放器初始化失败", Toast.LENGTH_LONG).show();
			return;
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (mTtsPlayer != null) {
			mTtsPlayer.release();
		}
		HciCloudSys.hciRelease();
	}

	// 测试按钮 ,播放,停止TTS语音播放
	public void onClick(View v) {
		if (mTtsPlayer != null) {
			try {
				switch (v.getId()) {
				case R.id.synth:
					// 开始合成
					synth(editText.getText().toString());
					break;

				case R.id.btnPause:
					if (mTtsPlayer.getPlayerState() == TTSCommonPlayer.PLAYER_STATE_PLAYING) {
						mTtsPlayer.pause();
					}
					break;

				case R.id.btnResume:
					if (mTtsPlayer.getPlayerState() == TTSCommonPlayer.PLAYER_STATE_PAUSE) {
						mTtsPlayer.resume();
					}
					break;

				default:
					break;
				}
			} catch (IllegalStateException ex) {
				Toast.makeText(getBaseContext(), "状态错误", Toast.LENGTH_SHORT)
						.show();
			}
		}
	}
	
	/**
	 * 初始化播放器
	 */
	private boolean initPlayer() {
		// 读取用户的调用的能力
		String capKey = mAccountInfo.getCapKey();
		
		// 构造Tts初始化的帮助类的实例
		TtsInitParam ttsInitParam = new TtsInitParam();
		// 获取App应用中的lib的路径
		//使用本地的capkey需要配置dataPath，云端的capkey可以不用配置。
		String dataPath = getBaseContext().getFilesDir().getAbsolutePath().replace("files", "lib");
		ttsInitParam.addParam(TtsInitParam.PARAM_KEY_DATA_PATH, dataPath);
		//加载语音库以so的方式，需要把对应的音库资源拷贝到libs/armeabi目录下，并修改名字为libxxx.so的方式。
        //还可以按照none的方式加载，此时不需要对音库修改名称，直接拷贝到dataPath目录下即可，最好设置dataPath为sd卡目录。比如
        //String dataPath = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "sinovoice";
		ttsInitParam.addParam(AsrInitParam.PARAM_KEY_INIT_CAP_KEYS, capKey);
		// 使用lib下的资源文件,需要添加android_so的标记
		ttsInitParam.addParam(HwrInitParam.PARAM_KEY_FILE_FLAG, "android_so");
		
		mTtsPlayer = new TTSPlayer();
		
		// 配置TTS初始化参数
		ttsConfig = new TtsConfig();
		mTtsPlayer.init(ttsInitParam.getStringConfig(), new TTSEventProcess());

		if (mTtsPlayer.getPlayerState() == TTSPlayer.PLAYER_STATE_IDLE) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * 语音合成函数
	 * @param text	需要合成的文本
	 */
	private void synth(String text) {
		// 读取用户的调用的能力
		String capKey = mAccountInfo.getCapKey();
		
		//语音合成的配置串参数，可以通过addParam参数添加，列出一些常见的参数配置
        TtsConfig synthConfig = new TtsConfig();
        //使用音库的前缀信息，使用本地的capkey，需要添加资源文件，资源文件有 CNPackage.dat,ENPackage.dat,DMPackage.dat,Default.conf
        //在android中使用时，需要放到libs/armeabi目录下，并修改文件名为libxxx.so，如果设置resPrefix参数，可以用来区分多个音库
        //比如使用王静的音库，可以设置加上WangJing_的前缀，此时要修改文件名为libWangJing_xxx.so
        synthConfig.addParam(TtsConfig.SessionConfig.PARAM_KEY_RES_PREFIX, "WangJing_");
        //设置使用的capkey信息
        synthConfig.addParam(TtsConfig.SessionConfig.PARAM_KEY_CAP_KEY, capKey);
        //合成的音频格式，默认为pcm16k16bit
        synthConfig.addParam(TtsConfig.BasicConfig.PARAM_KEY_AUDIO_FORMAT, "pcm16k16bit");
        //合成的语速设置，默认为5
        synthConfig.addParam(TtsConfig.BasicConfig.PARAM_KEY_SPEED, "5");
        //合成的基频设置，默认为5
        synthConfig.addParam(TtsConfig.BasicConfig.PARAM_KEY_PITCH, "5");
        //合成的音量设置，默认为5
        synthConfig.addParam(TtsConfig.BasicConfig.PARAM_KEY_VOLUME, "5");
        //数字阅读方式，有电报读法和数字读法两种
        synthConfig.addParam(TtsConfig.BasicConfig.PARAM_KEY_DIGIT_MODE, "auto_number");
        //英文阅读方式 ，有按照单词和字母两种方式，默认为自动判断
        synthConfig.addParam(TtsConfig.BasicConfig.PARAM_KEY_ENG_MODE, "auto");
        //标点符号读法，读符号和不读符号两种，默认为不读
        synthConfig.addParam(TtsConfig.BasicConfig.PARAM_KEY_PUNC_MODE, "off");
        //标记处理方式 ,仅本地能力支持，默认为none，本地支持s3ml标记
//        synthConfig.addParam(TtsConfig.BasicConfig.PARAM_KEY_TAG_MODE, "none");
        //朗读风格, clear: 清晰		vivid: 生动 		normal: 抑扬顿挫  		plain: 平稳庄重 
//        synthConfig.addParam(TtsConfig.BasicConfig.PARAM_KEY_VOICE_STYLE, "clear");
        //只有云端的capkey支持此方法，云端合成后，把音频文件发送到本地客户端时可以进行压缩以减少流量消耗，默认为none，不压缩
        //speex压缩，压缩的比较多，对应的libs/armeabi目录下的libjtspeex.so库，设置为此方式时需要添加对应的so库。
        //opus压缩，对应的libs/armeabi目录下的libjtopus.so库，设置为此方式时需要添加对应的so库。
//        synthConfig.addParam(TtsConfig.EncodeConfig.PARAM_KEY_ENCODE, "speex");

		if (mTtsPlayer.getPlayerState() == TTSCommonPlayer.PLAYER_STATE_PLAYING
				|| mTtsPlayer.getPlayerState() == TTSCommonPlayer.PLAYER_STATE_PAUSE) {
			mTtsPlayer.stop();
		}

		if (mTtsPlayer.getPlayerState() == TTSCommonPlayer.PLAYER_STATE_IDLE) {
			mTtsPlayer.play(text, synthConfig.getStringConfig());
		} else {
			Toast.makeText(HciCloudTTSPlayerExampleActivity.this, "播放器内部状态错误",
					Toast.LENGTH_SHORT).show();
		}
	}

	/**
	 * 播放器的回调函数
	 * @author sinovoice
	 */
	private class TTSEventProcess implements TTSPlayerListener {

		//播放器错误回调，出错时会走到此回调
		@Override
		public void onPlayerEventPlayerError(PlayerEvent playerEvent,
				int errorCode) {
			Log.i(TAG, "onError " + playerEvent.name() + " code: " + errorCode);
		}

		//播放器的进度回调
		@Override
		public void onPlayerEventProgressChange(PlayerEvent playerEvent,
				int start, int end) {
			Log.i(TAG, "onProcessChange " + playerEvent.name() + " from "
					+ start + " to " + end);
		}

		//播放器的状态变化回调
		@Override
		public void onPlayerEventStateChange(PlayerEvent playerEvent) {
			Log.i(TAG, "onStateChange " + playerEvent.name());
		}

	}
	
	/**
     * 检测授权是否需要更新，此函数如果授权不存在，会调用hciCheckAuth函数进行联网更新，如果授权存在，则检测授权是否过期，没有过期就跳过了。
     * @return true 成功
     */
    private int checkAuthAndUpdateAuth() {
        
    	// 获取系统授权到期时间
        int initResult;
        AuthExpireTime objExpireTime = new AuthExpireTime();
        initResult = HciCloudSys.hciGetAuthExpireTime(objExpireTime);
        if (initResult == HciErrorCode.HCI_ERR_NONE) {
            // 显示授权日期,如用户不需要关注该值,此处代码可忽略
            Date date = new Date(objExpireTime.getExpireTime() * 1000);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
            Log.i(TAG, "expire time: " + sdf.format(date));

            if (objExpireTime.getExpireTime() * 1000 > System.currentTimeMillis()) {
                // 已经成功获取了授权,并且距离授权到期有充足的时间(>7天)
                Log.i(TAG, "checkAuth success");
                return initResult;
            }
            
        } 
        
        // 获取过期时间失败或者已经过期
        initResult = HciCloudSys.hciCheckAuth();
        if (initResult == HciErrorCode.HCI_ERR_NONE) {
            Log.i(TAG, "checkAuth success");
            return initResult;
        } else {
            Log.e(TAG, "checkAuth failed: " + initResult);
            return initResult;
        }
    }
    
    /**
     * 加载初始化信息，其中的key信息需要从开发者社区注册应用，在应用详情中获取，并配置到assets目录下的AccountInfo.txt文件中
     * 
     * @param context 上下文语境
     * @return 系统初始化参数
     */
    private InitParam getInitParam() {
        String authDirPath = this.getFilesDir().getAbsolutePath();

        // 前置条件：无
        InitParam initparam = new InitParam();

        // 授权文件所在路径，此项必填
        initparam.addParam(InitParam.AuthParam.PARAM_KEY_AUTH_PATH, authDirPath);

        // 是否自动访问云授权,详见 获取授权/更新授权文件处注释
        initparam.addParam(InitParam.AuthParam.PARAM_KEY_AUTO_CLOUD_AUTH, "no");

        // 灵云云服务的接口地址，此项必填
        initparam.addParam(InitParam.AuthParam.PARAM_KEY_CLOUD_URL, AccountInfo.getInstance().getCloudUrl());

        // 开发者Key，此项必填，由捷通华声提供
        initparam.addParam(InitParam.AuthParam.PARAM_KEY_DEVELOPER_KEY, AccountInfo.getInstance().getDeveloperKey());

        // 应用Key，此项必填，由捷通华声提供
        initparam.addParam(InitParam.AuthParam.PARAM_KEY_APP_KEY, AccountInfo.getInstance().getAppKey());

        // 配置日志参数
        String sdcardState = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(sdcardState)) {
            String sdPath = Environment.getExternalStorageDirectory()
                    .getAbsolutePath();
            String packageName = this.getPackageName();

            String logPath = sdPath + File.separator + "sinovoice"
                    + File.separator + packageName + File.separator + "log"
                    + File.separator;

            // 日志文件地址
            File fileDir = new File(logPath);
            if (!fileDir.exists()) {
                fileDir.mkdirs();
            }

            // 日志的路径，可选，如果不传或者为空则不生成日志
            initparam.addParam(InitParam.LogParam.PARAM_KEY_LOG_FILE_PATH, logPath);

            // 日志数目，默认保留多少个日志文件，超过则覆盖最旧的日志
            initparam.addParam(InitParam.LogParam.PARAM_KEY_LOG_FILE_COUNT, "5");

            // 日志大小，默认一个日志文件写多大，单位为K
            initparam.addParam(InitParam.LogParam.PARAM_KEY_LOG_FILE_SIZE, "1024");

            // 日志等级，0=无，1=错误，2=警告，3=信息，4=细节，5=调试，SDK将输出小于等于logLevel的日志信息
            initparam.addParam(InitParam.LogParam.PARAM_KEY_LOG_LEVEL, "5");
        }

        return initparam;
    }

}
