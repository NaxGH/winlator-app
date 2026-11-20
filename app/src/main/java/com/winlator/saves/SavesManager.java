package com.winlator.saves;

import android.content.Context;
import com.winlator.core.FileUtils;
import org.json.JSONArray;
import org.json.JSONException;
import java.io.File;
import java.util.ArrayList;

public class SavesManager {
    private final File file;
    private final ArrayList<SaveEntry> saves = new ArrayList<>();

    public SavesManager(Context context) {
        file = new File(context.getFilesDir(), "saves.json");
        load();
    }

    public ArrayList<SaveEntry> getSaves() {
        return saves;
    }

    public void add(SaveEntry entry) {
        saves.add(entry);
        save();
    }

    public void remove(SaveEntry entry) {
        saves.remove(entry);
        save();
    }

    public void save() {
        try {
            JSONArray array = new JSONArray();
            for (SaveEntry entry : saves) array.put(entry.toJSON());
            FileUtils.writeString(file, array.toString());
        }
        catch (JSONException e) {}
    }

    private void load() {
        saves.clear();
        if (!file.isFile()) return;
        String text = FileUtils.readString(file);
        if (text.isEmpty()) return;
        try {
            JSONArray array = new JSONArray(text);
            for (int i = 0; i < array.length(); i++) saves.add(SaveEntry.fromJSON(array.getJSONObject(i)));
        }
        catch (JSONException e) {}
    }
}