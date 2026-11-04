package com.nexora.download;

import org.json.JSONArray;
import org.json.JSONObject;
import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.channel.ChannelInfoItem;
import org.schabi.newpipe.extractor.comments.CommentsInfo;
import org.schabi.newpipe.extractor.comments.CommentsInfoItem;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.playlist.PlaylistInfo;
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.Description;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.SubtitlesStream;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * Motor rápido (NewPipe Extractor, Java puro): información de videos, búsqueda,
 * comentarios y playlists en 1-2 segundos, sin arrancar Python.
 * yt-dlp sigue como respaldo y para las descargas.
 */
public final class NP {
    private NP() {}

    private static volatile boolean ready = false;
    private static final StreamingService YT = ServiceList.YouTube;

    public static synchronized void init(String lang, String country) {
        try {
            String l = (lang == null || lang.isEmpty()) ? "es" : lang;
            String c = (country == null || country.isEmpty()) ? "US" : country.toUpperCase();
            NewPipe.init(new Http(), new Localization(l, c), new ContentCountry(c));
            ready = true;
        } catch (Throwable e) {
            ready = false;
        }
    }

    public static boolean ready() { return ready; }

    // ================= descargador HTTP para el extractor =================
    static final class Http extends Downloader {
        static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0";

        @Override
        public Response execute(Request request) throws IOException, ReCaptchaException {
            String url = request.url();
            HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
            try {
                con.setConnectTimeout(10000);
                con.setReadTimeout(15000);
                con.setInstanceFollowRedirects(true);
                con.setRequestMethod(request.httpMethod());
                con.setRequestProperty("User-Agent", UA);
                con.setRequestProperty("Accept-Encoding", "gzip");
                for (Map.Entry<String, List<String>> h : request.headers().entrySet()) {
                    List<String> vs = h.getValue();
                    if (vs == null || vs.isEmpty()) continue;
                    con.setRequestProperty(h.getKey(), String.join(", ", vs));
                }
                byte[] data = request.dataToSend();
                if (data != null) {
                    con.setDoOutput(true);
                    con.setFixedLengthStreamingMode(data.length);
                    try (OutputStream os = con.getOutputStream()) { os.write(data); }
                }
                int code = con.getResponseCode();
                if (code == 429) throw new ReCaptchaException("reCaptcha Challenge requested", url);
                InputStream in = code >= 400 ? con.getErrorStream() : con.getInputStream();
                String body = "";
                if (in != null) {
                    if ("gzip".equalsIgnoreCase(con.getContentEncoding())) in = new GZIPInputStream(in);
                    try (InputStream is = in) {
                        ByteArrayOutputStream bo = new ByteArrayOutputStream();
                        byte[] buf = new byte[16384]; int n;
                        while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
                        body = new String(bo.toByteArray(), StandardCharsets.UTF_8);
                    }
                }
                Map<String, List<String>> hdrs = new LinkedHashMap<>();
                for (Map.Entry<String, List<String>> e : con.getHeaderFields().entrySet()) {
                    if (e.getKey() != null) hdrs.put(e.getKey(), e.getValue());
                }
                return new Response(code, con.getResponseMessage(), hdrs, body, con.getURL().toString());
            } finally {
                con.disconnect();
            }
        }
    }

    // ================= utilidades =================
    static String nz(String s) { return s == null ? "" : s; }

    /** La imagen más nítida sin pasarse de maxH (o la más grande si todas son pequeñas). */
    static String bestImage(List<Image> imgs, int maxH) {
        if (imgs == null || imgs.isEmpty()) return "";
        Image best = null;
        for (Image i : imgs) {
            int h = i.getHeight();
            if (h <= 0) h = 360;
            if (h > maxH) continue;
            if (best == null || h > (best.getHeight() <= 0 ? 360 : best.getHeight())) best = i;
        }
        if (best == null) best = imgs.get(imgs.size() - 1);
        String u = nz(best.getUrl());
        if (u.startsWith("//")) u = "https:" + u;
        return u;
    }

    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");
    static String ymd(DateWrapper d) {
        try { return d == null ? "" : d.offsetDateTime().format(YMD); } catch (Throwable e) { return ""; }
    }

