package com.wty.callbyvoice;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.GrammarListener;
import com.iflytek.cloud.InitListener;
import com.iflytek.cloud.RecognizerListener;
import com.iflytek.cloud.RecognizerResult;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechRecognizer;
import com.iflytek.cloud.util.ContactManager;
import com.iflytek.cloud.util.ContactManager.ContactListener;
import com.iflytek.cloud.LexiconListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.RunnableFuture;

import util.FucUtil;
import util.JsonParser;
import util.ListParser;

public class MainActivity extends AppCompatActivity {

    private static String TAG = "Wutaoyu";

    // 语音识别对象
    private SpeechRecognizer mAsr;

    // 云端语法文件
    private String mCloudGrammar = null;
    // 函数调用返回值
    int ret = 0;
    //构建出来的语法文件的字符串
    String mContent;
    //获得的联系人的字符串
    private String mContact = null;

    private Toast mToast;
    private String mEngineType = null;
    private static final int PERMISSIONS_REQUEST = 100;
    private String[] permissionArray = new String[]{
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.CALL_PHONE
    };

    // 缓存
    private SharedPreferences mSharedPreferences;
    private static final String KEY_GRAMMAR_ABNF_ID = "grammar_abnf_id";
    private static final String GRAMMAR_TYPE_ABNF = "abnf";


    private Button mStartBtn;
    private Button mButton;

    //联系人相关变量
    private List<HashMap<String,Object>> contact = new ArrayList<HashMap<String,Object>>();
    private String contactName = "" ;
    private String contactPhone = "" ;
    private boolean isGetContactSuccess = false;
    //判断是否要继续创建线程
    private boolean flag=false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAsr = SpeechRecognizer.createRecognizer(MainActivity.this, mInitListener);

        //android6.0需要请求通权限
        // Check the SDK version and whether the permission is already granted or not.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                ((checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED)
                ||(checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
                        ||(checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
                        ||(checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED)
                ))

        {
            requestPermissions(permissionArray, PERMISSIONS_REQUEST);
            //After this point you wait for callback in onRequestPermissionsResult(int, String[], int[]) overriden method
        }


        //开启一个子线程获取联系人信息
        new Thread(new Runnable()
        {
            public void run()
            {
                showTip("正在获取联系人...");
                try
                {
                    searchAllContact();
                    showTip("获取联系人成功");
                }
                catch(Exception e)
                {
                    showTip("获取联系人失败");
                }
            }

        }).start();

        //获取 ContactManager 实例化对象
        ContactManager mgr = ContactManager.createManager(MainActivity.this, mContactListener);

        //异步查询联系人接口，通过 onContactQueryFinish 接口回调
        mgr.asyncQueryAllContactsName();

        //缓存设置
        mSharedPreferences = getSharedPreferences(getPackageName(),	MODE_PRIVATE);

        //mToast参数设置
        mToast = Toast.makeText(this,"",Toast.LENGTH_SHORT);

