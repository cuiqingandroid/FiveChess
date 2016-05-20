package cq.game.fivechess;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import cq.game.fivechess.game.ComputerAI;
import cq.game.fivechess.game.Coordinate;
import cq.game.fivechess.game.Game;
import cq.game.fivechess.game.GameConstants;
import cq.game.fivechess.game.GameView;
import cq.game.fivechess.game.Player;

public class SingleGameActivity extends Activity implements OnClickListener{
    private static final String TAG = "SingleGameActivity";
    
    GameView mGameView = null;
    
    Game mGame;
    Player me;
    Player computer;
    
    ComputerAI ai;
    
    // 胜局
    private TextView mBlackWin;
    private TextView mWhiteWin;
    
    // 当前落子方
    private ImageView mBlackActive;
    private ImageView mWhiteActive;
    
    // 姓名
    private TextView mBlackName;
    private TextView mWhiteName;
    
    // Control Button
    private Button restart;
    private Button rollback;
    private Button setting;
    private Button about;
    
    private boolean isRollback;
    
    /**
     * 处理游戏回调信息，刷新界面
     */
    private Handler mComputerHandler ;

    /**
     * 处理游戏回调信息，刷新界面
     */
    private Handler mRefreshHandler = new Handler(){
        
        public void handleMessage(Message msg) {
            Log.d(TAG, "refresh action="+ msg.what);
            switch (msg.what) {
            case GameConstants.GAME_OVER:
                if (msg.arg1 == Game.BLACK){
                    showWinDialog("黑方胜！");
                    me.win();
                } else if (msg.arg1 == Game.WHITE) {
                    showWinDialog("白方胜！");
                    computer.win();
                } 
                updateScore(me, computer);
                break;
            case GameConstants.ACTIVE_CHANGE:
                updateActive(mGame);
                break;
            case GameConstants.ADD_CHESS:
                updateActive(mGame);
                if (mGame.getActive() == computer.getType()){
                    mComputerHandler.sendEmptyMessage(0);
                }
                break;
            default:
                break;
            }
        };
    };
    
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.game_single);
        initViews();
        initGame();
        initComputer();
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
//        if(keyCode == KeyEvent.KEYCODE_BACK){
//            return true;
//        }
        return super.onKeyDown(keyCode, event);
    }
    
    private void initViews(){
        mGameView = (GameView) findViewById(R.id.game_view);
        mBlackName = (TextView) findViewById(R.id.black_name);
        mBlackWin = (TextView) findViewById(R.id.black_win);
        mBlackActive = (ImageView) findViewById(R.id.black_active);
        mWhiteName = (TextView) findViewById(R.id.white_name);
        mWhiteWin = (TextView) findViewById(R.id.white_win);
        mWhiteActive = (ImageView) findViewById(R.id.white_active);
        restart = (Button) findViewById(R.id.restart);
        rollback = (Button) findViewById(R.id.rollback);
        setting = (Button) findViewById(R.id.setting);
        about = (Button) findViewById(R.id.about);
        restart.setOnClickListener(this);
        rollback.setOnClickListener(this);
        setting.setOnClickListener(this);
        about.setOnClickListener(this);
    }
    
    private void initGame(){
        me = new Player(getString(R.string.myself),Game.BLACK);
        computer = new Player(getString(R.string.computer),Game.WHITE);
        mGame = new Game(mRefreshHandler, me, computer);
        mGame.setMode(GameConstants.MODE_SINGLE);
        mGameView.setGame(mGame);
        updateActive(mGame);
        updateScore(me, computer);
        ai = new ComputerAI(mGame.getWidth(), mGame.getHeight());
    }
    
    private void initComputer(){
        HandlerThread thread = new HandlerThread("computerAi");
        thread.start();
        mComputerHandler = new ComputerHandler(thread.getLooper());
    }
    
    private void updateActive(Game game){
        if (game.getActive() == Game.BLACK){
            mBlackActive.setVisibility(View.VISIBLE);
            mWhiteActive.setVisibility(View.INVISIBLE);
        } else {
            mBlackActive.setVisibility(View.INVISIBLE);
            mWhiteActive.setVisibility(View.VISIBLE);
        }
    }
    
    private void updateScore(Player black, Player white){
        mBlackWin.setText(black.getWin());
        mWhiteWin.setText(white.getWin());
    }
    
    @Override
    protected void onDestroy() {
        mComputerHandler.getLooper().quit();
        super.onDestroy();
    }
    
    private void showWinDialog(String message){
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setCancelable(false);
        b.setMessage(message);
        b.setPositiveButton("继续", new DialogInterface.OnClickListener() {
            
            @Override
            public void onClick(DialogInterface dialog, int which) {
                mGame.reset();
                mGameView.drawGame();
            }
        });
        b.setNegativeButton("退出", new DialogInterface.OnClickListener() {
            
            @Override
            public void onClick(DialogInterface dialog, int which) {
                finish();
            }
        });
        b.show();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
        case R.id.restart:
            mGame.reset();
            updateActive(mGame);
            updateScore(me, computer);
            mGameView.drawGame();
            break;
        case R.id.rollback:
            if(mGame.getActive() != me.getType()){
                isRollback = true;
            } else {
                rollback();
            }
            break;
        case R.id.about:
            
            break;
        case R.id.setting:

            break;
        default:
            break;
        }
        
    }
    
    private void rollback(){
        mGame.rollback();
        mGame.rollback();
        updateActive(mGame);
        mGameView.drawGame();
    }
    
    class ComputerHandler extends Handler{
        
        public ComputerHandler(Looper looper) {
            super(looper);
        }
        
        @Override
        public void handleMessage(Message msg) {
            ai.updateValue(mGame.getChessMap());
            Coordinate c = ai.getPosition(mGame.getChessMap());
            mGame.addChess(c, computer);
            mGameView.drawGame();
            if (isRollback){
                rollback();
                isRollback = false;
            }
        }
        
    }
}