    private static final Pattern A_TAG = Pattern.compile("(?is)<a\\s[^>]*href=\"([^\"]*)\"[^>]*>(.*?)</a>");
    /** Descripción en texto plano: conserva los enlaces completos y los saltos de línea. */
    static String plain(Description d) {
        if (d == null) return "";
        String s = nz(d.content());
        if (d.type() != Description.Type.HTML) return s;
        Matcher m = A_TAG.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String href = m.group(1).replace("&amp;", "&");
            String txt = m.group(2).replaceAll("(?s)<[^>]+>", "");
            String t = txt.trim();
            String rep = (t.startsWith("http") || t.startsWith("www.") || t.endsWith("...") || t.endsWith("…")) && href.startsWith("http")
                ? href : txt;
            m.appendReplacement(sb, Matcher.quoteReplacement(rep));
        }
        m.appendTail(sb);
        s = sb.toString()
            .replaceAll("(?i)<br\\s*/?>", "\n")
            .replaceAll("(?i)</p>\\s*<p[^>]*>", "\n\n")
            .replaceAll("(?s)<[^>]+>", "");
        return unescape(s).trim();
    }

    static String unescape(String s) {
        return s.replace("&quot;", "\"").replace("&#39;", "'").replace("&#039;", "'").replace("&lt;", "<")
            .replace("&gt;", ">").replace("&nbsp;", " ").replace("&amp;", "&");
    }

    static long contentLength(org.schabi.newpipe.extractor.stream.Stream s) {
        try { return s.getItagItem() != null ? s.getItagItem().getContentLength() : 0; } catch (Throwable e) { return 0; }
    }

    static String suffix(MediaFormat f) { return f == null ? "" : nz(f.getSuffix()); }

    static boolean isVision(String url) {
        try { return YoutubeParsingHelper.isVisionOsStreamingUrl(url); } catch (Throwable e) { return false; }
    }

    static String uaFor(String url) {
        try { return isVision(url) ? YoutubeParsingHelper.getVisionOsUserAgent(null) : Http.UA; } catch (Throwable e) { return Http.UA; }
    }

    // ================= elementos de listas (búsqueda, relacionados, playlists) =================
    static JSONObject item(InfoItem it) throws Exception {
        JSONObject o = new JSONObject();
        String url = nz(it.getUrl());
        o.put("url", url).put("title", nz(it.getName()));
        if (it instanceof StreamInfoItem) {
            StreamInfoItem s = (StreamInfoItem) it;
            boolean shortV = false;
            try { shortV = s.isShortFormContent() || url.contains("/shorts/"); } catch (Throwable ignored) {}
            o.put("kind", "video")
             .put("channel", nz(s.getUploaderName()))
             .put("chUrl", nz(s.getUploaderUrl()))
             .put("duration", Math.max(0, s.getDuration()))
             .put("views", Math.max(0, s.getViewCount()))
             .put("thumb", bestImage(s.getThumbnails(), 720))
             .put("date", nz(s.getTextualUploadDate()))
             .put("short", shortV);
            String av = bestImage(s.getUploaderAvatars(), 200);
            if (!av.isEmpty()) o.put("avatar", av);
            try { if (s.getStreamType() != null && s.getStreamType().name().contains("LIVE")) o.put("live", true); } catch (Throwable ignored) {}
        } else if (it instanceof PlaylistInfoItem) {
            PlaylistInfoItem p = (PlaylistInfoItem) it;
            boolean mix = false;
            try { mix = p.getPlaylistType() != null && p.getPlaylistType().name().startsWith("MIX"); } catch (Throwable ignored) {}
            o.put("kind", "pl")
             .put("channel", nz(p.getUploaderName()))
             .put("count", Math.max(0, p.getStreamCount()))
             .put("thumb", bestImage(p.getThumbnails(), 720))
             .put("mix", mix || url.contains("list=RD"));
        } else if (it instanceof ChannelInfoItem) {
            ChannelInfoItem c = (ChannelInfoItem) it;
            o.put("kind", "ch")
             .put("channel", nz(c.getName()))
             .put("chUrl", url)
             .put("subs", Math.max(0, c.getSubscriberCount()))
             .put("count", Math.max(0, c.getStreamCount()))
             .put("thumb", bestImage(c.getThumbnails(), 240))
             .put("verified", c.isVerified());
        } else {
            return null;
        }
        return o;
    }

    static JSONArray items(List<? extends InfoItem> list) {
        JSONArray a = new JSONArray();
        if (list == null) return a;
        for (InfoItem it : list) {
            try { JSONObject o = item(it); if (o != null) a.put(o); } catch (Throwable ignored) {}
        }
        return a;
    }

    // ================= información de un video =================
    public static JSONObject info(String url) throws Exception {
        StreamInfo si = StreamInfo.getInfo(YT, url);
        JSONObject o = new JSONObject();

        // Formatos (para tamaños y calidades de descarga)
        JSONArray fm = new JSONArray();
        JSONObject byH = new JSONObject();
        List<VideoStream> muxed = si.getVideoStreams() != null ? si.getVideoStreams() : Collections.emptyList();
        List<VideoStream> vOnly = si.getVideoOnlyStreams() != null ? si.getVideoOnlyStreams() : Collections.emptyList();
        List<AudioStream> auds = si.getAudioStreams() != null ? si.getAudioStreams() : Collections.emptyList();

        for (VideoStream v : muxed) {
            int h = v.getHeight();
            if (h <= 0) { try { h = Integer.parseInt(nz(v.getResolution()).replaceAll("p.*$", "")); } catch (Exception ignored) {} }
            fm.put(new JSONObject().put("h", h).put("v", true).put("a", true).put("s", contentLength(v))
                .put("e", suffix(v.getFormat())).put("c", nz(v.getCodec()).startsWith("avc1")));
            if (h <= 0 || !v.isUrl()) continue;
            boolean hls = v.getDeliveryMethod() == DeliveryMethod.HLS;
            boolean prog = v.getDeliveryMethod() == DeliveryMethod.PROGRESSIVE_HTTP;
            if (!prog && !hls) continue;
            String key = String.valueOf(h);
            JSONObject prev = byH.optJSONObject(key);
            boolean mp4 = "mp4".equals(suffix(v.getFormat()));
            if (prev == null || (prev.optBoolean("hls") && prog) || (prev.optBoolean("hls") == hls && mp4 && !"mp4".equals(prev.optString("e")))) {
                byH.put(key, new JSONObject().put("h", h).put("url", v.getContent()).put("hls", hls)
                    .put("e", suffix(v.getFormat())).put("ua", uaFor(v.getContent())));
            }
        }
        JSONArray vo = new JSONArray();
        for (VideoStream v : vOnly) {
            int h = v.getHeight();
            long cl = contentLength(v);
            fm.put(new JSONObject().put("h", h).put("v", true).put("a", false).put("s", cl)
                .put("e", suffix(v.getFormat())).put("c", nz(v.getCodec()).startsWith("avc1")).put("fps", v.getFps()));
            if (v.isUrl() && v.getDeliveryMethod() == DeliveryMethod.PROGRESSIVE_HTTP && h > 0) {
                vo.put(new JSONObject().put("h", h).put("fps", v.getFps()).put("e", suffix(v.getFormat()))
                    .put("codec", nz(v.getCodec())).put("s", cl).put("url", v.getContent()));
            }
        }
        String audio = ""; boolean audioM4a = false; int bestBr = -1; long audioSize = 0;
        JSONArray ao = new JSONArray();
        for (AudioStream a : auds) {
            long cl = contentLength(a);
            fm.put(new JSONObject().put("h", 0).put("v", false).put("a", true).put("s", cl)
                .put("e", suffix(a.getFormat())).put("c", false));
            if (!a.isUrl() || a.getDeliveryMethod() != DeliveryMethod.PROGRESSIVE_HTTP) continue;
            // Solo la pista de audio original (no doblajes automáticos)
            try { if (a.getAudioTrackType() != null && !a.getAudioTrackType().name().equals("ORIGINAL")) continue; } catch (Throwable ignored) {}
            boolean m4a = a.getFormat() == MediaFormat.M4A;
            int br = a.getAverageBitrate();
            ao.put(new JSONObject().put("br", br).put("e", suffix(a.getFormat())).put("s", cl).put("url", a.getContent()));
            if ((m4a && !audioM4a) || (m4a == audioM4a && br > bestBr)) {
                audio = a.getContent(); bestBr = br; audioM4a = m4a; audioSize = cl;
            }
        }
        JSONArray streams = new JSONArray();
        java.util.Iterator<String> it = byH.keys();
        while (it.hasNext()) streams.put(byH.getJSONObject(it.next()));
        String hlsUrl = "";
        try { hlsUrl = nz(si.getHlsUrl()); } catch (Throwable ignored) {}

        // Subtítulos
        JSONArray subs = new JSONArray();
        try {
            List<SubtitlesStream> ss = si.getSubtitles();
            if (ss != null) for (SubtitlesStream s : ss) {
                if (!s.isUrl()) continue;
                String su = s.getContent();
                if (su.contains("fmt=")) su = su.replaceAll("fmt=[^&]*", "fmt=vtt"); else su = su + "&fmt=vtt";
                subs.put(new JSONObject().put("lang", nz(s.getLanguageTag())).put("name", nz(s.getDisplayLanguageName()))
                    .put("auto", s.isAutoGenerated()).put("url", su));
            }
        } catch (Throwable ignored) {}

        String desc = plain(si.getDescription());
        if (desc.length() > 8000) desc = desc.substring(0, 8000) + "…";
        String up = nz(si.getUploaderName());

        o.put("ok", true).put("np", true).put("client", 0)
         .put("title", nz(si.getName()))
         .put("uploader", up)
         .put("thumb", bestImage(si.getThumbnails(), 720))
         .put("duration", Math.max(0, si.getDuration()))
         .put("url", nz(si.getUrl()).isEmpty() ? url : si.getUrl())
         .put("formats", fm)
         .put("streams", streams)
         .put("vonly", vo)
         .put("auds", ao)
         .put("audio", audio)
         .put("audioUa", audio.isEmpty() ? "" : uaFor(audio))
         .put("audioSize", audioSize)
         .put("hls", hlsUrl)
         .put("desc", desc)
         .put("views", Math.max(0, si.getViewCount()))
         .put("likes", Math.max(0, si.getLikeCount()))
         .put("subs", Math.max(0, si.getUploaderSubscriberCount()))
         .put("date", ymd(si.getUploadDate()))
         .put("textDate", nz(si.getTextualUploadDate()))
         .put("chUrl", nz(si.getUploaderUrl()))
         .put("avatar", bestImage(si.getUploaderAvatars(), 200))
         .put("subtitles", subs)
         .put("tags", new JSONArray(si.getTags() != null ? si.getTags() : Collections.emptyList()))
         .put("category", nz(si.getCategory()))
         .put("related", items(si.getRelatedItems()));
        try { o.put("live", si.getStreamType() != null && si.getStreamType().name().contains("LIVE")); } catch (Throwable ignored) {}
        return o;
    }

    // ================= búsqueda con páginas =================
    private static final class SearchState {
        SearchQueryHandler qh; Page next; final List<JSONObject> items = new ArrayList<>(); boolean end; long t;
    }
    private static final Map<String, SearchState> SEARCH = new ConcurrentHashMap<>();

    /** Devuelve los resultados desde "start" (1 = primero), pidiendo más páginas si hace falta. */
    public static JSONObject search(String query, int start, int count) throws Exception {
        String key = query.trim().toLowerCase();
        SearchState st = SEARCH.get(key);
        if (st == null || start <= 1 || System.currentTimeMillis() - st.t > 20 * 60 * 1000) {
            st = new SearchState();
            st.qh = YT.getSearchQHFactory().fromQuery(query, Collections.singletonList("all"), "");
            SearchInfo si = SearchInfo.getInfo(YT, st.qh);
            addAll(st, si.getRelatedItems());
            st.next = si.getNextPage();
            st.end = !si.hasNextPage();
            st.t = System.currentTimeMillis();
            if (SEARCH.size() > 30) SEARCH.clear();
            SEARCH.put(key, st);
        }
        int from = Math.max(0, start - 1), to = from + Math.max(1, count);
        int guard = 0;
        synchronized (st) {
            while (st.items.size() < to && !st.end && st.next != null && guard++ < 5) {
                ListExtractor.InfoItemsPage<InfoItem> p = SearchInfo.getMoreItems(YT, st.qh, st.next);
                int before = st.items.size();
                addAll(st, p.getItems());
                st.next = p.getNextPage();
                st.end = !p.hasNextPage() || st.items.size() == before;
            }
        }
        JSONArray out = new JSONArray();
        for (int i = from; i < Math.min(to, st.items.size()); i++) out.put(st.items.get(i));
        return new JSONObject().put("ok", true).put("np", true).put("items", out)
            .put("more", !st.end || st.items.size() > to);
    }

    private static void addAll(SearchState st, List<? extends InfoItem> list) {
        if (list == null) return;
        for (InfoItem it : list) {
            try { JSONObject o = item(it); if (o != null) st.items.add(o); } catch (Throwable ignored) {}
        }
    }

    // ================= comentarios =================
    private static final Map<String, Object[]> CPAGES = new ConcurrentHashMap<>();   // token -> {url, Page}

    public static JSONObject comments(String url, String token) throws Exception {
        List<CommentsInfoItem> list; Page next; int count = -1; boolean disabled = false;
        if (token == null || token.isEmpty()) {
            CommentsInfo ci = CommentsInfo.getInfo(YT, url);
            list = ci.getRelatedItems(); next = ci.getNextPage();
            disabled = ci.isCommentsDisabled();
            try { count = ci.getCommentsCount(); } catch (Throwable ignored) {}
        } else {
            Object[] st = CPAGES.remove(token);
            if (st == null) return new JSONObject().put("ok", false).put("error", "expired");
            ListExtractor.InfoItemsPage<CommentsInfoItem> p = CommentsInfo.getMoreItems(YT, (String) st[0], (Page) st[1]);
            list = p.getItems(); next = p.getNextPage();
        }
        JSONArray a = new JSONArray();
        if (list != null) for (CommentsInfoItem c : list) {
            try {
                String txt = plain(c.getCommentText());
                a.put(new JSONObject()
                    .put("author", nz(c.getUploaderName()))
                    .put("chUrl", nz(c.getUploaderUrl()))
                    .put("avatar", bestImage(c.getUploaderAvatars(), 120))
                    .put("text", txt.length() > 3000 ? txt.substring(0, 3000) + "…" : txt)
                    .put("likes", Math.max(0, c.getLikeCount()))
                    .put("likesTxt", nz(c.getTextualLikeCount()))
                    .put("date", nz(c.getTextualUploadDate()))
                    .put("replies", Math.max(0, c.getReplyCount()))
                    .put("pinned", c.isPinned())
                    .put("hearted", c.isHeartedByUploader())
                    .put("owner", c.isChannelOwner()));
            } catch (Throwable ignored) {}
        }
        String nt = "";
        if (next != null) {
            nt = "c" + System.nanoTime();
            if (CPAGES.size() > 60) CPAGES.clear();
            CPAGES.put(nt, new Object[]{url, next});
        }
        return new JSONObject().put("ok", true).put("items", a).put("next", nt).put("count", count).put("disabled", disabled);
    }

    // ================= playlists y mixes =================
    private static final Map<String, Object[]> PSTATE = new ConcurrentHashMap<>();  // url -> {List<JSONObject>, Page, meta JSONObject}

    @SuppressWarnings("unchecked")
    public static JSONObject playlist(String url, int start, int count) throws Exception {
        Object[] st = PSTATE.get(url);
        if (st == null || start <= 1) {
            PlaylistInfo pi = PlaylistInfo.getInfo(YT, url);
            List<JSONObject> list = new ArrayList<>();
            for (StreamInfoItem s : pi.getRelatedItems()) { try { JSONObject o = item(s); if (o != null) list.add(o); } catch (Throwable ignored) {} }
            JSONObject meta = new JSONObject()
                .put("title", nz(pi.getName()))
                .put("channel", nz(pi.getUploaderName()))
                .put("chUrl", nz(pi.getUploaderUrl()))
                .put("count", Math.max(0, pi.getStreamCount()))
                .put("thumb", bestImage(pi.getThumbnails(), 720))
                .put("desc", plain(pi.getDescription()));
            st = new Object[]{list, pi.hasNextPage() ? pi.getNextPage() : null, meta};
            if (PSTATE.size() > 20) PSTATE.clear();
            PSTATE.put(url, st);
        }
        List<JSONObject> list = (List<JSONObject>) st[0];
        int from = Math.max(0, start - 1), to = from + Math.max(1, count);
        int guard = 0;
        synchronized (st) {
            while (list.size() < to && st[1] != null && guard++ < 5) {
                ListExtractor.InfoItemsPage<StreamInfoItem> p = PlaylistInfo.getMoreItems(YT, url, (Page) st[1]);
                int before = list.size();
                for (StreamInfoItem s : p.getItems()) { try { JSONObject o = item(s); if (o != null) list.add(o); } catch (Throwable ignored) {} }
                st[1] = p.hasNextPage() && list.size() > before ? p.getNextPage() : null;
            }
        }
        JSONArray out = new JSONArray();
        for (int i = from; i < Math.min(to, list.size()); i++) out.put(list.get(i));
        JSONObject meta = (JSONObject) st[2];
        return new JSONObject(meta.toString()).put("ok", true).put("np", true).put("items", out)
            .put("more", st[1] != null || list.size() > to);
    }
}
