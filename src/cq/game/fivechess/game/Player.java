package cq.game.fivechess.game;

public class Player {

    String mName;
    // 白子还是黑子
    int type;
    // 胜局
    int mWin;
    // 败局
    int mLose;
    
    public Player(String name, int type){
        this.mName = name;
        this.type = type;
    }
    
    public Player(int type){
        if (type == Game.WHITE){
            this.mName = "White";
        } else if (type == Game.BLACK){
            this.mName = "Black";
        }
        this.type = type;
    }
    
    public int getType(){
        return this.type;
    }
    
    /**
     * 胜一局
     */
    public void win(){
        mWin += 1;
    }

    public String getWin(){
        return String.valueOf(mWin);
    }
    
    /**
     * 负一局
     */
    public void lose(){
        mLose += 1;
    }
}
