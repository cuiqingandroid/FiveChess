package com.txxia.game.fivechess.net;

public class ConnectionItem {

    public String name;
    public String ip;
    
    public ConnectionItem(String name, String ip) {
        this.name = name;
        this.ip = ip;
    }

    @Override
    public int hashCode() {
        return 0;
    }
    
    @Override
    public boolean equals(Object o) {
        ConnectionItem other = (ConnectionItem) o;
        return this.ip.equals(other.ip);
    }
    
}
