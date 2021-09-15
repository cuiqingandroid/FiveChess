package com.txxia.game.fivechess;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class MainActivity extends Activity {

    boolean stop = true;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button newGame = (Button) findViewById(R.id.new_game);
        Button fight = (Button) findViewById(R.id.fight);
        Button netFight = (Button) findViewById(R.id.conn_fight);
        newGame.setOnClickListener(new OnClickListener() {
            
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SingleGameActivity.class));
            }
        });
        
        
        fight.setOnClickListener(new OnClickListener() {
            
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, FightGameActivity.class));
            }
        });
        
        netFight.setOnClickListener(new OnClickListener() {
            
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, ConnectionActivity.class));
            }
        });
    }

}
