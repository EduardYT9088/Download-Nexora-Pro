package com.nexora.download;

import org.json.JSONArray;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;

public class Main {
    static final String WEBVIEW_UA = "Mozilla/5.0 (Linux; Android 14; 23117RA68G Build/UKQ1.230804.001; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/130.0.6723.107 Mobile Safari/537.36";
    static void probe(String label, String url, String ua) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestProperty("User-Agent", ua);
            c.setRequestProperty("Range", "bytes=0-");
            c.setConnectTimeout(10000); c.setReadTimeout(10000);
            int code = c.getResponseCode();
            System.out.println("   PROBE " + label + " -> " + code + " len=" + c.getHeaderField("Content-Length") + " type=" + c.getContentType());
            c.disconnect();
        } catch (Exception e) { System.out.println("   PROBE " + label + " ERR " + e); }
    }
    public static void main(String[] a) throws Exception {
        NP.init("es", "CO");
        String[] vids = {"https://www.youtube.com/watch?v=doQoKPF7O9M", "https://www.youtube.com/watch?v=dQw4w9WgXcQ", "https://www.youtube.com/watch?v=jNQXAC9IVRw"};
        for (String v : vids) {
            for (int round = 0; round < 2; round++) {
                long t = System.currentTimeMillis();
                try {
                    JSONObject o = NP.info(v);
                    System.out.println("INFO " + v + " round" + round + " " + (System.currentTimeMillis() - t) + "ms title=" + o.optString("title") + " up=" + o.optString("uploader")
                        + " views=" + o.optLong("views") + " likes=" + o.optLong("likes") + " subs=" + o.optLong("subs") + " date=" + o.optString("date") + " dur=" + o.optLong("duration"));
                    System.out.println("   thumb=" + o.optString("thumb") + " avatar=" + o.optString("avatar").length());
                    System.out.println("   streams=" + o.optJSONArray("streams").toString().replaceAll("\"url\":\"[^\"]*\"", "\"url\":…"));
                    System.out.println("   vonly=" + o.optJSONArray("vonly").length() + " auds=" + o.optJSONArray("auds").length() + " hls=" + (o.optString("hls").isEmpty() ? "no" : "yes") + " audioUa=" + o.optString("audioUa"));
                    JSONArray fm = o.getJSONArray("formats"); StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < fm.length(); i++) { JSONObject f = fm.getJSONObject(i); sb.append(f.optInt("h")).append(f.optBoolean("v") ? "v" : "").append(f.optBoolean("a") ? "a" : "").append(":").append(f.optLong("s") / 1024).append("K ").append(f.optString("e")).append(" | "); }
                    System.out.println("   formats=" + sb);
                    System.out.println("   desc[0..300]=" + o.optString("desc").substring(0, Math.min(300, o.optString("desc").length())).replace("\n", "⏎"));
                    System.out.println("   subtitles=" + o.optJSONArray("subtitles").length() + " related=" + o.optJSONArray("related").length());
                    JSONArray rel = o.getJSONArray("related");
                    for (int i = 0; i < Math.min(6, rel.length()); i++) { JSONObject r = rel.getJSONObject(i); System.out.println("     rel " + r.optString("kind") + " " + r.optString("title") + " thumb=" + (r.optString("thumb").isEmpty() ? "NONE" : "ok") + " " + r.optString("url")); }
                    if (round == 0) {
                        JSONArray st = o.getJSONArray("streams");
                        for (int i = 0; i < st.length(); i++) { JSONObject s = st.getJSONObject(i); probe(s.optInt("h") + "p webviewUA", s.optString("url"), WEBVIEW_UA); probe(s.optInt("h") + "p ownUA", s.optString("url"), s.optString("ua")); }
                        if (!o.optString("audio").isEmpty()) { probe("audio webviewUA", o.optString("audio"), WEBVIEW_UA); probe("audio ownUA", o.optString("audio"), o.optString("audioUa")); }
                        if (o.optJSONArray("vonly").length() > 0) probe("vonly0 webviewUA", o.getJSONArray("vonly").getJSONObject(0).optString("url"), WEBVIEW_UA);
                        if (!o.optString("hls").isEmpty()) probe("hls webviewUA", o.optString("hls"), WEBVIEW_UA);
                        JSONArray subs = o.getJSONArray("subtitles");
                        if (subs.length() > 0) probe("sub0", subs.getJSONObject(0).optString("url"), WEBVIEW_UA);
                    }
                } catch (Throwable e) { System.out.println("INFO " + v + " FAIL " + (System.currentTimeMillis() - t) + "ms " + e); e.printStackTrace(System.out); }
            }
        }
        for (String q : new String[]{"bad bunny", "musica"}) {
            long t = System.currentTimeMillis();
            try {
                JSONObject o = NP.search(q, 1, 20);
                System.out.println("SEARCH " + q + " " + (System.currentTimeMillis() - t) + "ms n=" + o.getJSONArray("items").length() + " more=" + o.optBoolean("more"));
                JSONArray it = o.getJSONArray("items");
                for (int i = 0; i < it.length(); i++) { JSONObject r = it.getJSONObject(i); System.out.println("   " + r.optString("kind") + " | " + r.optString("title") + " | " + r.optString("channel") + " | views=" + r.optLong("views") + " dur=" + r.optLong("duration") + " count=" + r.optLong("count") + " thumb=" + r.optString("thumb")); }
                t = System.currentTimeMillis();
                JSONObject o2 = NP.search(q, 21, 20);
                System.out.println("SEARCH page2 " + (System.currentTimeMillis() - t) + "ms n=" + o2.getJSONArray("items").length());
            } catch (Throwable e) { System.out.println("SEARCH FAIL " + e); e.printStackTrace(System.out); }
        }
        try {
            long t = System.currentTimeMillis();
            JSONObject c = NP.comments("https://www.youtube.com/watch?v=dQw4w9WgXcQ", "");
            System.out.println("COMMENTS " + (System.currentTimeMillis() - t) + "ms n=" + c.getJSONArray("items").length() + " count=" + c.optInt("count") + " next=" + !c.optString("next").isEmpty());
            for (int i = 0; i < Math.min(3, c.getJSONArray("items").length()); i++) System.out.println("   " + c.getJSONArray("items").getJSONObject(i));
            JSONObject c2 = NP.comments("https://www.youtube.com/watch?v=dQw4w9WgXcQ", c.optString("next"));
            System.out.println("COMMENTS page2 n=" + c2.getJSONArray("items").length());
        } catch (Throwable e) { System.out.println("COMMENTS FAIL " + e); e.printStackTrace(System.out); }
        for (String pl : new String[]{"https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=RDdQw4w9WgXcQ", "https://www.youtube.com/playlist?list=PLFgquLnL59alCl_2TQvOiD5Vgm1hCaGSI"}) {
            try {
                long t = System.currentTimeMillis();
                JSONObject p = NP.playlist(pl, 1, 24);
                System.out.println("PLAYLIST " + (System.currentTimeMillis() - t) + "ms title=" + p.optString("title") + " count=" + p.optLong("count") + " n=" + p.getJSONArray("items").length() + " more=" + p.optBoolean("more") + " thumb=" + p.optString("thumb"));
            } catch (Throwable e) { System.out.println("PLAYLIST FAIL " + e); e.printStackTrace(System.out); }
        }
    }
}
