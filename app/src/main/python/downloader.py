import os, re, json, time, html, random, shutil, hashlib, threading
from concurrent.futures import ThreadPoolExecutor, as_completed
import requests
from bs4 import BeautifulSoup
import yt_dlp

CACHE_TTL = 1800; _LOCAL_CACHE = {}; _LOCK = threading.Lock(); _CANCEL = {}
MOBILE_UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
IOS_UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.6 Mobile/15E148 Safari/604.1"
HAS_FFMPEG = bool(shutil.which("ffmpeg") or os.path.exists("/system/bin/ffmpeg"))

def set_cancelled(session_id, flag=True):
    with _LOCK: _CANCEL[str(session_id)] = bool(flag)
def _cancelled(bridge, session=""):
    with _LOCK:
        if _CANCEL.get(str(session)): return True
    if bridge is None: return False
    try: return bool(bridge.isCancelled())
    except: return False

def _prog(bridge, pct, downloaded=0, total=0, speed=0.0):
    if bridge is None: return
    try: bridge.onProgressEx(int(pct), int(downloaded), int(total), float(speed)); return
    except: pass
    try: bridge.onProgress(int(pct))
    except: pass

class _SpeedTracker(object):
    def __init__(self): self.t = time.time(); self.b = 0; self.speed = 0.0
    def update(self, downloaded):
        now = time.time(); dt = now - self.t
        if dt >= 0.25:
            inst = max(0, (downloaded - self.b) / dt)
            self.speed = inst if self.speed <= 0 else (self.speed * 0.6 + inst * 0.4)
            self.t = now; self.b = downloaded
        return self.speed

def _clean_url(url):
    url = (url or "").strip()
    m = re.search(r"https?://[^\s'\"<>)]+", url)
    if m: url = m.group(0)
    if url.startswith("stagram.com/"): url = "https://www.in" + url
    elif url.startswith(("instagram.com/", "www.instagram.com/")): url = "https://" + url.lstrip("/")
    return url

def _detect_platform(url):
    u = (url or "").lower()
    if "youtu" in u: return "YouTube"
    if "instagram.com" in u or "stagram.com" in u: return "Instagram"
    if "tiktok.com" in u: return "TikTok"
    if "pinterest." in u or "pin.it" in u: return "Pinterest"
    if "facebook.com" in u or "fb.watch" in u: return "Facebook"
    if "x.com" in u or "twitter.com" in u: return "X"
    return "Web"

def _audio_ext_and_label(kbps):
    if HAS_FFMPEG: return "mp3", "MP3 %dkbps" % kbps
    return "m4a", "M4A / AAC (original)"

def _build_categorized_formats(direct_video="", direct_audio="", thumb_url="", base_mb=16.0, carousel_list=None):
    carousel_list = carousel_list or []
    a_ext, a_qual = _audio_ext_and_label(320)
    formats = [
        {"formatId": (direct_video or "best[ext=mp4]/best"), "label": "High Quality Video", "quality": "Best available", "type": "MAIN_VIDEO", "ext": "mp4", "sizeMb": round(base_mb, 1)},
        {"formatId": "AUDIO::320", "label": "High Quality Song", "quality": a_qual, "type": "MAIN_AUDIO", "ext": a_ext, "sizeMb": round(max(1.5, base_mb * 0.22), 1)},
        {"formatId": "THUMB", "label": "High Quality Thumbnail", "quality": "Original JPG", "type": "MAIN_IMAGE", "ext": "jpg", "sizeMb": 1.2},
    ]
    for h, factor in [(1080, 1.0), (720, 0.6), (480, 0.4), (360, 0.25)]:
        sel = "bestvideo[height<=%d]+bestaudio/best" if HAS_FFMPEG else "best[height<=%d]/best"
        formats.append({"formatId": sel % h, "label": "%dp %s" % (h, "Full HD" if h >= 1080 else ("HD" if h >= 720 else "")), "quality": "%dp MP4" % h, "type": "VIDEO", "ext": "mp4", "sizeMb": round(base_mb * factor, 1)})
    for kbps, factor in [(320, 0.24), (256, 0.19), (192, 0.15), (128, 0.10)]:
        ext, qual = _audio_ext_and_label(kbps)
        formats.append({"formatId": "AUDIO::%d" % kbps, "label": "Audio %dkbps" % kbps, "quality": qual, "type": "AUDIO", "ext": ext, "sizeMb": round(max(1.2, base_mb * factor), 1)})
    
    # Add IG Carousel / Gallery items
    for idx, c_url in enumerate(carousel_list, 1):
        is_vid = ".mp4" in c_url.lower()
        formats.append({"formatId": "DIRECT::" + c_url, "label": "Gallery Item #%d of %d" % (idx, len(carousel_list)), "quality": "MP4" if is_vid else "Full-res Photo", "type": "VIDEO" if is_vid else "IMAGE", "ext": "mp4" if is_vid else "jpg", "sizeMb": 6.2 if is_vid else 1.5})
    if carousel_list:
        formats.insert(1, {"formatId": "DIRECT::ALL", "label": "Download ALL %d items" % len(carousel_list), "quality": "Every photo/video in this post", "type": "IMAGE", "ext": "zip", "sizeMb": round(1.5 * len(carousel_list), 1)})
    return formats

