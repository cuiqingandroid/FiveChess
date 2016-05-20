package cq.game.fivechess;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import cq.game.fivechess.game.Game;
import cq.game.fivechess.game.GameConstants;
import cq.game.fivechess.game.GameView;
import cq.game.fivechess.game.Player;
import cq.game.fivechess.net.ConnectedService;
import static cq.game.fivechess.net.ConnectConstants.*;

public class NetGameActivity extends Activity implements OnClickListener{

    private static final String TAG = "GameActivity";
    
    GameView mGameView = null;
    
    Game mGame;
    Player me;
    Player challenger;

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
    private Button requestEqual;
    private Button fail;
    
    // 网络服务
    private ConnectedService mService;
    boolean isServer;
    
    // 连接等待框
    private ProgressDialog waitDialog;
    
    private boolean isRequest ;
    
    /**
     * 处理游戏回调信息，刷新界面
     */
    private Handler mRefreshHandler = new Handler(){
        
        public void handleMessage(Message msg) {
            Log.d(TAG, "refresh action="+ msg.what);
            switch (msg.what) {
            case GameConstants.GAME_OVER:
                if (msg.arg1 == me.getType()){
                    showWinDialog("恭喜你！你赢了！");
                    me.win();
                } else if (msg.arg1 == challenger.getType()) {
                    showWinDialog("很遗憾！你输了！");
                    challenger.win();
                } else {
                    Log.d(TAG, "type="+msg.arg1);
                }
                updateScore(me, challenger);
                break;
            case GameConstants.ADD_CHESS:
                int x = msg.arg1;
                int y = msg.arg2;
                mService.addChess(x, y);
                updateActive(mGame);
                break;
            default:
                break;
            }
        };
    };
    
    /**
     * 处理网络信息，更新界面
     */
    private Handler mRequestHandler = new Handler(){
        
        public void handleMessage(Message msg) {
            Log.d(TAG, "net action="+ msg.what);
            switch (msg.what) {
            case GAME_CONNECTED:
                waitDialog.dismiss();
                break;
            case CONNECT_ADD_CHESS:
                mGame.addChess(msg.arg1, msg.arg2, challenger);
                mGameView.drawGame();
                updateActive(mGame);
                break;
            case ROLLBACK_ASK:
                showRollbackDialog();
                break;
            case ROLLBACK_AGREE:
                Toast.makeText(NetGameActivity.this, "对方同意悔棋", Toast.LENGTH_SHORT).show();
                rollback();
                isRequest = false;
                break;
            case ROLLBACK_REJECT:
                isRequest = false;
                Toast.makeText(NetGameActivity.this, "对方拒绝了你的请求", Toast.LENGTH_LONG).show();
                break;
            default:
                break;
            }
        };
    };
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.game_net);
        mGameView = (GameView) findViewById(R.id.game_view);
        initViews();
        initGame();
    }

    @Override
    protected void onResume() {
        super.onResume();
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
        requestEqual = (Button) findViewById(R.id.requestEqual);
        fail = (Button) findViewById(R.id.fail);
        restart.setOnClickListener(this);
        rollback.setOnClickListener(this);
        requestEqual.setOnClickListener(this);
        fail.setOnClickListener(this);
    }
    
    private void initGame(){
        Bundle b = getIntent().getExtras();
        if (b == null){
            Toast.makeText(this, "建立网络失败,请重试", Toast.LENGTH_SHORT).show();
            finish();
        }
        showProgressDialog(null, "建立连接中，请稍后");
        isServer = b.getBoolean("isServer");
        String ip = b.getString("ip");
        mService = new ConnectedService(mRequestHandler, ip, isServer);
        
        if (isServer){
            me = new Player(Game.BLACK);
            challenger = new Player(Game.WHITE);
            mBlackName.setText(R.string.myself);
            mWhiteName.setText(R.string.challenger);
        } else {
            me = new Player(Game.WHITE);
            challenger = new Player(Game.BLACK);
            mWhiteName.setText(R.string.myself);
            mBlackName.setText(R.string.challenger);
        }
        mGame = new Game(mRefreshHandler, me, challenger);
        mGame.setMode(GameConstants.MODE_NET);
        mGameView.setGame(mGame);
        updateActive(mGame);
        updateScore(me, challenger);
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
    
    private void updateScore(Player me, Player challenger){
        mBlackWin.setText(me.getWin());
        mWhiteWin.setText(challenger.getWin());
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        mService.stop();
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return super.onKeyDown(keyCode, event);
    }
    
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (mGame.getActive() != me.getType()){
            return true;
        }
        if (isRequest){
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }
    
    private void showWinDialog(String message){
        AlertDialog.Builder b = new AlertDialog.Builder(this);
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
            updateScore(me, challenger);
            mGameView.drawGame();
            break;
        case R.id.rollback:
            mService.rollback();
            isRequest = true;
            break;
        case R.id.requestEqual:
            
            break;
        case R.id.fail:

            break;
        default:
            break;
        }
    }
    
    private void rollback(){
        if (mGame.getActive() == me.getType()){
            mGame.rollback();
        }
        mGame.rollback();
        updateActive(mGame);
        mGameView.drawGame();
    }
    
    // 显示等待框
    private void showProgressDialog(String title, String message){
        if (waitDialog == null){
            waitDialog = new ProgressDialog(this);
        }
        if (!TextUtils.isEmpty(title)){
            waitDialog.setTitle(title);
        }
        waitDialog.setMessage(message);
        waitDialog.setIndeterminate(true);
        waitDialog.setCancelable(true);
        waitDialog.show();
    }
    
    private void showRollbackDialog(){
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setMessage("是否同意对方悔棋");
        b.setCancelable(false);
        b.setPositiveButton(R.string.agree, new DialogInterface.OnClickListener() {
            
            @Override
            public void onClick(DialogInterface dialog, int which) {
                mService.agreeRollback();
                rollback();
            }
        });
        b.setNegativeButton(R.string.reject, new DialogInterface.OnClickListener() {
            
            @Override
            public void onClick(DialogInterface dialog, int which) {
                mService.rejectRollback();
            }
        });
        b.show();
    }
    
}