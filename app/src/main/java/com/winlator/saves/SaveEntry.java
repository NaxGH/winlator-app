package com.winlator.saves;

import org.json.JSONException;
import org.json.JSONObject;

public class SaveEntry {
    public final long id;
    public String name;
    public int containerId;
    public String relativePath;

    public SaveEntry(long id, String name, int containerId, String relativePath) {
        this.id = id;
        this.name = name;
        this.containerId = containerId;
        this.relativePath = relativePath;
    }

    public JSONObject toJSON() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", name);
        o.put("containerId", containerId);
        o.put("relativePath", relativePath);
        return o;
    }

    public static SaveEntry fromJSON(JSONObject o) throws JSONException {
        return new SaveEntry(o.getLong("id"), o.getString("name"), o.getInt("containerId"), o.getString("relativePath"));
    }
}