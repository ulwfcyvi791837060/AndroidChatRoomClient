package com.example.client;

import android
        .annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

// 1. 【修复】迁移到 AndroidX
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
// 2. 【修复】引入线程池来处理后台任务
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatRoom extends AppCompatActivity {

    private List<Msg> msgList;
    private EditText inputText;
    private Button send;
    private Button edit;
    private RecyclerView msgRecyclerView;
    private MsgAdapter adapter;

    private String name;
    private String ip;
    private String port;

    private Socket socket = null;
    private OutputStream sendOutput;
    private InputStream receiveInput;

    // 使用单线程的线程池来处理所有网络写操作，确保顺序执行
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.chatroom);

        // 初始化视图
        initViews();
        // 初始化 RecyclerView
        initRecyclerView();

        // 获取登录信息
        Intent intent = getIntent();
        name = intent.getStringExtra("name");
        ip = intent.getStringExtra("ip");
        port = intent.getStringExtra("port");

        // 2. 【修复】在后台线程建立网络连接
        executorService.execute(this::connectToServer);

        // 设置点击事件监听
        setupClickListeners();
    }

    private void initViews() {
        inputText = findViewById(R.id.input_text);
        send = findViewById(R.id.send);
        edit = findViewById(R.id.edit_up);
        msgRecyclerView = findViewById(R.id.msg_recycle_view);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
    }

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
            sendOutput = socket.getOutputStream();
            receiveInput = socket.getInputStream();
            // 连接成功，切换回主线程提示用户并开始监听消息
            runOnUiThread(() -> {
                Toast.makeText(this, "登录成功", Toast.LENGTH_SHORT).show();
                startListening(); // 连接成功后才开始监听
            });
        } catch (Exception e) {
            e.printStackTrace();
            // 连接失败，切换回主线程提示用户并关闭页面
            runOnUiThread(() -> {
                Toast.makeText(this, "连接服务器失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                finish();
            });
        }
    }

    private void startListening() {
        // 创建一个专门的后台线程来持续接收消息
        new Thread(() -> {
            try {
                byte[] buffer = new byte[1024 * 4];
                int len;
                // 当 socket 未关闭且能读到数据时，循环执行
                while (socket != null && !socket.isClosed() && (len = receiveInput.read(buffer)) != -1) {
                    String receivedMsg = new String(buffer, 0, len);
                    final Msg msg = new Msg(receivedMsg, Msg.TYPE_RECEIVED);
                    // 收到消息，切换回主线程更新UI
                    runOnUiThread(() -> {
                        msgList.add(msg);
                        adapter.notifyItemInserted(msgList.size() - 1);
                        msgRecyclerView.scrollToPosition(msgList.size() - 1);
                    });
                }
            } catch (IOException e) {
                // 通常是 socket 关闭或网络断开时会进入这里
                e.printStackTrace();
                runOnUiThread(() -> {
                    if (!isFinishing()) {
                        Toast.makeText(ChatRoom.this, "与服务器的连接已断开", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).start();
    }

    private void setupClickListeners() {
        // 退出按钮
        edit.setOnClickListener(v -> showExitDialog());

        // 发送按钮
        send.setOnClickListener(view -> {
            String content = inputText.getText().toString().trim();
            if (!content.isEmpty()) {
                @SuppressLint("SimpleDateFormat")
                String date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                String messageToSend = content + "\n\n来自：" + name + "\n" + date;

                // 2. 【修复】在后台线程发送消息
                executorService.execute(() -> {
                    try {
                        if (sendOutput != null) {
                            sendOutput.write(messageToSend.getBytes());
                            sendOutput.flush(); // 确保消息立即发送出去

                            // 发送成功，切换回主线程更新UI
                            runOnUiThread(() -> {
                                Msg msg = new Msg(messageToSend, Msg.TYPE_SENT);
                                msgList.add(msg);
                                adapter.notifyItemInserted(msgList.size() - 1);
                                msgRecyclerView.scrollToPosition(msgList.size() - 1);
                                inputText.setText(""); // 清空输入框
                            });
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        // 发送失败，切换回主线程提示用户
                        runOnUiThread(() -> Toast.makeText(ChatRoom.this, "发送失败", Toast.LENGTH_SHORT).show());
                    }
                });
            }
        });
    }

    private void showExitDialog() {
        new AlertDialog.Builder(this)
                .setTitle("退出")
                .setMessage("确定退出登录吗？")
                .setPositiveButton("确定", (dialog, which) -> finish())
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // 拦截返回键，弹出退出对话框
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            showExitDialog();
            return true; // 表示事件已处理，不再向后传递
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 3. 【修复】在页面销毁时，关闭网络连接和线程池，释放资源
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        executorService.shutdown();
    }
}