def _js_str(raw):
    try: return json.loads('"%s"' % raw)
    except: return raw.replace("\\/", "/").replace('\\"', '"').replace("\\u0026", "&")

def _ig_full_res(u):
    u = _js_str(u).replace("&amp;", "&")
    return re.sub(r"/s\d+x\d+/", "/", re.sub(r"/p\d+x\d+/", "/", u))

def _ig_best_image(node):
    iv = node.get("image_versions2") or {}
    cands = iv.get("candidates") or []
    if cands:
        best = max(cands, key=lambda c: (c.get("width") or 0) * (c.get("height") or 0))
        if best.get("url"): return _ig_full_res(best["url"])
    du = node.get("display_url") or node.get("display_src") or ""
    return _ig_full_res(du) if du else ""

def _ig_best_video(node):
    for key in ("video_versions", "video_url"):
        v = node.get(key)
        if isinstance(v, list) and v:
            best = max(v, key=lambda c: (c.get("width") or 0) * (c.get("height") or 0))
            if best.get("url"): return _js_str(best["url"])
        elif isinstance(v, str) and v: return _js_str(v)
    return ""

def _engine_instagram_direct(url):
    m = re.search(r"/(?:p|reel|reels|tv|share)/([A-Za-z0-9_-]+)", url)
    if not m: return None
    shortcode = m.group(1)
    ordered = []; video_url = ""; thumb_url = ""; uploader = "Instagram"
    try:
        er = requests.get(f"https://www.instagram.com/p/{shortcode}/embed/captioned/", headers={"User-Agent": IOS_UA}, timeout=7)
        if er.status_code == 200 and er.text:
            page = er.text
            for key in ('"gql_data":', '"shortcode_media":'):
                i = page.find(key)
                while i != -1:
                    brace = page.find("{", i + len(key))
                    if brace != -1:
                        try: obj, _ = json.JSONDecoder().raw_decode(page, brace)
                        except: obj = None
                        if isinstance(obj, dict):
                            media = obj.get("shortcode_media") or obj.get("gql_data") or obj
                            if isinstance(media, str):
                                try: media = json.loads(media)
                                except: media = None
                            if isinstance(media, dict):
                                uploader = (media.get("owner") or {}).get("username") or uploader
                                edges = (media.get("edge_sidecar_to_children") or {}).get("edges") or []
                                if edges:
                                    for edge in edges:
                                        node = edge.get("node") or {}
                                        v = _ig_best_video(node)
                                        if node.get("is_video") and v: ordered.append(v); video_url = video_url or v
                                        else:
                                            im = _ig_best_image(node)
                                            if im: ordered.append(im)
                                    thumb_url = _ig_best_image(media) or (ordered[0] if ordered else "")
                                else:
                                    v = _ig_best_video(media); im = _ig_best_image(media)
                                    if v: ordered.append(v); video_url = v
                                    if im: ordered.append(im); thumb_url = thumb_url or im
                                break
                    i = page.find(key, i + 1)
    except: pass
    if not ordered: return None
    return {"status": "SUCCESS", "url": url, "title": f"Instagram by @{uploader}", "uploader": uploader, "platform": "Instagram", "thumbnail": thumb_url, "duration": 0, "isPlaylist": len(ordered) > 1, "itemCount": len(ordered), "formats": _build_categorized_formats(video_url or ordered[0], video_url, thumb_url, 15.0, ordered)}

