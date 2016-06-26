package com.fanxin.app.ui;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.easemob.redpacketsdk.RPCallback;
import com.easemob.redpacketsdk.RedPacket;
import com.fanxin.app.DemoApplication;
import com.fanxin.app.DemoHelper;
import com.fanxin.app.R;
import com.fanxin.app.db.DemoDBManager;
import com.fanxin.app.main.FXConstant;
import com.fanxin.app.main.MainActivity;
import com.hyphenate.EMCallBack;
import com.hyphenate.chat.EMClient;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Login screen
 */
public class LoginActivity extends BaseActivity {
    private static final String TAG = "LoginActivity";

    private EditText et_usertel;
    private EditText et_password;
    private boolean autoLogin = false;
    private Button btn_login;
    private Button btn_qtlogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // enter the main activity if already logged in
        if (DemoHelper.getInstance().isLoggedIn()) {
            autoLogin = true;
            startActivity(new Intent(LoginActivity.this, MainActivity.class));

            return;
        }
        setContentView(R.layout.fx_activity_login);

        et_usertel = (EditText) findViewById(R.id.et_usertel);
        et_password = (EditText) findViewById(R.id.et_password);
        btn_login = (Button) findViewById(R.id.btn_login);
        btn_qtlogin = (Button) findViewById(R.id.btn_qtlogin);
        // 监听多个输入框
        TextChange textChange = new TextChange();
        et_usertel.addTextChangedListener(textChange);
        et_password.addTextChangedListener(textChange);
        // if user changed, clear the password
        et_usertel.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                et_password.setText(null);
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });
        //TODO 此处可预置上次登陆的手机号
        //		if (DemoHelper.getInstance().getCurrentUsernName() != null) {
        //			et_usertel.setText(DemoHelper.getInstance().getCurrentUsernName());
        //		}


        btn_login.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loginInSever(et_usertel.getText().toString(),et_password.getText().toString());
            }
        });
    }

    private void loginInSever(String tel, String password) {
        final ProgressDialog pd = new ProgressDialog(LoginActivity.this);
        pd.setCanceledOnTouchOutside(false);

        pd.setMessage(getString(R.string.Is_landing));
        pd.show();

        RequestBody formBody = new FormBody.Builder()
                .add("usertel",tel)
                .add("password",password)
                .build();

        Request request = new Request.Builder()
                .url(FXConstant.URL_Login)
                .post(formBody)
                .build();

        DemoApplication.getInstance().okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {

            }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String result = null;
                try {
                    result =((Response) response).body().string();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                try {
                    JSONObject jsonObject = JSONObject.parseObject(result);
                    int code = jsonObject.getInteger("code");
                    if (code == 1) {

                        JSONObject json = jsonObject.getJSONObject("user");
                        loginHuanXin(json, pd);
                    } else if (code == 2) {
                        pd.dismiss();
                        Toast.makeText(LoginActivity.this,
                                "账号或密码错误...", Toast.LENGTH_SHORT)
                                .show();
                    } else if (code == 3) {
                        pd.dismiss();
                        Toast.makeText(LoginActivity.this,
                                "服务器端注册失败...", Toast.LENGTH_SHORT)
                                .show();
                    } else {
                        pd.dismiss();
                        Toast.makeText(LoginActivity.this,
                                "服务器繁忙请重试...", Toast.LENGTH_SHORT)
                                .show();
                    }

                } catch (JSONException e) {


                }
            }
        });

    }

    private void loginHuanXin(JSONObject jsonObject, final ProgressDialog progressDialog){
        final String nick = jsonObject.getString("nick");
        final String hxid = jsonObject.getString("hxid");
        final String password = jsonObject.getString("password");

        DemoDBManager.getInstance().closeDB();

        // reset current user name before login
        DemoHelper.getInstance().setCurrentUserName(hxid);

        final long start = System.currentTimeMillis();
        // call login method
        Log.d(TAG, "EMClient.getInstance().login");
        EMClient.getInstance().login(hxid, password, new EMCallBack() {

            @Override
            public void onSuccess() {
                Log.d(TAG, "login: onSuccess");

                if (!LoginActivity.this.isFinishing() && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }

                // ** manually load all local groups and conversation
                EMClient.getInstance().groupManager().loadAllGroups();
                EMClient.getInstance().chatManager().loadAllConversations();

                // uprogressDialogate current user's display name for APNs
                boolean updatenick = EMClient.getInstance().updateCurrentUserNick(
                        nick);
                if (!updatenick) {
                    Log.e("LoginActivity", "update current user nick fail");
                }

                // get user's info (this should be get from App's server or 3rd party service)
                DemoHelper.getInstance().getUserProfileManager().asyncGetCurrentUserInfo();

                RedPacket.getInstance().initRPToken(hxid, hxid, EMClient.getInstance().getChatConfig().getAccessToken(), new RPCallback() {
                    @Override
                    public void onSuccess() {

                    }
                    @Override
                    public void onError(String s, String s1) {

                    }
                });
                // enter main activity
                Intent intent = new Intent(LoginActivity.this,
                        MainActivity.class);
                startActivity(intent);

                finish();
            }

            @Override
            public void onProgress(int progress, String status) {
                Log.d(TAG, "login: onProgress");
            }

            @Override
            public void onError(final int code, final String message) {
                Log.d(TAG, "login: onError: " + code);

                runOnUiThread(new Runnable() {
                    public void run() {
                        progressDialog.dismiss();
                        Toast.makeText(getApplicationContext(), getString(R.string.Login_failed) + message,
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });

    }



    // EditText监听器
    class TextChange implements TextWatcher {

        @Override
        public void afterTextChanged(Editable arg0) {

        }

        @Override
        public void beforeTextChanged(CharSequence arg0, int arg1, int arg2,
                                      int arg3) {

        }

        @Override
        public void onTextChanged(CharSequence cs, int start, int before,
                                  int count) {

            boolean Sign2 = et_usertel.getText().length() > 0;
            boolean Sign3 = et_password.getText().length() > 0;

            if (Sign2 & Sign3) {
                btn_login.setEnabled(true);
            }
            // 在layout文件中，对Button的text属性应预先设置默认值，否则刚打开程序的时候Button是无显示的
            else {
                btn_login.setEnabled(false);
            }
        }

    }






    @Override
    protected void onResume() {
        super.onResume();
        if (autoLogin) {
            return;
        }
    }
}