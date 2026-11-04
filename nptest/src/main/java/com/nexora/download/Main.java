package com.nexora.download;

import org.json.JSONObject;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] a) throws Exception {
        NP.init("es", "CO");
        JSONObject out = new JSONObject();
        long t = System.currentTimeMillis();
        out.put("info", NP.info("https://www.youtube.com/watch?v=dQw4w9WgXcQ"));
        System.out.println("info ms " + (System.currentTimeMillis() - t));
        out.put("search", NP.search("rick astley", 1, 20));
        out.put("comments", NP.comments("https://www.youtube.com/watch?v=dQw4w9WgXcQ", ""));
        try { out.put("channel", NP.channel("https://www.youtube.com/@RickAstleyYT/videos", 1, 12)); System.out.println("channel ok " + out.getJSONObject("channel").optString("title") + " n=" + out.getJSONObject("channel").getJSONArray("items").length()); }
        catch (Throwable e) { System.out.println("channel FAIL " + e); }
        Files.write(Paths.get("../data.json"), out.toString().getBytes("UTF-8"));
    }
}