def _engine_tiktok_direct(url):
    try:
        r = requests.post("https://www.tikwm.com/api/", data={"url": url, "hd": 1}, headers={"User-Agent": MOBILE_UA}, timeout=6)
        if r.status_code != 200: return None
        d = (r.json() or {}).get("data") or {}
        play = d.get("hdplay") or d.get("play") or ""
        images = [i for i in (d.get("images") or []) if i]
        if play: images.append(play)
        images = list(dict.fromkeys(images))
        return {"status": "SUCCESS", "url": url, "title": d.get("title") or "TikTok", "uploader": "TikTok", "platform": "TikTok", "thumbnail": d.get("cover"), "duration": d.get("duration") or 0, "isPlaylist": len(images) > 1, "itemCount": len(images), "formats": _build_categorized_formats(play, d.get("music"), d.get("cover"), 14.0, images)}
    except: return None

def _engine_fast_ytdlp(url):
    opts = {"quiet": True, "no_warnings": True, "nocheckcertificate": True, "skip_download": True, "socket_timeout": 6, "geo_bypass": True, "http_headers": {"User-Agent": MOBILE_UA}, "extractor_args": {"youtube": {"player_client": ["android_vr", "ios", "web"]}}}
    with yt_dlp.YoutubeDL(opts) as ydl:
        info = ydl.extract_info(url, download=False)
        if not info: return None
        filesize = info.get("filesize") or info.get("filesize_approx") or 0
        dur = int(info.get("duration") or 0)
        base_mb = round(filesize / 1048576.0, 1) if filesize > 0 else round(max(4.8, (dur or 120) * 0.14), 1)
        thumb = info.get("thumbnail") or ""
        return {"status": "SUCCESS", "url": url, "title": info.get("title") or "Media", "uploader": info.get("uploader") or "Creator", "platform": _detect_platform(url), "thumbnail": thumb, "duration": dur, "isPlaylist": False, "itemCount": 1, "formats": _build_categorized_formats(base_mb=base_mb, thumb_url=thumb)}

def extract_media_info(url, cookies_path="", is_playlist=False):
    url = _clean_url(url)
    low = url.lower()
    futures = []
    with ThreadPoolExecutor(max_workers=4) as pool:
        if "instagram.com" in low: futures.append(pool.submit(_engine_instagram_direct, url))
        elif "tiktok.com" in low: futures.append(pool.submit(_engine_tiktok_direct, url))
        futures.append(pool.submit(_engine_fast_ytdlp, url))
        last_err = "Could not read this link."
        for fut in as_completed(futures):
            try:
                res = fut.result()
                if res and res.get("status") == "SUCCESS": return json.dumps(res)
            except Exception as e: last_err = str(e)
    return json.dumps({"status": "ERROR", "code": "EXTRACTION_FAILED", "message": last_err})