        //联系人有更新之后要上传语法文件到服务器并等待生效
        mButton = (Button) findViewById(R.id.button);
        mButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                asr();
            }
        });

        //按钮，开始监听
        ToggleButton mTogBtn = (ToggleButton) findViewById(R.id.btn_start); // 获取到控件
        mTogBtn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView,boolean isChecked) {
                // TODO Auto-generated method stub
                if(isChecked){
                    //开启一个子线程监听语音信息
                    flag=true;
                    new Thread(new Runnable()
                    {
                        public void run()
                        {
                            while(flag) {
                                try {

                                    ret = mAsr.startListening(mRecognizerListener);
                                    if (ret != ErrorCode.SUCCESS) {
                                        showTip("识别失败,错误码: " + ret);
                                    }
                                    Thread.sleep(4000);
                                } catch (Exception e) {
                                    Thread.currentThread().interrupt();
                                }
                            }
                        }

                    }).start();
                    //选中
                }
                else{
                    flag=false;//未选中
                }
            }
        });// 添加监听事件


    }

    /**
     * 构建语法文件及设置参数
     */
    public void asr() {
        mCloudGrammar = FucUtil.readFile(this,"grammar.abnf","utf-8");
        StringBuffer result = new StringBuffer();
        result = new StringBuffer(mCloudGrammar);
        result.append(new StringBuffer(mContact));
        result.append(";");

        mContent=result.toString();


        mEngineType = SpeechConstant.TYPE_CLOUD;

        // 构建语法文件
        mAsr.setParameter(SpeechConstant.ENGINE_TYPE, mEngineType);
        mAsr.setParameter(SpeechConstant.TEXT_ENCODING, "utf-8");
        ret = mAsr.buildGrammar( GRAMMAR_TYPE_ABNF,mContent,mCloudGrammarListener);
        if (ret != ErrorCode.SUCCESS){
            Log.d(TAG,"语法构建失败,错误码：" + ret);
        }else{
            Log.d(TAG,"语法构建成功" );
        }

    }
    /**
     * 云端构建语法监听器
     */
    private GrammarListener mCloudGrammarListener = new GrammarListener() {

        @Override
        public void onBuildFinish(String grammarId, SpeechError error) {
            if(error == null){
                String grammarID = new String(grammarId);
                SharedPreferences.Editor editor = mSharedPreferences.edit();
                if(!TextUtils.isEmpty(grammarId))
                    editor.putString(KEY_GRAMMAR_ABNF_ID, grammarID);

                editor.commit();
                showTip("语法构建成功：" + grammarId);
                //参数设置
                if (!setParam()) {
                    showTip("构建语法失败。");
                    return;
                }



                if (ret != ErrorCode.SUCCESS) {
                    showTip("识别失败,错误码: " + ret);
                }
            }else{
                showTip("语法构建失败,错误码：" + error.getErrorCode());
            }
        }
    };


    /**
     * 获取联系人监听器
     */

    private ContactListener mContactListener = new ContactListener() {
        @Override
        public void onContactQueryFinish(String contactInfos, boolean changeFlag) {
            //返回联系人信息
            String str=contactInfos.replaceAll("\n","|");
            mContact = str.substring(0,str.length()-1);
        }
    };

    /**
     * 识别监听器。
     */
    private RecognizerListener mRecognizerListener = new RecognizerListener() {


        @Override
        public void onVolumeChanged(int volume, byte[] data) {
            showTip("当前正在说话，音量大小：" + volume);
            Log.d(TAG, "返回音频数据："+data.length);
        }

        @Override
        public void onResult(final RecognizerResult result, boolean isLast) {
            if(false == isLast) {
                if (null != result) {
                    Log.d(TAG, "recognizer result：" + result.getResultString());
                    List<String> voiceList = new ArrayList<String>();
                    voiceList=JsonParser.parseGrammarResult(result.getResultString());
                    //显示
                    String text= ListParser.TurnListToString(voiceList);
                    ((EditText) findViewById(R.id.isr_text)).setText(text);

                    //若获取本地联系人成功，则开始拨号
                    if(isGetContactSuccess)
                    {
                        CallIt(voiceList);
                    }

                    // 显示
                    //((EditText) findViewById(R.id.isr_text)).setText(text);
                } else {
                    Log.d(TAG, "recognizer result : null");
                }
            }
        }

        @Override
        public void onEndOfSpeech() {
            // 此回调表示：检测到了语音的尾端点，已经进入识别过程，不再接受语音输入
            showTip("结束说话");
        }

        @Override
        public void onBeginOfSpeech() {
            // 此回调表示：sdk内部录音机已经准备好了，用户可以开始语音输入
            showTip("开始说话");
        }

        @Override
        public void onError(SpeechError error) {
            showTip("onError Code："	+ error.getErrorCode());
        }

        @Override
        public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
            // 以下代码用于获取与云端的会话id，当业务出错时将会话id提供给技术支持人员，可用于查询会话日志，定位出错原因
            // 若使用本地能力，会话id为null
            //	if (SpeechEvent.EVENT_SESSION_ID == eventType) {
            //		String sid = obj.getString(SpeechEvent.KEY_EVENT_SESSION_ID);
            //		Log.d(TAG, "session id =" + sid);
            //	}
        }

    };
    /**
     * 初始化监听器。
     */
    private InitListener mInitListener = new InitListener() {

        @Override
        public void onInit(int code) {
            Log.d(TAG, "SpeechRecognizer init() code = " + code);
            if (code != ErrorCode.SUCCESS) {
                showTip("初始化失败,错误码："+code);
            }
        }
    };


    //请求权限后的处理
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        if(requestCode== PERMISSIONS_REQUEST){

                if (verifyPermissions(grantResults)) {
                // Permission is granted
                } else {
                    Toast.makeText(this, "未授予权限", Toast.LENGTH_SHORT).show();
                }
                super.onRequestPermissionsResult(requestCode,permissions, grantResults);
        }
    }
    //判断权限请求的结果
    public static boolean verifyPermissions(int[] grantResults) {
        // At least one result must be checked.
        if (grantResults.length < 1) {
            return false;
        }

        // Verify that each required permission has been granted, otherwise return false.
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }
    /**
     * 参数设置
     * @return
     */
    public boolean setParam(){
        boolean result = false;
        //设置识别引擎
        mAsr.setParameter(SpeechConstant.ENGINE_TYPE, mEngineType);
        //设置返回结果为json格式
        mAsr.setParameter(SpeechConstant.RESULT_TYPE, "json");
        //  设置标点符号, 设置为"0" 返回结果无标点, 设置为"1" 返回结果有标点
        mAsr.setParameter(SpeechConstant.ASR_PTT, "0");

            String grammarId = mSharedPreferences.getString(KEY_GRAMMAR_ABNF_ID, null);
            if(TextUtils.isEmpty(grammarId))
            {
                result =  false;
            }else {
                //设置云端识别使用的语法id
                mAsr.setParameter(SpeechConstant.CLOUD_GRAMMAR, grammarId);
                result =  true;
            }


        // 设置音频保存路径，保存音频格式支持pcm、wav，设置路径为sd卡请注意WRITE_EXTERNAL_STORAGE权限
        // 注：AUDIO_FORMAT参数语记需要更新版本才能生效
        mAsr.setParameter(SpeechConstant.AUDIO_FORMAT,"wav");
        mAsr.setParameter(SpeechConstant.ASR_AUDIO_PATH, Environment.getExternalStorageDirectory()+"/msc/asr.wav");
        return result;
    }


    //获取通讯录中所有联系人的姓名和电话号码
    public void searchAllContact() throws Exception
    {

        Uri uri = Uri.parse("content://com.android.contacts/contacts");			//获取所有联系人id
        ContentResolver resolver = getApplicationContext().getContentResolver();
        Cursor cursor = resolver.query(uri, new String[]{"_id"}, null, null, null);
        while(cursor.moveToNext())
        {
            int contactid = cursor.getInt(0);

            uri = Uri.parse("content://com.android.contacts/contacts/"+contactid+"/data");
            Cursor datacursor = resolver.query(uri, new String[]{"mimetype","data1","data2"}, null, null, null);
            while(datacursor.moveToNext())
            {
                String data = datacursor.getString(datacursor.getColumnIndex("data1"));
                String type = datacursor.getString(datacursor.getColumnIndex("mimetype"));
                if("vnd.android.cursor.item/name".equals(type))
                {
                    contactName = data;
                }
                else if("vnd.android.cursor.item/phone_v2".equals(type))
                {
                    contactPhone = data;
                }
            }
            HashMap<String,Object> map = new HashMap<String,Object>();
            map.put("id", contactid);
            map.put("name", contactName);
            map.put("phone", contactPhone);
            contact.add(map);
            contactName = "";
            contactPhone = "";
        }
        isGetContactSuccess = true;
        showTip("本地获取联系人成功，可开始拨号");
    }

    //查询并拨号
    public void CallIt(List<String> voiceList)
    {
        Iterator<HashMap<String,Object>> it = contact.iterator();
        Intent intentCall = new Intent();
        while(it.hasNext())
        {
            HashMap<String,Object> hashmap = it.next();
            for(String str : voiceList)
            {
                if(hashmap.get("name").toString().equals(str))
                {
                    intentCall.setAction("android.intent.action.CALL");
                    intentCall.setData(Uri.parse("tel:"+hashmap.get("phone")));
                    startActivity(intentCall);
                }
            }
        }
    }

    //弹窗信息
    private void showTip(final String str) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mToast.setText(str);
                mToast.show();
            }
        });
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 退出时释放连接
        mAsr.cancel();
        mAsr.destroy();
    }
}
