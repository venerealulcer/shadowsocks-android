package com.vm.shadowsocks.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.*;
import android.widget.*;
import android.widget.CompoundButton.OnCheckedChangeListener;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.vm.shadowsocks.R;
import com.vm.shadowsocks.core.AppInfo;
import com.vm.shadowsocks.core.AppProxyManager;
import com.vm.shadowsocks.core.LocalVpnService;
import com.vm.shadowsocks.core.ProxyConfig;
import org.bouncycastle.util.encoders.Base64;

import java.io.UnsupportedEncodingException;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

import static com.vm.shadowsocks.ui.ParseHtml.DownLoadFromUrl;
import static com.vm.shadowsocks.ui.ParseHtml.GetServer;

public class MainActivity extends Activity implements
        View.OnClickListener,
        OnCheckedChangeListener,
        LocalVpnService.onStatusChangedListener {

    private static String GL_HISTORY_LOGS;

    private static final String TAG = MainActivity.class.getSimpleName();

    private static final String CONFIG_URL_KEY = "CONFIG_URL_KEY";

    private static final int START_VPN_SERVICE_REQUEST_CODE = 1985;

    private static String mBaseUrl = "";

    private Switch switchProxy;
    private TextView textViewLog;
    private ScrollView scrollViewLog;
    private TextView textViewProxyUrl, textViewProxyApp;
    private Calendar mCalendar;

    private boolean b_ShowException = false;

    private static List<String> listServer = new LinkedList<>();

    @SuppressLint("WrongViewCast")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        scrollViewLog = findViewById(R.id.scrollViewLog);
        textViewLog = findViewById(R.id.textViewLog);
        findViewById(R.id.ProxyUrlLayout).setOnClickListener(this);
        findViewById(R.id.AppSelectLayout).setOnClickListener(this);

        textViewProxyUrl = findViewById(R.id.textViewProxyUrl);
        String ProxyUrl = readProxyUrl();
        if (TextUtils.isEmpty(ProxyUrl)) {
            textViewProxyUrl.setText(R.string.config_not_set_value);
        } else {
            textViewProxyUrl.setText(ProxyUrl);
        }

        textViewLog.setText(GL_HISTORY_LOGS);
        scrollViewLog.fullScroll(ScrollView.FOCUS_DOWN);

        mCalendar = Calendar.getInstance();
        LocalVpnService.addOnStatusChangedListener(this);

        //Pre-App Proxy
        if (AppProxyManager.isLollipopOrAbove) {
            new AppProxyManager(this);
            textViewProxyApp = findViewById(R.id.textViewAppSelectDetail);
        } else {
            ((ViewGroup) findViewById(R.id.AppSelectLayout).getParent()).removeView(findViewById(R.id.AppSelectLayout));
            ((ViewGroup) findViewById(R.id.textViewAppSelectLine).getParent()).removeView(findViewById(R.id.textViewAppSelectLine));
        }

        readUrlPreference();

        onLogReceived("Shadowsock App Start...");

    }

    void readUrlPreference() {
        SharedPreferences preferences = getSharedPreferences("AppSettings", MODE_PRIVATE);
        mBaseUrl = preferences.getString("BaseUrl", null);

        if (mBaseUrl == null && mBaseUrl.isEmpty()) {
            onLogReceived("mBaseUrl : null");
        } else {
            onLogReceived("mBaseUrl : " + mBaseUrl);
        }
    }

    void saveUrlPreference() {
        SharedPreferences preferences = getSharedPreferences("AppSettings", MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("BaseUrl", mBaseUrl);
        editor.apply();
        onLogReceived("Set Url Source OK: " + mBaseUrl);
    }

    String readProxyUrl() {
        SharedPreferences preferences = getSharedPreferences("shadowsocksProxyUrl", MODE_PRIVATE);
        return preferences.getString(CONFIG_URL_KEY, "");
    }

    void setProxyUrl(String ProxyUrl) {
        SharedPreferences preferences = getSharedPreferences("shadowsocksProxyUrl", MODE_PRIVATE);
        Editor editor = preferences.edit();
        editor.putString(CONFIG_URL_KEY, ProxyUrl);
        editor.apply();
    }

    String getVersionName() {
        PackageManager packageManager = getPackageManager();
        if (packageManager == null) {
            Log.e(TAG, "null package manager is impossible");
            return null;
        }

        try {
            return packageManager.getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "package not found is impossible", e);
            return null;
        }
    }

    boolean isValidUrl(String url) {
        try {
            if (url == null || url.isEmpty())
                return false;

            if (url.startsWith("ss://")) {//file path
                return true;
            } else { //url
                Uri uri = Uri.parse(url);
                if (!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme()))
                    return false;
                if (uri.getHost() == null)
                    return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void onClick(View v) {
        if (switchProxy.isChecked()) {
            return;
        }

        if (v.getTag().toString().equals("ProxyUrl")) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.config_url)
                    .setItems(new CharSequence[]{
                            getString(R.string.config_url_scan),
                            getString(R.string.config_url_manual),
                            getString(R.string.menu_item_get_server)
                    }, new OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            switch (i) {
                                case 0:
                                    scanForProxyUrl();
                                    break;
                                case 1:
                                    showProxyUrlInputDialog();
                                    break;
                                case 2: {
                                    ShowGetServerDialog();
                                    break;
                                }
                            }
                        }
                    })
                    .show();
        } else if (v.getTag().toString().equals("AppSelect")) {
            startActivity(new Intent(this, AppManager.class));
        }
    }

    private void scanForProxyUrl() {
        new IntentIntegrator(this)
                .setPrompt(getString(R.string.config_url_scan_hint))
                .initiateScan(IntentIntegrator.QR_CODE_TYPES);
    }

    private void ShowSetBaseUrlDialog() {
        final EditText editText = new EditText(this);
        editText.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        editText.setText(mBaseUrl);
        new AlertDialog.Builder(this)
                .setTitle(R.string.config_url)
                .setView(editText)
                .setPositiveButton(R.string.btn_ok, new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (editText.getText() == null) {
                            return;
                        }
                        mBaseUrl = editText.getText().toString();
                        saveUrlPreference();
                    }
                }).setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void showProxyUrlInputDialog() {
        final EditText editText = new EditText(this);
        editText.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        editText.setHint(getString(R.string.config_url_hint));
        editText.setText(readProxyUrl());

        new AlertDialog.Builder(this)
                .setTitle(R.string.config_url)
                .setView(editText)
                .setPositiveButton(R.string.btn_ok, new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (editText.getText() == null) {
                            return;
                        }

                        String ProxyUrl = editText.getText().toString().trim();

                        if (ProxyUrl.startsWith("ss://")) {
                            String temp = ProxyUrl.substring(5);
                            if (!temp.contains(":")) {
                                try {
                                    String read = new String(Base64.decode(temp), "UTF-8");
                                    ProxyUrl = "ss://" + read;
                                } catch (UnsupportedEncodingException e) {
                                    e.printStackTrace();
                                }
                            }
                        }

                        if (isValidUrl(ProxyUrl)) {
                            setProxyUrl(ProxyUrl);
                            textViewProxyUrl.setText(ProxyUrl);
                        } else {
                            Toast.makeText(MainActivity.this, R.string.err_invalid_url, Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    @SuppressLint("DefaultLocale")
    @Override
    public void onLogReceived(String logString) {

        mCalendar.setTimeInMillis(System.currentTimeMillis());

        final String _logString = String.format("[%1$02d:%2$02d:%3$02d] %4$s\n",
                mCalendar.get(Calendar.HOUR_OF_DAY),
                mCalendar.get(Calendar.MINUTE),
                mCalendar.get(Calendar.SECOND),
                logString);

        System.out.println(_logString);

        if (!b_ShowException && _logString.contains("Exception")) {
            Toast.makeText(this, _logString, Toast.LENGTH_SHORT).show();
            b_ShowException = true;
        }

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (textViewLog.getLineCount() > 200) {
                    textViewLog.setText("");
                }
                textViewLog.append(_logString);
                scrollViewLog.fullScroll(ScrollView.FOCUS_DOWN);
                GL_HISTORY_LOGS = textViewLog.getText() == null ? "" : textViewLog.getText().toString();
            }
        });

    }

    @Override
    public void onStatusChanged(String status, Boolean isRunning) {
        switchProxy.setEnabled(true);
        switchProxy.setChecked(isRunning);
        onLogReceived(status);
        Toast.makeText(this, status, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (LocalVpnService.IsRunning != isChecked) {
            if (!switchProxy.getText().toString().contains(getString(R.string.config_not_set_value)))
                switchProxy.setEnabled(false);
            if (isChecked) {
                Intent intent = LocalVpnService.prepare(this);
                if (intent == null) {
                    startVPNService();
                } else {
                    startActivityForResult(intent, START_VPN_SERVICE_REQUEST_CODE);
                }
            } else {
                LocalVpnService.IsRunning = false;
            }
        }
    }

    private void startVPNService() {
        String ProxyUrl = readProxyUrl();
        if (!isValidUrl(ProxyUrl)) {
            Toast.makeText(this, R.string.err_invalid_url, Toast.LENGTH_SHORT).show();
            switchProxy.post(new Runnable() {
                @Override
                public void run() {
                    switchProxy.setChecked(false);
                    switchProxy.setEnabled(true);
                }
            });
            return;
        }
        b_ShowException = false;
        textViewLog.setText("");
        GL_HISTORY_LOGS = null;
        onLogReceived("starting...");
        LocalVpnService.ProxyUrl = ProxyUrl;
        startService(new Intent(this, LocalVpnService.class));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == START_VPN_SERVICE_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                startVPNService();
            } else {
                switchProxy.setChecked(false);
                switchProxy.setEnabled(true);
                onLogReceived("canceled.");
            }
            return;
        }

        IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, intent);
        if (scanResult != null) {
            String ProxyUrl = scanResult.getContents();

            if (ProxyUrl != null && ProxyUrl.startsWith("ss://")) {
                String temp = ProxyUrl.substring(5);
                if (!temp.contains(":")) {
                    try {
                        String read = new String(Base64.decode(temp), "UTF-8");
                        ProxyUrl = "ss://" + read;
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                }
            }

            if (isValidUrl(ProxyUrl)) {
                setProxyUrl(ProxyUrl);
                Toast.makeText(this, R.string.toaast_parse_ok, Toast.LENGTH_SHORT).show();
                textViewProxyUrl.setText(ProxyUrl);
            } else {
                Toast.makeText(MainActivity.this, R.string.err_invalid_url, Toast.LENGTH_SHORT).show();
            }
            return;
        }

        super.onActivityResult(requestCode, resultCode, intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_activity_actions, menu);

        MenuItem menuItem = menu.findItem(R.id.menu_item_switch);
        if (menuItem == null) {
            return false;
        }

        switchProxy = (Switch) menuItem.getActionView();
        if (switchProxy == null) {
            return false;
        }

        switchProxy.setChecked(LocalVpnService.IsRunning);
        switchProxy.setOnCheckedChangeListener(this);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_item_about:
                new AlertDialog.Builder(this)
                        .setTitle(getString(R.string.app_name) + " " + getVersionName() + " · Java · IDEA")
                        .setMessage(R.string.about_info)
                        .setPositiveButton(R.string.btn_ok, null)
                        .show();
                return true;
            case R.id.menu_item_exit:
                if (!LocalVpnService.IsRunning) {
                    finish();
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            System.exit(0);
                        }
                    }).start();
                    return true;
                }

                new AlertDialog.Builder(this)
                        .setTitle(R.string.menu_item_exit)
                        .setMessage(R.string.exit_confirm_info)
                        .setPositiveButton(R.string.btn_ok, new OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                LocalVpnService.IsRunning = false;
                                LocalVpnService.Instance.disconnectVPN();
                                stopService(new Intent(MainActivity.this, LocalVpnService.class));
                                System.runFinalization();
                                System.exit(0);
                            }
                        })
                        .setNegativeButton(R.string.btn_cancel, null)
                        .show();

                return true;
            case R.id.menu_item_toggle_global:
                ProxyConfig.Instance.globalMode = !ProxyConfig.Instance.globalMode;
                if (ProxyConfig.Instance.globalMode) {
                    onLogReceived("Proxy global mode is on");
                } else {
                    onLogReceived("Proxy global mode is off");
                }
                break;
            case R.id.menu_item_get_server:
                ShowGetServerDialog();
                break;
            case R.id.menu_item_set_server_url:
                ShowSetBaseUrlDialog();
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void ShowGetServerDialog() {
        if (listServer.size() == 0) {
            final AlertDialog pgd = new AlertDialog.Builder(this).create();

            final Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {

                    if (mBaseUrl == null || mBaseUrl.isEmpty())
                        onLogReceived("mBaseUrl is empty !");

                    String str = DownLoadFromUrl(mBaseUrl);

                    if (str == null) {
                        onLogReceived("DownLoadFromUrl: return null");
                    } else {
                        try {
                            listServer = GetServer(str);

                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    ShowSelectServer();
                                }
                            });

                        } catch (Exception ex) {
                            onLogReceived(ex.getMessage());
                        }
                    }

                    pgd.dismiss();
                }
            });

            thread.start();

            pgd.setTitle("Loading Server...");

            ProgressBar progressBar = new ProgressBar(this);
            progressBar.setPadding(10, 60, 10, 30);
            pgd.setView(progressBar);
            pgd.setButton(DialogInterface.BUTTON_POSITIVE, this.

                    getText(R.string.btn_cancel), new

                    OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            try {
                                thread.stop();
                            } catch (Exception ignored) {
                            }
                        }
                    });
            pgd.show();

        } else {
            ShowSelectServer();
        }
    }

    private void ShowSelectServer() {

        String currURL = textViewProxyUrl.getText().toString();

        int _SelectIndex = -1;

        for (int i = 0; i < listServer.size(); i++) {
            if (listServer.get(i).equals(currURL)) {
                _SelectIndex = i;
                break;
            }
        }

        final int SelectIndex = _SelectIndex;

        final int[] yourChoice = new int[]{-1};

        String[] strings = new String[listServer.size()];

        listServer.toArray(strings);

        final String[] items = strings;

        final AlertDialog.Builder singleChoiceDialog = new AlertDialog.Builder(MainActivity.this);

        singleChoiceDialog.setTitle("Select Server");

        singleChoiceDialog.setSingleChoiceItems(items, SelectIndex,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        yourChoice[0] = which;
                    }
                });

        singleChoiceDialog.setPositiveButton(R.string.btn_ok,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (yourChoice[0] == -1) return;
                        if (yourChoice[0] != SelectIndex) {

                            String ProxyUrl = items[yourChoice[0]];

                            if (isValidUrl(ProxyUrl)) {
                                setProxyUrl(ProxyUrl);
                                textViewProxyUrl.setText(ProxyUrl);

                                if (!switchProxy.isChecked())
                                    switchProxy.setChecked(true);
                                else {
                                    switchProxy.setChecked(false);
                                    new Thread(new Runnable() {
                                        @Override
                                        public void run() {
                                            try {
                                                Thread.sleep(500);
                                            } catch (InterruptedException e) {
                                                e.printStackTrace();
                                            }
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    switchProxy.setChecked(true);
                                                }
                                            });
                                        }
                                    }).start();
                                }
                            } else {
                                Toast.makeText(MainActivity.this, R.string.err_invalid_url, Toast.LENGTH_SHORT).show();
                            }

                            textViewProxyUrl.setText(ProxyUrl);
                        }
                    }
                });

        singleChoiceDialog.setNeutralButton("刷新服务器列表", new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                listServer.clear();
                ShowGetServerDialog();
            }
        });

        final AlertDialog dialog = singleChoiceDialog.show();

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                WindowManager m = getWindowManager();
                Display d = m.getDefaultDisplay();  //为获取屏幕宽、高
                android.view.WindowManager.LayoutParams p = dialog.getWindow().getAttributes();  //获取对话框当前的参数值
                p.height = (int) (d.getHeight() * 0.7);   //高度设置为屏幕的0.3
                p.width = (int) (d.getWidth());    //宽度设置为屏幕的0.5
                dialog.getWindow().setAttributes(p);     //设置生效
            }
        });
    }


    @Override
    protected void onResume() {
        super.onResume();
        b_ShowException = false;
        if (AppProxyManager.isLollipopOrAbove) {
            if (AppProxyManager.Instance.proxyAppInfo.size() != 0) {
                String tmpString = "";
                for (AppInfo app : AppProxyManager.Instance.proxyAppInfo) {
                    tmpString += app.getAppLabel() + ", ";
                }
                textViewProxyApp.setText(tmpString);
            }
        }
    }

    @Override
    protected void onDestroy() {
        LocalVpnService.removeOnStatusChangedListener(this);
        super.onDestroy();
    }

}
