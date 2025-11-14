package com.example.client;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ChatRoom extends AppCompatActivity {

    private List<Msg> msgList;
    private EditText inputText;
    private Button send;
    private Button edit;
    private RecyclerView msgRecyclerView;
    private TextView connectionStatus; // 【新增】状态文本视图
    private MsgAdapter adapter;

    private String name;
    private String ip;
    private String port;

    private Socket socket = null;
    private OutputStream sendOutput;
    private InputStream receiveInput;

    // 【修改】使用ScheduledExecutorService来处理定时心跳任务和普通网络任务
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private static final String HEARTBEAT_SIGNAL = "heartbeat-heartbeat";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.chatroom);

        initViews();
        initRecyclerView();

        Intent intent = getIntent();
        name = intent.getStringExtra("name");
        ip = intent.getStringExtra("ip");
        port = intent.getStringExtra("port");

        // 在后台线程建立网络连接
        scheduler.execute(this::connectToServer);

        setupClickListeners();
    }

    private void initViews() {
        inputText = findViewById(R.id.input_text);
        send = findViewById(R.id.send);
        edit = findViewById(R.id.edit_up);
        msgRecyclerView = findViewById(R.id.msg_recycle_view);
        connectionStatus = findViewById(R.id.connection_status); // 【新增】获取状态视图实例

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
    }

    // ... initRecyclerView() 方法保持不变 ...
    private void initRecyclerView() {
        msgList = new ArrayList<>();
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        msgRecyclerView.setLayoutManager(layoutManager);
        adapter = new MsgAdapter(msgList);
        msgRecyclerView.setAdapter(adapter);
    }

    private void connectToServer() {
        try {
            socket = new Socket(ip, Integer.parseInt(port));
            // 启用SO_KEEPALIVE，但自定义心跳更可靠
            socket.setKeepAlive(true);
            sendOutput = socket.getOutputStream();
            receiveInput = socket.getInputStream();

            runOnUiThread(() -> {
                Toast.makeText(this, "登录成功", Toast.LENGTH_SHORT).show();
                updateConnectionStatus(true, "连接正常");
                startListening();
                // 【新增】启动心跳任务
                startHeartbeat();
            });
        } catch (Exception e) {
            e.printStackTrace();
            runOnUiThread(() -> {
                Toast.makeText(this, "连接服务器失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                updateConnectionStatus(false, "连接失败");
                finish();
            });
        }
    }

    private void startListening() {
        // ... (省略部分代码，逻辑基本不变)
        new Thread(() -> {
            try {
                byte[] buffer = new byte[1024 * 4];
                int len;
                while (socket != null && !socket.isClosed() && (len = receiveInput.read(buffer)) != -1) {
                    String receivedMsg = new String(buffer, 0, len);
                    // 【修改】如果收到的是心跳回声，则进行处理，避免在聊天界面显示

                    if (HEARTBEAT_SIGNAL.equals(receivedMsg.trim())||"".equals(receivedMsg.trim())) {
                        continue;
                    }
                    final Msg msg = new Msg(receivedMsg.trim(), Msg.TYPE_RECEIVED);
                    runOnUiThread(() -> {
                        msgList.add(msg);
                        adapter.notifyItemInserted(msgList.size() - 1);
                        msgRecyclerView.scrollToPosition(msgList.size() - 1);
                    });
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                // 【修改】当监听循环退出时，说明连接已断开
                runOnUiThread(() -> {
                    if (!isFinishing()) {
                        updateConnectionStatus(false, "连接已断开");
                        Toast.makeText(ChatRoom.this, "与服务器的连接已断开", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).start();
    }

    // 【新增】启动心跳的方法
    private void startHeartbeat() {
        // 每隔15秒执行一次心跳任务
        scheduler.scheduleAtFixedRate(() -> {
            if (socket != null && !socket.isClosed() && socket.isConnected()) {
                try {
                    // 发送一个心跳包
                    sendOutput.write(HEARTBEAT_SIGNAL.getBytes());
                    sendOutput.flush();
                } catch (IOException e) {
                    // 心跳发送失败，说明连接可能已经断开
                    e.printStackTrace();
                    runOnUiThread(() -> updateConnectionStatus(false, "连接异常"));
                }
            }
        }, 15, 15, TimeUnit.SECONDS);
    }

    // 【新增】更新UI状态的方法
    private void updateConnectionStatus(boolean isConnected, String status) {
        if (isConnected) {
            connectionStatus.setText(status);
            connectionStatus.setTextColor(Color.parseColor("#00C853")); // 绿色
        } else {
            connectionStatus.setText(status);
            connectionStatus.setTextColor(Color.parseColor("#D50000")); // 红色
        }
    }

    private void sendMessage(final String message) {
        scheduler.execute(() -> {
            try {
                if (sendOutput != null) {
                    sendOutput.write(message.getBytes());
                    sendOutput.flush();
                    runOnUiThread(() -> {
                        Msg msg = new Msg(message, Msg.TYPE_SENT);
                        msgList.add(msg);
                        adapter.notifyItemInserted(msgList.size() - 1);
                        msgRecyclerView.scrollToPosition(msgList.size() - 1);
                        inputText.setText("");
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(ChatRoom.this, "发送失败", Toast.LENGTH_SHORT).show();
                    updateConnectionStatus(false, "连接异常");
                });
            }
        });
    }


    private void setupClickListeners() {
        // ... (退出按钮逻辑不变)
        edit.setOnClickListener(v -> showExitDialog());
        // 【修改】发送按钮的逻辑
        send.setOnClickListener(view -> {
            String content = inputText.getText().toString().trim();
            if (!content.isEmpty()) {
                @SuppressLint("SimpleDateFormat")
                String date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                sendMessage(content);
            }
        });
    }

    // ... showExitDialog() 和 onKeyDown() 保持不变 ...

    private void showExitDialog() {
        new AlertDialog.Builder(this)
                .setTitle("提示")
                .setMessage("确定要退出登录吗？")
                .setPositiveButton("确定", (dialog, which) -> {
                    // 关闭Socket连接，清理资源
                    try {
                        if (socket != null) {
                            socket.close();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    // 关闭当前Activity
                    finish();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            showExitDialog();
            return true; // 消费掉这个事件，防止默认的返回行为
        }
        return super.onKeyDown(keyCode, event);
    }



    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 【修改】关闭所有后台任务和网络连接
        scheduler.shutdownNow(); // 立即停止所有任务
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
