package com.wdh.wnfcard;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.support.design.widget.FloatingActionButton;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public class CardListActivity extends AppCompatActivity {

    /**
     * Whether or not the activity is in two-pane mode, i.e. running on a tablet
     * device.
     */
    public String mLastCardID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_card_list);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setTitle(getTitle());

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(CardListActivity.this, NewCardActivity.class));
            }
        });

        if (CardContent.ITEM_MAP.isEmpty())
            CardContent.loadCard(CardListActivity.this);

        SharedPreferences sharedPreferences = getSharedPreferences("wNFCard", Context.MODE_PRIVATE);
        mLastCardID = sharedPreferences.getString("CurrentID", "");
        //Log.i("wNFCard", mLastCardID);

        View recyclerView = findViewById(R.id.card_list);
        assert recyclerView != null;
        setupRecyclerView((RecyclerView) recyclerView);
    }

    private void setupRecyclerView(@NonNull RecyclerView recyclerView) {
        recyclerView.setAdapter(new SimpleItemRecyclerViewAdapter(CardContent.ITEM_MAP));
    }

    public boolean removeCard(String cardID) {
        CardContent.erase(cardID);
        try {
            CardContent.save(this);

            RecyclerView recyclerView = (RecyclerView) findViewById(R.id.card_list);
            assert recyclerView != null;

            setupRecyclerView(recyclerView);

            Toast.makeText(getApplicationContext(), R.string.success,
                    Toast.LENGTH_SHORT).show();
            return true;
        } catch (IOException e) {
            Toast.makeText(getApplicationContext(), e.toString(),
                    Toast.LENGTH_LONG).show();
        }

        return false;
    }

    public void applyCard(String cardID) throws IOException {
        modifyNfcConf(cardID);
        updateNfcConf();
        restartNfcService();

        SharedPreferences sharedPreferences = getSharedPreferences("wNFCard", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("CurrentID", cardID);
        editor.commit();
        mLastCardID = cardID;

        RecyclerView recyclerView = (RecyclerView) findViewById(R.id.card_list);
        setupRecyclerView(recyclerView);
    }

    public void modifyNfcConf(String cardID) throws IOException {
        File file = new File("/etc/libnfc-nxp.conf");
        FileInputStream fis = new FileInputStream(file);
        InputStreamReader inputStreamReader = new InputStreamReader(fis);
        BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
        String line;
        StringBuilder sb = new StringBuilder();
        StringBuilder coresb = new StringBuilder();
        boolean in_core_conf = false;
        while ((line = bufferedReader.readLine()) != null) {
            if (line.startsWith("NXP_DEFAULT_SE=")) {
                sb.append("NXP_DEFAULT_SE=0x00\r\n");
                continue;
            }

            if (line.startsWith("DEFAULT_AID_ROUTE=")) {
                sb.append("DEFAULT_AID_ROUTE=0x00\r\n");
                continue;
            }

            if (line.startsWith("NXP_CORE_CONF=")) {
                in_core_conf = true;

                String[] fields = line.split(",", 4);
                coresb.append(fields[0] + ",");
                coresb.append(fields[1] + ",");
                coresb.append(" ??,");
                coresb.append(fields[3] + "\r\n");
                continue;
            }
            if (in_core_conf) {
                if (line.trim().startsWith("33")) {
                    int start_idx = line.indexOf("33");
                    coresb.append(line.substring(0, start_idx + 2));
                    coresb.append(", 04, ");
                    coresb.append(cardID.replace(" ", ", "));
                    coresb.append(",\r\n");
                    continue;
                }

                if (line.trim().startsWith("}")) {
                    String dat = coresb.toString();
                    int counter = 0;
                    for (int i = 0; i < dat.length(); i++) {
                        if (dat.charAt(i) == ',')
                            counter++;
                    }

                    //Log.d("wNFCard", dat);
                    //Log.d("wNFCard", String.format("Counter: %d", counter));

                    in_core_conf = false;
                    sb.append(dat.replace("??", String.format("%02X", counter - 2)));
                } else {
                    coresb.append(line + "\r\n");
                    continue;
                }
            }

            sb.append(line + "\r\n");
        }
        bufferedReader.close();
        inputStreamReader.close();
        fis.close();

        File dst = new File(getCacheDir(), "libnfc-nxp.conf");
        FileOutputStream fos = new FileOutputStream(dst);
        fos.write(sb.toString().getBytes());
        fos.close();
    }

    private boolean updateNfcConf() {
        ShellUtils.CommandResult result = ShellUtils.execCommand("mount", false);
        if (result.result != 0)
            return false;

        int start_idx = result.successMsg.indexOf("/system");
        if (start_idx == -1)
            return false;
        start_idx = result.successMsg.indexOf("(", start_idx);
        if (start_idx == -1)
            return false;
        int end_idx = result.successMsg.indexOf(")", start_idx);
        if (end_idx == -1)
            return false;
        String options = result.successMsg.substring(start_idx + 1, end_idx);

        String cnf = getCacheDir().getAbsolutePath() + "/libnfc-nxp.conf";

        List<String> commands = new ArrayList<>();
        commands.add("mount -o rw,remount /system");
        commands.add("cp " + cnf + " /etc/libnfc-nxp.conf");
        //commands.add("mount -o " + options + ",remount /system");
        result = ShellUtils.execCommand(commands, true);

        //Log.d("wNFCard", options);
        //Log.d("wNFCard", result.successMsg);

        if (result.result == 0)
            return true;

        return false;
    }

    private boolean restartNfcService() {
        //ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        //am.killBackgroundProcesses("com.android.nfc");

        ShellUtils.CommandResult result = ShellUtils.execCommand("pkill com.android.nfc", true);
        if (result.result == 0)
            return true;

        return false;
    }

    public class SimpleItemRecyclerViewAdapter
            extends RecyclerView.Adapter<SimpleItemRecyclerViewAdapter.ViewHolder> {

        private final List<CardContent.Card> mValues;

        public SimpleItemRecyclerViewAdapter(Map<String, CardContent.Card> items) {
            mValues = new ArrayList<CardContent.Card>(items.values());
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.card_list_content, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(final ViewHolder holder, int position) {
            holder.mItem = mValues.get(position);
            holder.mIdView.setText(mValues.get(position).id);
            holder.mNameView.setText(mValues.get(position).name);

            //Log.d("wNFCard", mLastCardID);
            //Log.d("wNFCard", holder.mItem.toString());

            if (mLastCardID.compareTo(holder.mItem.id) == 0) {
                holder.mApplyView.setBackgroundColor(getResources().getColor(R.color.green, getTheme()));
                holder.mApplyView.setText(R.string.inuse);
            } else {
                holder.mApplyView.setText(R.string.unuse);
                holder.mApplyView.setBackgroundColor(getResources().getColor(R.color.blue, getTheme()));
            }
        }

        @Override
        public int getItemCount() {
            return mValues.size();
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            public final View mView;
            public final TextView mIdView;
            public final TextView mNameView;
            public final TextView mApplyView;
            public final TextView mDelView;

            public final ScrollLinearLayout mLayout;

            public CardContent.Card mItem;

            public ViewHolder(View view) {
                super(view);
                mView = view;
                mIdView = (TextView) view.findViewById(R.id.card_id);
                mNameView = (TextView) view.findViewById(R.id.card_name);
                mApplyView = (TextView) view.findViewById(R.id.use_card);
                mDelView = (TextView) view.findViewById(R.id.del_tv);
                mLayout = (ScrollLinearLayout) itemView.findViewById(R.id.item_recycler_ll);

                View.OnClickListener listener = new View.OnClickListener() {
                    @Override
                    public void onClick(View arg0) {
                        mLayout.beginScroll();
                    }
                };

                //mIdView.setOnClickListener(listener);
                //mNameView.setOnClickListener(listener);
                mLayout.setOnClickListener(listener);

                mApplyView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View arg0) {
                        try {
                            CardListActivity.this.applyCard(mItem.id);
                            Toast.makeText(getApplicationContext(), R.string.success,
                                    Toast.LENGTH_SHORT).show();
                        } catch (IOException e) {
                            Toast.makeText(getApplicationContext(), e.toString(),
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });

                mDelView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View arg0) {
                        CardListActivity.this.removeCard(mItem.id);
                    }
                });

            }

            @Override
            public String toString() {
                return super.toString() + " '" + mItem.toString() + "'";
            }

        }
    }
}
