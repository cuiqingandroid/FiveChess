package cq.game.fivechess;

import static cq.game.fivechess.net.ConnectConstants.CHAT_ONE;
import static cq.game.fivechess.net.ConnectConstants.CONNECT_AGREE;
import static cq.game.fivechess.net.ConnectConstants.CONNECT_ASK;
import static cq.game.fivechess.net.ConnectConstants.CONNECT_REJECT;
import static cq.game.fivechess.net.ConnectConstants.ON_EXIT;
import static cq.game.fivechess.net.ConnectConstants.ON_JOIN;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;
import cq.game.fivechess.net.ChatContent;
import cq.game.fivechess.net.ConnectionItem;
import cq.game.fivechess.net.ConnnectingService;

public class ConnectionActivity extends Activity implements OnClickListener
{

    public static final String TAG = "ConnectionActivity";
    private static final boolean DEBUG = true;
    
    private List<ConnectionItem> mConnections = new ArrayList<ConnectionItem>();
    private ListView mListView;
    private ConnectionAdapter mAdapter;
    
    private String mIP;
    private ConnnectingService mCM;
    
    // 联机请求对话框
    private AlertDialog mConnectDialog;
    // 联机请求等待对话框
    private ProgressDialog waitDialog;
    // 显示聊天对话框
    private Dialog mChatDialog;
    private ChatAdapter mChatAdapter;
    private List<ChatContent> mChats = new ArrayList<ChatContent>();
    
    /**
     * 处理网络回调信息，刷新界面
     */
    private Handler mHandler = new Handler(){
        
        public void handleMessage(Message msg) {
            Log.d(TAG, "refresh action="+ msg.what);
            switch (msg.what) {
            case ON_JOIN:
                ConnectionItem add = getConnectItem(msg);
                if (!mConnections.contains(add)) {
                    mConnections.add(add);
                    mAdapter.changeData(mConnections);
                }
                break;
            case ON_EXIT:
                ConnectionItem remove = getConnectItem(msg);
                if (mConnections.contains(remove)) {
                    mConnections.remove(remove);
                    mAdapter.changeData(mConnections);
                }
                break;
            case CONNECT_ASK:
                ConnectionItem ask = getConnectItem(msg);
                showConnectDialog(ask.name, ask.ip);
                break;

            case CHAT_ONE:
                ConnectionItem ci = getConnectItem(msg);

                String chat = msg.peekData().getString("chat");
                ChatContent cc = new ChatContent(ci, chat);
                mChats.add(cc);
                showChatDialog();
                break;
            case CONNECT_AGREE:
                if (waitDialog != null && waitDialog.isShowing()) {
                    waitDialog.dismiss();
                }
                String ip = msg.peekData().getString("ip");
                startGameActivity(false, ip);
                break;
            case CONNECT_REJECT:
                if (waitDialog != null && waitDialog.isShowing()) {
                    waitDialog.dismiss();
                }
                Toast.makeText(ConnectionActivity.this, "对方拒绝了你的请求",
                        Toast.LENGTH_LONG).show();
            default:
                break;
            }
        };
    };
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect);
        initView();
        initNet();
    }
    
    private void initView(){
        Button scan = (Button) findViewById(R.id.scan);
        scan.setOnClickListener(this);
        mListView = (ListView) findViewById(R.id.list);
        mAdapter = new ConnectionAdapter(this, mConnections);
        mListView.setAdapter(mAdapter);
        mListView.setOnItemClickListener(new OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View v, int position,
                    long id) {
                String ipDst = mConnections.get(position).ip;
                mCM.sendAskConnect(ipDst);
                String title = "请求对战";
                String message = "等待"+ipDst+"回应.请稍后....";
                showProgressDialog(title, message);
            }
        });
        // 屏蔽对话功能
