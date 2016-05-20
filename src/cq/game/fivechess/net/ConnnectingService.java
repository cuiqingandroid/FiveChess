package cq.game.fivechess.net;

import static cq.game.fivechess.net.ConnectConstants.*;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Arrays;

import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

/**
 * 联机管理<br>
 * 初始化这个对象后，调用{@link #start()}方法就会搜索局域网
 * 内的可连接手机，同时自己也会成为别人可见的对象。<br>
 * 当搜索到可联机对象后会返回可连对象的机器名和IP地址。
 * @author cuiqing
 *
 */
public class ConnnectingService {

    public static final String TAG = "ConnnectManager";
    private static final boolean DEBUG = true;
    
    private String mIp;
    
    // UPD接收程序
    private DatagramSocket mDataSocket;
    
    // 点对多广播
    private MulticastSocket mMulticastSocket;
    private InetAddress mCastAddress;
    
    // 广播组地址
    private static final String MUL_IP = "230.0.2.2";
    private static final int MUL_PORT = 1688;
    private static final int UDP_PORT = 2599;
    
    // 接收UPD消息
    private UDPReceiver mUdpReceiver;
    // 接收广播消息
    private MulticastReceiver mMulticastReceiver; 
    // udp消息发送模块
    private UdpSendHandler mUdpSender;
    // 广播消息发送模块
    private MulticastSendHandler mBroadCastSender;
    
    private Handler mRequestHandler;
    
    public ConnnectingService(String ip, Handler request){
        mRequestHandler = request;
        this.mIp = ip;
        mUdpReceiver = new UDPReceiver();
        mMulticastReceiver = new MulticastReceiver();
    }
    
    /**
     * 启动连接程序
     */
    public void start(){
        mUdpReceiver.start();
        mMulticastReceiver.start();
        
        HandlerThread udpThread = new HandlerThread("udpSender");
        udpThread.start();
        mUdpSender = new UdpSendHandler(udpThread.getLooper());
        
        HandlerThread broadcastThread = new HandlerThread("broadcastSender");
        broadcastThread.start();
        mBroadCastSender = new MulticastSendHandler(broadcastThread.getLooper());
    }

    public void stop(){
        mUdpReceiver.quit();
        mMulticastReceiver.quit();
        mUdpSender.getLooper().quit();
        mBroadCastSender.getLooper().quit();
    }
    
    /**
     * 发送一个查询广播消息，查询当前可连接对象
     */
    public void sendScanMsg(){
        Message msg = Message.obtain();
        byte[] buf = packageBroadcast(BROADCAST_JOIN);
        msg.obj = buf;
        mBroadCastSender.sendMessage(msg);
        
    }

    /**
     * 发送一个查询广播消息，退出可联机
     */
    public void sendExitMsg(){
        // 起一个线程发送一个局域网广播(android主线程不能有网络操作)
        // 不用mMulticastSocket对象发送时因为退出的时候涉及跨线程操作
        // 可能mMulticastSocket已经close状态，不可控
        new Thread(){
            public void run() {
                MulticastSocket multicastSocket;
                try {
                    multicastSocket = new MulticastSocket();
                    InetAddress address = InetAddress.getByName(MUL_IP);
                    multicastSocket.setTimeToLive(1);
                    byte[] buf = packageBroadcast(BROADCAST_EXIT);
                    DatagramPacket datagramPacket = new DatagramPacket(buf, buf.length);
                    // 接收地址和group的标识相同
                    datagramPacket.setAddress(address); 
                    // 发送至的端口号
                    datagramPacket.setPort(MUL_PORT); 
                    multicastSocket.send(datagramPacket);
                    multicastSocket.close();
                } catch (IOException e) {
                    Log.d(TAG, "send exit multicast fail:"+e.getMessage());
                } 
            };
        }.start();
    }
    
    /**
     * 发送请求连接消息
     * @param ipDst
     */
    public void sendAskConnect(String ipDst){
        Message msg = Message.obtain();
        Bundle b = new Bundle();
        b.putString("ipDst", ipDst);
        byte[] data = createAskConnect();
        b.putByteArray("data", data);
        msg.setData(b);
        mUdpSender.sendMessage(msg);
    }
    
