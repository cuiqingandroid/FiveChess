package com.txxia.game.fivechess;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import com.txxia.game.fivechess.net.ConnectionItem;

public class ConnectionAdapter extends BaseAdapter {

    private List<ConnectionItem> mData = new ArrayList<ConnectionItem>();
    private Context mContext;

    public ConnectionAdapter(Context context, List<ConnectionItem> data) {
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
            convertView = inflate.inflate(R.layout.list_item, null);
            holder = new ViewHolder();
            holder.name = (TextView) convertView.findViewById(R.id.name);
            holder.ip = (TextView) convertView.findViewById(R.id.ip);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
        ConnectionItem item = mData.get(position);
        holder.name.setText(item.name);
        holder.ip.setText(item.ip);
        return convertView;
    }

    public void changeData(List<ConnectionItem> data){
        mData = data;
        notifyDataSetChanged();
    }
    
    class ViewHolder{
        TextView name;
        TextView ip;
    }
}
