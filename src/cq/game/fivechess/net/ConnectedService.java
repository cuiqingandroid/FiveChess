package cq.game.fivechess.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import static cq.game.fivechess.net.ConnectConstants.*;

/**
 * 联机传输<br>
 * 传输棋盘信息给对方
 * @author cuiqing
 *
 */
public class ConnectedService {

    public static final String TAG = "ConnnectedService";
    private static final boolean DEBUG = true;
    
    private String mIp;
    
    private Socket mSocket;
    private GameReceiver mReceiver;
    private GameSender mSender;
    private boolean isServer;
    
    private static final int TCP_PORT = 8899;
    
    private Handler mRequestHandler;
    
    public ConnectedService(Handler handler, String ip, boolean isServer){
        mRequestHandler = handler;
        this.isServer = isServer;
        this.mIp = ip;
        mReceiver = new GameReceiver();
        mReceiver.start();
        
        HandlerThread sendThread = new HandlerThread("GameSender");
        sendThread.start();
        mSender = new GameSender(sendThread.getLooper());
    }
    
    /**
     * 下子
     * @param x
     * @param y
     */
    public void addChess(int x, int y){
        byte[] data = new byte[4];
        data[0] = 4;
        data[1] = ConnectConstants.CONNECT_ADD_CHESS;
        data[2] = (byte) x;
        data[3] = (byte) y;
        Message msg = new Message();
        msg.obj = data;
        mSender.sendMessage(msg);
    }
    
    /**
     * 请求悔棋
     */
    public void rollback(){
        byte[] data = new byte[2];
        data[0] = 2;
        data[1] = ConnectConstants.ROLLBACK_ASK;
        Message msg = new Message();
        msg.obj = data;
        mSender.sendMessage(msg);
    }
    
    /**
     * 同意悔棋
     */
    public void agreeRollback(){
        byte[] data = new byte[2];
        data[0] = 2;
        data[1] = ConnectConstants.ROLLBACK_AGREE;
        Message msg = new Message();
        msg.obj = data;
        mSender.sendMessage(msg);
    }
    
    /**
     * 拒绝悔棋
     */
    public void rejectRollback(){
        byte[] data = new byte[2];
        data[0] = 2;
        data[1] = ConnectConstants.ROLLBACK_REJECT;
        Message msg = new Message();
        msg.obj = data;
        mSender.sendMessage(msg);
    }
    
    public void stop(){
        mSender.quit();
        mReceiver.quit();
    }
    
    /**
     * TCP消息接收线程
     */
    class GameReceiver extends Thread {
        
        byte[] buf = new byte[1024];
        boolean isStop = false;
        
        ServerSocket server;
        
        public GameReceiver() {
        }
        
        @Override
        public void run() {
            try {
                if (isServer){
                    server = new ServerSocket(TCP_PORT);
                    mSocket = server.accept();
                    Log.d(TAG, "server:net connected");
                    mRequestHandler.sendEmptyMessage(GAME_CONNECTED);
                } else {
                    Socket s = new Socket();
                    InetSocketAddress addr = new InetSocketAddress(mIp, TCP_PORT);
                    /* 连接失败尝试重连，重试8次
                     * 因为机器性能不一样不能保证作为Server端的Activity先于客户端启动
                     */
                    int retryCount = 0;
                    while (retryCount < 8){
                        try {
                            s.connect(addr);
                            mSocket = s;
                            mRequestHandler.sendEmptyMessage(GAME_CONNECTED);
                            Log.d(TAG, "client:net connected");
                            break;
                        } catch (IOException e) {
                            retryCount++;
                            s = new Socket();
                            try {
                                Thread.sleep(200);
                            } catch (InterruptedException e1) {
                            }
                            Log.d(TAG, "connect exception ："+e.getMessage()+"  retry count="+retryCount);
                        }
                    }
                    if (retryCount >= 8){
                        // TODO
                        return;
                    }
                }
            } catch (IOException e) {
                Log.d(TAG, "socket exception:"+e.getMessage());
                // TODO
                return ;
            } 
            InputStream is;
            try {
                is = mSocket.getInputStream();
                while (!isStop){
                    if (is.read(buf) == -1){
                        // 连接断开
                        break;
                    }
                    if (DEBUG) Log.d(TAG, "tcp received:"+Arrays.toString(buf));
                    int length = buf[0];
                    // 从buffer中截取收到的数据
                    byte[] body = new byte[length];
                    System.arraycopy(buf, 1, body, 0, length);
                    processNetData(body);
                }
            } catch (IOException e) {
                Log.d(TAG, "IOException:"+"an error occurs while receiving data");
                // TODO 提示连接断开
            }

        }
        
        public void quit(){
            try {
                isStop = true;
                if (mSocket != null){
                    mSocket.close();
                }
                if(server != null){
                    server.close();
                }
            } catch (IOException e) {
                Log.d(TAG, "close Socket Exception:"+e.getMessage());
            }
        }
        
    }
    
    /**
     * 把消息交给TCP发送线程发送
     */
    class GameSender extends Handler {
        
        public GameSender(Looper looper) {
            super(looper);
        }
        
        public void handleMessage(Message msg) {
            byte[] data = (byte[]) msg.obj;
            try {
                Socket s = mSocket;
                if (s == null){
//                    onError(SOCKET_NULL);
                    Log.d(TAG, "Send fail,socket is null");
                    return;
                }
                OutputStream os = s.getOutputStream();
                // 发送数据
                os.write(data);
                os.flush();
            } catch (IOException e) {
                Log.d(TAG, "tcp socket error:" + e.getMessage());
//                onError(SOCKET_NULL);
            }

        }
        
        public void quit(){
            getLooper().quit();
        }
        
    };
    
    // 处理消息
    private void processNetData(byte[] data){
        int type = data[0];
        switch (type) {
        case CONNECT_ADD_CHESS:
            notifyAddChess(data[1], data[2]);
            break;
        case ROLLBACK_ASK:
            mRequestHandler.sendEmptyMessage(ROLLBACK_ASK);
            break;
        case ROLLBACK_AGREE:
            mRequestHandler.sendEmptyMessage(ROLLBACK_AGREE);
            break;
        case ROLLBACK_REJECT:
            mRequestHandler.sendEmptyMessage(ROLLBACK_REJECT);
            break;
        default:
            break;
        }
    }
    
    private void notifyAddChess(int x, int y){
        Message msg = Message.obtain();
        msg.what = CONNECT_ADD_CHESS;
        msg.arg1 = x;
        msg.arg2 = y;
        mRequestHandler.sendMessage(msg);
    }
}