//        mListView.setOnItemLongClickListener(new OnItemLongClickListener() {
//
//            @Override
//            public boolean onItemLongClick(AdapterView<?> parent, View v,
//                    int position, long id) {
//                String ipDst = mConnections.get(position).ip;
//                showMenuDialog(ipDst);
//                return true;
//            }
//        });
    }
    
    private void initNet(){
        mIP = getIp();
        if (TextUtils.isEmpty(mIP)){
            Toast.makeText(this, "请检查wifi连接后重试", Toast.LENGTH_LONG).show();
            finish();
        }

    }
    
    @Override
    protected void onStart() {
        super.onStart();
        mCM = new ConnnectingService(mIP, mHandler);
        mCM.start();
        mCM.sendScanMsg();
    }
    
    @Override
    protected void onStop() {
        super.onStop();
        mCM.stop();
        mCM.sendExitMsg();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (DEBUG) Log.d(TAG, "onDestroy");
    }
    
    /**
     * 从消息里面获取数据并生成ConnectionItem对象
     * @param msg
     * @return ConnectionItem
     */
    private ConnectionItem getConnectItem(Message msg){
        Bundle data = msg.peekData();
        String name = data.getString("name");
        String ip = data.getString("ip");
        if (DEBUG) Log.d(TAG, "getFromMsg:name="+name+"  ip="+ip);
        ConnectionItem ci = new ConnectionItem(name, ip);
        return ci;
    }
    
    /**
     * 获取本机的ip地址,通过wifi连接局域网的情况
     * @return ip地址
     */
    private String getIp(){
        WifiManager wm = (WifiManager)getSystemService(Context.WIFI_SERVICE);
        //检查Wifi状态  
        if(!wm.isWifiEnabled()){
            Log.d(TAG, "wifi is not enable,enable wifi first");
            return null;
        }
        WifiInfo wi = wm.getConnectionInfo();
        //获取32位整型IP地址  
        int ipAdd = wi.getIpAddress();
        //把整型地址转换成“*.*.*.*”地址  
        String ip=intToIp(ipAdd);
        Log.d(TAG, "ip:"+ip);
        return ip;
    }
    
    private String intToIp(int i) {
        return (i & 0xFF ) + "." +
        ((i >> 8 ) & 0xFF) + "." +
        ((i >> 16 ) & 0xFF) + "." +
        ( i >> 24 & 0xFF) ;
    } 
    
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
        case R.id.scan:
            mCM.sendScanMsg();
            break;

        default:
            break;
        }
    }
    
    private void startGameActivity(boolean server, String dstIp){
        Intent intent = new Intent(this, NetGameActivity.class);
        Bundle b = new Bundle();
        b.putBoolean("isServer", server);
        b.putString("ip", dstIp);
        intent.putExtras(b);
        startActivity(intent);
    }
    
    private void showConnectDialog(String name, final String ip){
        String msg = name+getString(R.string.fight_request);
        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener(){
            
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                case DialogInterface.BUTTON_POSITIVE:
                    mCM.accept(ip);
                    startGameActivity(true, ip);
                    break;
                case DialogInterface.BUTTON_NEGATIVE:
                    mCM.reject(ip);
                    break;
                default:
                    break;
                }
            }
            
        };
        if (mConnectDialog == null){
            AlertDialog.Builder b = new AlertDialog.Builder(this);
            b.setCancelable(false);
            b.setMessage(msg);
            b.setPositiveButton(R.string.agree,listener);
            b.setNegativeButton(R.string.reject, listener);
            mConnectDialog = b.create();
        } else {
            mConnectDialog.setMessage(msg);
            mConnectDialog.setButton(DialogInterface.BUTTON_POSITIVE,getText(R.string.agree),listener);
            mConnectDialog.setButton(DialogInterface.BUTTON_NEGATIVE,getText(R.string.reject), listener);
        }
        if (!mConnectDialog.isShowing()){
            mConnectDialog.show();
        }
    }
    
    /**
     * 显示聊天内容对话框
     */
    private void showChatDialog(){
        if (mChatDialog == null){
            AlertDialog.Builder b = new AlertDialog.Builder(this);
            b.setIcon(R.drawable.chat);
            b.setTitle("对话");
            View view = getLayoutInflater().inflate(R.layout.chat_dialog, null);
            ListView list = (ListView) view.findViewById(R.id.list_chat);
            mChatAdapter = new ChatAdapter(this, mChats);
            list.setAdapter(mChatAdapter);
            b.setView(view);
            mChatDialog = b.create();
            mChatDialog.show();
        } else {
            if (mChatDialog.isShowing()){
                mChatAdapter.notifyDataSetChanged();
            } else {
                mChatDialog.show();
            }
        }
    }
    
    // Disable chat function 
    @SuppressWarnings("unused")
    private void showMenuDialog(final String ipDst){
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setIcon(R.drawable.chat);
        b.setTitle("对话");
        View view = getLayoutInflater().inflate(R.layout.chat_edit, null);
        final EditText edit = (EditText) view.findViewById(R.id.chat_edit);
        Button send = (Button) view.findViewById(R.id.chat_send);
        send.setOnClickListener(new OnClickListener() {
            
            @Override
            public void onClick(View v) {
                String content = edit.getText().toString();
                if (TextUtils.isEmpty(content)){
                    Toast.makeText(ConnectionActivity.this, "内容不能为空", Toast.LENGTH_SHORT).show();
                } else {
                    edit.setText("");
                    mCM.sendChat(content, ipDst);
                }
            }
        });
        b.setView(view);
        b.show();
    }
    
    private void showProgressDialog(String title, String message){
        if (waitDialog == null){
            waitDialog = new ProgressDialog(this);
        }
        waitDialog.setTitle(title);
        waitDialog.setMessage(message);
        waitDialog.setIndeterminate(true);
        waitDialog.setCancelable(true);
        waitDialog.show();
    }
}
