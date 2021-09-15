package com.txxia.game.fivechess.net;

import java.text.SimpleDateFormat;
import java.util.Date;

public class ChatContent {

    public ConnectionItem connector;
    public String content;
    public String time;

    public static final String FORMAT = "yyyy-M-d HH:mm:ss";
    
    public ChatContent(ConnectionItem ci, String content){
        this.connector = ci;
        this.content = content;
        this.time = new SimpleDateFormat(FORMAT).format(new Date());
    }
    
}
