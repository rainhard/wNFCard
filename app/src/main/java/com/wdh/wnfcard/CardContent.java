package com.wdh.wnfcard;

import android.content.Context;
import android.support.v4.util.ArrayMap;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Iterator;
import java.util.Map;
import java.io.FileInputStream;


public class CardContent {

    private static String SAVE_FILE = "cards.txt";

    /**
     * A map of card items, by ID.
     */
    public static final Map<String, CardContent.Card> ITEM_MAP = new ArrayMap<>();


    public static void addCard(CardContent.Card card) {
        ITEM_MAP.put(card.id, card);
    }

    public static int loadCard(Context ctx) {
        try {
            FileInputStream fis = ctx.openFileInput(SAVE_FILE);
            InputStreamReader inputStreamReader = new InputStreamReader(fis);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                int idx = line.indexOf('=');
                if (idx == -1 || idx != 11)
                    continue;

                String id = line.substring(0, 11);
                String name = line.substring(12);
                addCard(new CardContent.Card(id, name, ""));
            }
            bufferedReader.close();
            inputStreamReader.close();
            fis.close();
        } catch (Exception e) {
            //e.printStackTrace();
            return -1;
        }

        return ITEM_MAP.size();
    }

    public static void save(Context ctx) throws IOException {
        String res = "";
        Iterator<Map.Entry<String, CardContent.Card>> it = ITEM_MAP.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, CardContent.Card> entry = it.next();
            res += entry.getValue().toString() + "\n";
        }

        FileOutputStream fos = ctx.openFileOutput(SAVE_FILE, Context.MODE_PRIVATE);
        fos.write(res.getBytes());
        fos.close();
    }

    public static void erase(String id) {
        ITEM_MAP.remove(id);
    }


    /**
     * item representing a card.
     */
    public static class Card {
        public final String id;
        public final String name;
        public final String details;

        public Card(String id, String name, String details) {
            this.id = id;
            this.name = name;
            this.details = details;
        }

        @Override
        public String toString() {
            return id + "=" + name;
        }
    }
}
