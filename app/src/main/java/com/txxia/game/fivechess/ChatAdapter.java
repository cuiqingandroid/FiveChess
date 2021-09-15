package com.txxia.game.fivechess;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import com.txxia.game.fivechess.net.ChatContent;

public class ChatAdapter extends BaseAdapter{
    private List<ChatContent> mData = new ArrayList<ChatContent>();
    private Context mContext;

    public ChatAdapter(Context context, List<ChatContent> data) {
        mContext = context;
        mData = data;
    }

    @Override
    public int getCount() {
        return mData.size();
    }

    @Override
    public Object getItem(int position) {
        return mData.get(position);
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            LayoutInflater inflate = (LayoutInflater) mContext
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflate.inflate(R.layout.chat_item, null);
            holder = new ViewHolder();
            holder.title = (TextView) convertView.findViewById(R.id.title);
            holder.content = (TextView) convertView.findViewById(R.id.content);
            holder.time = (TextView) convertView.findViewById(R.id.time);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
        int count = getCount() - 1;
        ChatContent item = mData.get(count - position);
        holder.title.setText(item.connector.name+"("+item.connector.ip+")");
        holder.content.setText(item.content);
        holder.time.setText(item.time);
        return convertView;
    }

    public void changeData(List<ChatContent> data){
        mData = data;
        notifyDataSetChanged();
    }
    
    class ViewHolder{
        TextView title;
        TextView content;
        TextView time;
    }
}