    /**
     * 发送聊天内容
     * @param content
     * @param ipDst
     */
    public void sendChat(String content, String ipDst){
        Message msg = Message.obtain();
        Bundle b = new Bundle();
        b.putString("ipDst", ipDst);
        byte[] data = createChat(content);
        b.putByteArray("data", data);
        msg.setData(b);
        mUdpSender.sendMessage(msg);
    }
    
    /**
     * 同意联机
     */
    public void accept(String ipDst){
        Message msg = Message.obtain();
        Bundle b = new Bundle();
        b.putString("ipDst", ipDst);
        byte[] data = createConnectResponse(CONNECT_AGREE);
        b.putByteArray("data", data);
        msg.setData(b);
        mUdpSender.sendMessage(msg);
    }
    
    /**
     * 拒绝请求
     */
    public void reject(String ipDst){
        Message msg = Message.obtain();
        Bundle b = new Bundle();
        b.putString("ipDst", ipDst);
        byte[] data = createConnectResponse(CONNECT_REJECT);
        b.putByteArray("data", data);
        msg.setData(b);
        mUdpSender.sendMessage(msg);
    }
    
    /**
     * 接收UDP消息,未建立TCP连接之前，都通过udp接收消息
     * @author qingc
     *
     */
    class UDPReceiver extends Thread {
        
        byte[] buf = new byte[1024];
        boolean isInit = true;
        
        private DatagramSocket dataSocket;
        private DatagramPacket dataPacket;
        
        public UDPReceiver() {
            try {
                dataSocket = new DatagramSocket(UDP_PORT);
                mDataSocket = dataSocket;
                dataPacket = new DatagramPacket(buf, buf.length);
            } catch (SocketException e) {
                isInit = false;
                Log.d(TAG, "Socket Exception:"+e.getMessage());
            }
        }
        
        @Override
        public void run() {
            try {
                while (isInit){
                    mDataSocket.receive(dataPacket);
                    if (DEBUG) Log.d(TAG, "udp received:"+Arrays.toString(buf));
                    int type = buf[0];
                    // 从buffer中截取收到的数据
                    byte[] body = new byte[dataPacket.getLength() - 1];
                    System.arraycopy(buf, 1, body, 0, dataPacket.getLength()-1);
                    switch (type) {
                    case UDP_JOIN:
                        processUdpJoin(body);
                        break;
                    case CONNECT_ASK:
                        processAsk(body);
                        break;
                    case CHAT_ONE:
                        processChat(body);
                        break;
                    case CONNECT_AGREE:
                    case CONNECT_REJECT:
                        processConnectResponse(body, type);
                    default:
                        break;
                    }

                }
            } catch (SocketException e) {
                isInit = false;
                Log.d(TAG, "Socket Exception:"+e.getMessage());
            } catch (IOException e) {
                isInit = false;
                Log.d(TAG, "IOException:"+"an error occurs while receiving the packet");
            } 
        }
        
        public void quit(){
            dataSocket.close();
            isInit = false;
        }
        
    }

    /**
     * 发送UDP消息，未建立TCP连接之前，都通过UDP发送指令到制定的ip
     */
    class UdpSendHandler extends Handler {
        
        public UdpSendHandler(Looper looper) {
            super(looper);
        }
        
        public void handleMessage(Message msg) {
            Bundle b = msg.peekData();
            String ipDst = b.getString("ipDst");
            byte[] data = b.getByteArray("data");
            Log.d(TAG, "udp send destination ip:"+ipDst);
            if (DEBUG) Log.d(TAG, "udp send data:"+Arrays.toString(data));
            if (data == null){
                onError(UDP_DATA_ERROR);
            }
            try {
                DatagramSocket ds;
                ds = mDataSocket;
                if (ds == null){
                    onError(SOCKET_NULL);
                    return;
                }
                InetAddress dstAddress = InetAddress.getByName(ipDst);
                
                // 创建发送数据包
                DatagramPacket dataPacket = new DatagramPacket(data, data.length, dstAddress, UDP_PORT);
                ds.send(dataPacket);
            } catch (UnknownHostException e1) {
                Log.d(TAG, "ip is not corrected");
                onError(UDP_IP_ERROR);
            } catch (IOException e) {
                Log.d(TAG, "udp socket error:" + e.getMessage());
            }

        }
        
        public void quit(){
            getLooper().quit();
        }
        
    }
    
    /**
     * 接收广播消息线程，监听其他手机的扫描或加入广播
     *
     */
    class MulticastReceiver extends Thread{
        
