package com.nexora.download;

import org.json.JSONObject;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] a) throws Exception {
        NP.init("es", "CO");
        String[] vids = {"https://www.youtube.com/watch?v=dQw4w9WgXcQ", "https://www.youtube.com/watch?v=doLMt10ytHY", "https://www.youtube.com/watch?v=kJQP7kiw5Fk"};
        JSONObject out = new JSONObject();
        for (String v : vids) {
            try {
                JSONObject o = NP.info(v);
                out.put(v, new JSONObject().put("hls", o.optString("hls")).put("audio", o.optString("audio")).put("vonly", o.optJSONArray("vonly")));
                System.out.println("OK " + v + " hls=" + !o.optString("hls").isEmpty() + " audioUa=" + o.optString("audioUa"));
            } catch (Throwable e) { System.out.println("FAIL " + v + " " + e); }
        }
        Files.write(Paths.get("../hls.json"), out.toString().getBytes("UTF-8"));
    }
}
