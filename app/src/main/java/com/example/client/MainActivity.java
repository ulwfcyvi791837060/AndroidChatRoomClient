package com.example.client;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private Button enter;
    private EditText nameInput;
    private EditText ipInput;
    private EditText portInput;

    public static final String PREFS_NAME = "MyPrefsFile";
    private static final String PREF_IP = "ipAddress";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 隐藏 ActionBar
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }

        // 初始化视图
        enter = findViewById(R.id.enter);
        nameInput = findViewById(R.id.name_input);
        ipInput = findViewById(R.id.ip_input);
        portInput = findViewById(R.id.port_input);

        // 加载保存的IP地址
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        String savedIp = settings.getString(PREF_IP, "100.127.142.1");

        // 设置默认值
        nameInput.setText("Lpp");
        ipInput.setText(savedIp);
        portInput.setText("12580");

        // 设置点击事件监听
        enter.setOnClickListener(v -> {
            // 从输入框获取文本
            String name = nameInput.getText().toString().trim();
            String ip = ipInput.getText().toString().trim();
            String port = portInput.getText().toString().trim();

            // 简单的输入验证
            if (name.isEmpty() || ip.isEmpty() || port.isEmpty()) {
                Toast.makeText(MainActivity.this, "名称、IP和端口不能为空", Toast.LENGTH_SHORT).show();
                return;
            }

            // 保存IP地址
            SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, 0).edit();
            editor.putString(PREF_IP, ip);
            editor.apply();

            // 创建意图并传递数据
            Intent intent = new Intent(MainActivity.this, ChatRoom.class);
            intent.putExtra("name", name);
            intent.putExtra("ip", ip);
            intent.putExtra("port", port);
            startActivity(intent);
        });
    }
}
