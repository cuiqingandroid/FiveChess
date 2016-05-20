package cq.game.fivechess.game;

/**
 * 坐标类
 * @author cuiqing
 */
public class Coordinate {
    public int x;
    public int y;

    public Coordinate(){
        
    }
    
    public Coordinate(int x, int y) {
        this.x = x;
        this.y = y;
    }
    
    public void set(int x, int y){
        this.x = x;
        this.y = y;
    }
    
}