        byte[] buffer = new byte[1024];
        private boolean isInit = true;
        
        private MulticastSocket multiSocket;
        private DatagramPacket dataPacket;
        
        public MulticastReceiver() {
            try {
                multiSocket = new MulticastSocket();
                // 接收数据时需要指定监听的端口号
                multiSocket = new MulticastSocket(MUL_PORT);
                // 加入广播组
                InetAddress address = InetAddress.getByName(MUL_IP);
                mCastAddress = address;
                multiSocket.joinGroup(address);
                multiSocket.setTimeToLive(1);
                dataPacket = new DatagramPacket(buffer, buffer.length);
                // 全局引用指向这里的广播socket,用于发送广播消息
                mMulticastSocket = multiSocket;
            } catch (IOException e) {
                isInit = false;
                Log.d(TAG, "Init mulcast fail by IOException="+e.getMessage());
            }
        }
        
        @Override
        public void run() {
            try {
                while (isInit) {
                    // 接收数据，会进入阻塞状态
                    mMulticastSocket.receive(dataPacket); 
                    // 从buffer中截取收到的数据
                    byte[] message = new byte[dataPacket.getLength()]; 
                    System.arraycopy(buffer, 0, message, 0, dataPacket.getLength());
                    Log.d(TAG, "multicast receive:"+ Arrays.toString(message));
                    String ip = processBroadcast(message);
                    // Check ip address and send ip address myself to it.
                    if (ip != null && !ip.equals(mIp)){
                        Message msg = Message.obtain();
                        Bundle b = new Bundle();
                        b.putString("ipDst", ip);
                        byte[] data = packageUdpJoin();
                        b.putByteArray("data", data);
                        msg.setData(b);
                        mUdpSender.sendMessage(msg);
                    }
                }
            } catch (IOException e) {
                Log.d(TAG, "IOException="+e.getMessage());
            }
        }
        
        public void quit(){
            // close socket
            multiSocket.close();
            isInit = false;
        }
        
    }

    /**
     * 发送广播消息
     */
    class MulticastSendHandler extends Handler {
        
        public MulticastSendHandler(Looper looper) {
            super(looper);
        }
        
        public void handleMessage(Message msg) {
            
            byte[] buf = (byte[]) msg.obj;
            if (DEBUG) Log.d(TAG, "BroadcastSendHandler:data="+buf);
            MulticastSocket s = mMulticastSocket;
            if (s == null){
                onError(SOCKET_NULL);
                return;
            }
            InetAddress address = mCastAddress;
            if (address == null || !address.isMulticastAddress()){
                onError(MULTICAST_ERROR);
                return;
            }
            try {
                // s.setTimeToLive(1);  is it nessary?
                DatagramPacket datagramPacket = new DatagramPacket(buf, buf.length);
                // 设置发送group地址
                datagramPacket.setAddress(address); 
                // 发送至的端口号
                datagramPacket.setPort(MUL_PORT); 
                s.send(datagramPacket);
            } catch (IOException e) {
                Log.d(TAG, "send multicasr fail:"+e.getMessage());
                onError(SOCKET_NULL);
            } 
        }
        
        public void quit(){
            getLooper().quit();
        }
        
    };

    /**
     * 错误信息
     * @param error
     */
    private void onError(int error){
        Log.d(TAG, "error:"+error);
        Message msg = Message.obtain();
        msg.what = error;
        mRequestHandler.sendMessage(msg);
    }
    
    /**
     * 有新的可联机对象加入
     * @param name 机器名
     * @param ip 地址
     */
    private void onJoin(String name, String ip){
        Message msg = Message.obtain();
        msg.what = ON_JOIN;
        Bundle b = new Bundle();
        b.putString("name", name);
        b.putString("ip", ip);
        msg.setData(b);
        mRequestHandler.sendMessage(msg);
    }
    
    /**
     * 有可联机对象退出
     * @param name 机器名
     * @param ip 地址
     */
    private void onExit(String name, String ip){
        Message msg = Message.obtain();
        msg.what = ON_EXIT;
        Bundle b = new Bundle();
        b.putString("name", name);
        b.putString("ip", ip);
        msg.setData(b);
        mRequestHandler.sendMessage(msg);
    }
    