def download_media(url, format_id, media_type, sandbox_dir, cookies_path="", progress_bridge=None):
    url = _clean_url(url); os.makedirs(sandbox_dir, exist_ok=True)
    prefix = f"Grab_{int(time.time())}_"
    out_tmpl = os.path.join(sandbox_dir, prefix + "%(autonumber)03d.%(ext)s")
    kind = "all_gallery" if format_id == "DIRECT::ALL" else ("direct" if format_id.startswith("DIRECT::") else ("audio" if format_id.startswith("AUDIO::") or "AUDIO" in media_type else ("thumb" if format_id == "THUMB" else "video")))
    
    try:
        if kind == "all_gallery":
            res = _engine_instagram_direct(url) or _engine_tiktok_direct(url) or {}
            urls = [f["formatId"][8:] for f in res.get("formats", []) if f.get("formatId", "").startswith("DIRECT::") and f["formatId"] != "DIRECT::ALL"]
            files = []
            for i, u in enumerate(urls, 1):
                if _cancelled(progress_bridge): break
                ext = "mp4" if ".mp4" in u.lower() else "jpg"
                dest = os.path.join(sandbox_dir, f"{prefix}{i:02d}.{ext}")
                requests.get(u, headers={"User-Agent": MOBILE_UA}, stream=True, timeout=20).raise_for_status()
                with open(dest, "wb") as f:
                    for chunk in requests.get(u, headers={"User-Agent": MOBILE_UA}, stream=True).iter_content(262144): f.write(chunk)
                files.append(dest); _prog(progress_bridge, min(99, int(i * 100 / len(urls))))
            return json.dumps({"status": "SUCCESS", "filePath": files[0], "ext": "jpg", "count": len(files)})
        
        if kind == "direct":
            payload = format_id[8:]
            ext = "mp4" if ".mp4" in payload.lower() else "jpg"
            dest = os.path.join(sandbox_dir, prefix + "001." + ext)
            r = requests.get(payload, headers={"User-Agent": MOBILE_UA}, stream=True, timeout=25)
            total = int(r.headers.get("content-length") or 0); done = 0; tracker = _SpeedTracker()
            with open(dest, "wb") as f:
                for chunk in r.iter_content(262144):
                    if _cancelled(progress_bridge): break
                    f.write(chunk); done += len(chunk); _prog(progress_bridge, min(99, int(done * 100 / total)) if total else 50, done, total, tracker.update(done))
            return json.dumps({"status": "SUCCESS", "filePath": dest, "ext": ext})

        if kind == "thumb":
            with yt_dlp.YoutubeDL({"quiet": True, "skip_download": True}) as ydl:
                info = ydl.extract_info(url, download=False) or {}
                thumb = (info.get("thumbnails") or [{}])[-1].get("url") or info.get("thumbnail")
            if not thumb: return json.dumps({"status": "ERROR", "message": "No thumbnail"})
            dest = os.path.join(sandbox_dir, prefix + "cover.jpg")
            with open(dest, "wb") as f:
                for chunk in requests.get(thumb, headers={"User-Agent": MOBILE_UA}).iter_content(): f.write(chunk)
            return json.dumps({"status": "SUCCESS", "filePath": dest, "ext": "jpg"})

        opts = {"outtmpl": out_tmpl, "noplaylist": True, "retries": 3, "fragment_retries": 3, "socket_timeout": 15, "geo_bypass": True, "nocheckcertificate": True, "windowsfilenames": True, "concurrent_fragment_downloads": 8, "http_headers": {"User-Agent": MOBILE_UA}, "extractor_args": {"youtube": {"player_client": ["android_vr", "ios", "web"]}}}
        if kind == "audio":
            opts["format"] = "bestaudio[ext=m4a]/bestaudio/best"
            if HAS_FFMPEG: opts["postprocessors"] = [{"key": "FFmpegExtractAudio", "preferredcodec": "mp3", "preferredquality": format_id.split("::")[1] if "::" in format_id else "320"}]
        else:
            opts["format"] = format_id if format_id and "+" not in format_id else "best[ext=mp4]/best"

        tracker = _SpeedTracker()
        def hook(d):
            if _cancelled(progress_bridge): raise Exception("CANCELLED")
            if d.get("status") == "downloading":
                total = d.get("total_bytes") or d.get("total_bytes_estimate") or 0
                done = d.get("downloaded_bytes") or 0
                speed = d.get("speed") or tracker.update(done)
                _prog(progress_bridge, min(99, int(done * 100 / total)) if total else 50, done, total, speed)
        opts["progress_hooks"] = [hook]
        
        with yt_dlp.YoutubeDL(opts) as ydl:
            info = ydl.extract_info(url, download=True) or {}
            prepared = ydl.prepare_filename(info)
            
        found = [os.path.join(sandbox_dir, f) for f in os.listdir(sandbox_dir) if f.startswith(prefix) and not f.endswith(".part")]
        target = found[0] if found else prepared
        if not os.path.exists(target):
            u = info.get("url") or ""
            if u.startswith("http"):
                dest = os.path.join(sandbox_dir, prefix + "fallback.mp4")
                with open(dest, "wb") as f:
                    for chunk in requests.get(u, stream=True).iter_content(262144): f.write(chunk)
                target = dest
        ext = os.path.splitext(target)[1].lstrip(".").lower()
        _prog(progress_bridge, 100)
        return json.dumps({"status": "SUCCESS", "filePath": target, "ext": ext})
    except Exception as e:
        return json.dumps({"status": "ERROR", "message": str(e) or "Download failed"})

def update_extractors_info():
    return json.dumps({"status": f"Extractors Synced (yt-dlp {yt_dlp.version.__version__})"})
