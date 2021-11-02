package net.froemling.bsremote.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import net.froemling.bsremote.LogThread;
import net.froemling.bsremote.R;

import java.util.LinkedList;

public class LibraryAdapter extends BaseAdapter {
  final LinkedList<String> knownGames = new LinkedList<>();
  private final ScanActivity scanActivity;
  Context context;
  LayoutInflater inflater;
  View headerView;

  @SuppressLint("InflateParams")
  public LibraryAdapter(ScanActivity scanActivity, Context context) {
    this.scanActivity = scanActivity;
    this.context = context;
    this.inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    this.headerView = inflater.inflate(R.layout.item_network, null, false);
  }

  void notifyLost(String gameName) {
    if (Looper.getMainLooper().getThread() != Thread.currentThread()) {
      throw new AssertionError();
    }
    if (knownGames.contains(gameName)) {
      knownGames.remove(gameName);
      notifyDataSetChanged();
    }
  }

  public void notifyFound(String gameName) {
    if (Looper.getMainLooper().getThread() != Thread.currentThread()) {
      throw new AssertionError();
    }
    if (!knownGames.contains(gameName)) {
      knownGames.add(gameName);
      notifyDataSetChanged();
    }
  }

  public Object getItem(int position) {
    if (!knownGames.isEmpty()) {
      return knownGames.get(position);
    } else {
      return null;
    }
  }

  @Override
  public boolean hasStableIds() {
    return true;
  }

  public int getCount() {
    return knownGames.size();
  }

  public long getItemId(int position) {
    return position;
  }

  @SuppressLint("SetTextI18n")
  public View getView(int position, View convertView, ViewGroup parent) {
    if (convertView == null) {
      convertView = inflater.inflate(android.R.layout.simple_list_item_1, parent, false);
      TextView tv = convertView.findViewById(android.R.id.text1);
      tv.setPadding(40, 0, 0, 0);
      tv.setTextColor(0xFFCCCCFF);
    }
    try {
      String gameName = (String) this.getItem(position);
      TextView tv = convertView.findViewById(android.R.id.text1);
      tv.setText(gameName);

    } catch (Exception e) {
      LogThread.log("Problem getting zero-conf info", e, scanActivity);
      ((TextView) convertView.findViewById(android.R.id.text1)).setText("Unknown");
    }
    return convertView;
  }
}