    /**
     * 处理UDP加入可连接对象消息
     * @param data 接收到的消息体
     * @return 返回解析到的ip地址
     */
    private void processUdpJoin(byte[] data){
        int nameLen = data[0];
        int ipLen = data[nameLen+1];
        byte[] nameArr = new byte[nameLen];
        byte[] iparr = new byte[ipLen];
        System.arraycopy(data, 1, nameArr, 0, nameLen);
        System.arraycopy(data, nameLen+2, iparr, 0, ipLen);
        String name = new String(nameArr);
        String ip = new String(iparr);
        Log.d(TAG, "processUdpJoin-->"+"name="+name+"  ip="+ip);
        onJoin(name, ip);
    }
    
    /**
     * 将本机名称和ip地址封装成byte数组
     * @return
     */
    private byte[] packageUdpJoin(){
        byte[] ip = mIp.getBytes();
        byte[] name = (Build.BRAND+"-"+Build.MODEL).getBytes();
        // 消息长度包括(名字、名字长度、ip、ip长度、广播类型) 
        int dataLen = name.length + ip.length + 3;
        byte[] data = new byte[dataLen];
        data[0] = UDP_JOIN;
        int namePos = 1;
        int ipPos = namePos + name.length + 1;
        data[namePos] = (byte) name.length;
        System.arraycopy(name, 0, data, namePos + 1, name.length);
        data[ipPos] = (byte) ip.length;
        System.arraycopy(ip, 0, data, ipPos + 1, ip.length);
        return data;
    }
    
    /**
     * 处理广播消息
     * @param data 接收到的消息体
     * @return 返回解析到的ip地址
     */
    private String processBroadcast(byte[] data){
        int nameLen = data[0];
        int ipLen = data[nameLen+1];
        byte[] nameArr = new byte[nameLen];
        byte[] iparr = new byte[ipLen];
        System.arraycopy(data, 1, nameArr, 0, nameLen);
        System.arraycopy(data, nameLen+2, iparr, 0, ipLen);
        String name = new String(nameArr);
        String ip = new String(iparr);
        Log.d(TAG, "processBroadcast-->"+"name="+name+"  ip="+ip);
        // 如果是自己发送的信息，则不加入可连接集合
        if (ip.equals(mIp)){
            return ip;
        }
        int type = data[data.length - 1];
        if (type == BROADCAST_JOIN){
            onJoin(name, ip);
        } else if (type == BROADCAST_EXIT) {
            onExit(name, ip);
        }
        return ip;
    }
    
    /**
     * 将本机名称和ip地址封装成byte数组
     * @return
     */
    private byte[] packageBroadcast(int type){
        byte[] ip = mIp.getBytes();
        byte[] name = (Build.BRAND+"-"+Build.MODEL).getBytes();
        // 消息长度包括(名字、名字长度、ip、ip长度、广播类型) 
        int dataLen = name.length + 1 + ip.length + 1 + 1;
        byte[] data = new byte[dataLen];
        int namePos = 0;
        int ipPos = name.length + 1;
        data[namePos] = (byte) name.length;
        System.arraycopy(name, 0, data, namePos + 1, name.length);
        data[ipPos] = (byte) ip.length;
        System.arraycopy(ip, 0, data, ipPos + 1, ip.length);
        data[dataLen-1] = (byte) type;
        return data;
    }

    /**
     * 封装请求连接消息体
     * @return data
     */
    private byte[] createAskConnect(){
        byte[] ip = mIp.getBytes();
        byte[] name = (Build.BRAND+"-"+Build.MODEL).getBytes();
        // 消息长度包括(名字、名字长度、ip、ip长度、广播类型) 
        int dataLen = name.length + ip.length + 3;
        byte[] data = new byte[dataLen];
        data[0] = CONNECT_ASK;
        int namePos = 1;
        int ipPos = namePos + name.length + 1;
        data[namePos] = (byte) name.length;
        System.arraycopy(name, 0, data, namePos + 1, name.length);
        data[ipPos] = (byte) ip.length;
        System.arraycopy(ip, 0, data, ipPos + 1, ip.length);
        return data;
    }
    
    /**
     * 解析请求联机数据
     * @param data
     */
    private void processAsk(byte[] data){
        int nameLen = data[0];
        int ipLen = data[nameLen+1];
        byte[] nameArr = new byte[nameLen];
        byte[] iparr = new byte[ipLen];
        System.arraycopy(data, 1, nameArr, 0, nameLen);
        System.arraycopy(data, nameLen+2, iparr, 0, ipLen);
        String name = new String(nameArr);
        String ip = new String(iparr);
        Log.d(TAG, "processUdpJoin-->"+"name="+name+"  ip="+ip);
        Message msg = Message.obtain();
        msg.what = CONNECT_ASK;
        Bundle b = new Bundle();
        b.putString("name", name);
        b.putString("ip", ip);
        msg.setData(b);
        mRequestHandler.sendMessage(msg);
    }
    
    /**
     * 创建聊天内容消息体
     * @return
     */
    private byte[] createChat(String content){
        byte[] ip = mIp.getBytes();
        byte[] name = (Build.BRAND).getBytes();
        byte[] chat = content.getBytes();
        // 消息长度包括(名字、名字长度、ip、ip长度、广播类型、聊天内容、聊天长度) 
        int dataLen = name.length + ip.length + 3 + chat.length + 1;
        byte[] data = new byte[dataLen];
        data[0] = CHAT_ONE;
        int namePos = 1;
        int ipPos = namePos + name.length + 1;
        int chatPos = ipPos + ip.length + 1;
        data[namePos] = (byte) name.length;
        System.arraycopy(name, 0, data, namePos + 1, name.length);
        data[ipPos] = (byte) ip.length;
        System.arraycopy(ip, 0, data, ipPos + 1, ip.length);
        data[chatPos] = (byte) chat.length;
        System.arraycopy(chat, 0, data, chatPos + 1, chat.length);
        return data;
    }
    
    /**
     * 处理聊天内容
     * @param data
     */
    private void processChat(byte[] data){
        int nameLen = data[0];
        int ipLen = data[nameLen+1];
        int chatLen = data[nameLen+ipLen+2];
        byte[] nameArr = new byte[nameLen];
        byte[] iparr = new byte[ipLen];
        byte[] chatArr = new byte[chatLen];
        System.arraycopy(data, 1, nameArr, 0, nameLen);
        System.arraycopy(data, nameLen+2, iparr, 0, ipLen);
        System.arraycopy(data, nameLen+ipLen+3, chatArr, 0, chatLen);
        String name = new String(nameArr);
        String ip = new String(iparr);
        String chat = new String(chatArr);
        Log.d(TAG, "processChat-->"+"name="+name+"  ip="+ip +"  chat="+chat);
        
        
        Message msg = Message.obtain();
        msg.what = CHAT_ONE;
        Bundle b = new Bundle();
        b.putString("name", name);
        b.putString("ip", ip);
        b.putString("chat", chat);
        msg.setData(b);
        mRequestHandler.sendMessage(msg);
    }
    
    /**
     * 创建连接相应消息
     * @param type
     * @return 消息数组
     */
    private byte[] createConnectResponse(int type){
        byte[] ip = mIp.getBytes();
        byte[] name = (Build.BRAND).getBytes();
        // 消息长度包括(名字、名字长度、ip、ip长度、广播类型) 
        int dataLen = name.length + ip.length + 3;
        byte[] data = new byte[dataLen];
        data[0] = (byte) type;
        int namePos = 1;
        int ipPos = namePos + name.length + 1;
        data[namePos] = (byte) name.length;
        System.arraycopy(name, 0, data, namePos + 1, name.length);
        data[ipPos] = (byte) ip.length;
        System.arraycopy(ip, 0, data, ipPos + 1, ip.length);
        return data;
    }
    
    /**
     * 解析连接请求响应并处理
     * @param data
     * @param type
     */
    private void processConnectResponse(byte[] data, int type){
        int nameLen = data[0];
        int ipLen = data[nameLen+1];
        byte[] nameArr = new byte[nameLen];
        byte[] iparr = new byte[ipLen];
        System.arraycopy(data, 1, nameArr, 0, nameLen);
        System.arraycopy(data, nameLen+2, iparr, 0, ipLen);
        String name = new String(nameArr);
        String ip = new String(iparr);
        Log.d(TAG, "processConnectResponse-->"+"name="+name+"  ip="+ip);
        Message msg = Message.obtain();
        msg.what = type;
        Bundle b = new Bundle();
        b.putString("name", name);
        b.putString("ip", ip);
        msg.setData(b);
        mRequestHandler.sendMessage(msg);
    }
}
