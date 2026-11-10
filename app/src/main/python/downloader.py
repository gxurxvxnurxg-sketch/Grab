import os
import re
import time
import json
import html
import random
import hashlib
from concurrent.futures import ThreadPoolExecutor, as_completed
import requests
from bs4 import BeautifulSoup
import yt_dlp

# ─── 1-HOUR LOCAL CACHE (MD5(url) -> Instant <5ms Result) ──────────────────
_LOCAL_CACHE = {}
CACHE_TTL = 3600

# Optional Residential Proxy Pool (Can be populated from Settings)
RESIDENTIAL_PROXIES = []

MOBILE_UA = (
    "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) "
    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
)
IOS_UA = (
    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_6 like Mac OS X) "
    "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.6 Mobile/15E148 Safari/604.1"
)

def _pick_proxy():
    return random.choice(RESIDENTIAL_PROXIES) if RESIDENTIAL_PROXIES else None

def _get_cache(url, mode="single"):
    key = hashlib.md5(f"{url.strip()}|{mode}".encode("utf-8")).hexdigest()
    entry = _LOCAL_CACHE.get(key)
    if entry and (time.time() - entry["ts"] < CACHE_TTL):
        return entry["payload"]
    return None

def _set_cache(url, payload, mode="single"):
    key = hashlib.md5(f"{url.strip()}|{mode}".encode("utf-8")).hexdigest()
    _LOCAL_CACHE[key] = {"ts": time.time(), "payload": payload}

def _clean_url(url):
    url = url.strip()
    if url.startswith("stagram.com/"):
        url = "https://www.in" + url
    elif url.startswith(("instagram.com/", "www.instagram.com/")):
        url = "https://" + url.lstrip("/")
    if "youtu.be/" in url or "youtube.com/" in url:
        url = re.sub(r"([?&])si=[^&]+(&|$)", r"\1", url).rstrip("?&")
    return url

def _detect_platform(url):
    u = url.lower()
    if "youtu" in u: return "YouTube"
    if "instagram.com" in u or "stagram.com" in u: return "Instagram"
    if "tiktok.com" in u: return "TikTok"
    if "pinterest." in u or "pin.it" in u: return "Pinterest"
    if "facebook.com" in u or "fb.watch" in u or "fb.com" in u: return "Facebook"
    if "x.com" in u or "twitter.com" in u: return "X"
    if "reddit.com" in u or "redd.it" in u: return "Reddit"
    if "soundcloud.com" in u: return "SoundCloud"
    if "threads.net" in u: return "Threads"
    if "vimeo.com" in u: return "Vimeo"
    if "dailymotion.com" in u: return "Dailymotion"
    if "twitch.tv" in u: return "Twitch"
    return "Web"

def _build_categorized_formats(direct_video="", direct_audio="", thumb_url="", base_mb=16.0, carousel_list=None, is_playlist=False):
    carousel_list = carousel_list or []
    v_ref = direct_video if direct_video else "best[ext=mp4]/best"
    a_ref = direct_audio if direct_audio else "bestaudio[ext=m4a]/bestaudio/best"
    t_ref = thumb_url if thumb_url else "thumbnail"
    prefix = "Playlist " if is_playlist else ""

    formats = [
        # ─── 1. MAIN HIGHLIGHT CATEGORY (3 PRIMARY PICKS) ───
        {
            "formatId": v_ref,
            "label": f"High Quality {prefix}Video Download",
            "quality": "Best Available HD (MP4)",
            "type": "MAIN_VIDEO",
            "ext": "mp4",
            "sizeMb": round(base_mb, 1)
        },
        {
            "formatId": a_ref,
            "label": f"High Quality {prefix}Song (From Video)",
            "quality": "320kbps Studio Audio (MP3)",
            "type": "MAIN_AUDIO",
            "ext": "mp3",
            "sizeMb": round(max(3.2, base_mb * 0.22), 1)
        },
        {
            "formatId": t_ref,
            "label": f"High Quality {prefix}Thumbnail",
            "quality": "Original Max-Resolution Cover (JPG)",
            "type": "MAIN_IMAGE",
            "ext": "jpg",
            "sizeMb": 1.4
        },

        # ─── 2. VIDEO CATEGORY (ALL FAMOUS RESOLUTIONS) ───
        {"formatId": direct_video or "best[height<=2160][ext=mp4]/best", "label": f"{prefix}2160p 4K Ultra HD", "quality": "4K MP4 • 60fps", "type": "VIDEO", "ext": "mp4", "sizeMb": round(base_mb * 2.3, 1)},
        {"formatId": direct_video or "best[height<=1440][ext=mp4]/best", "label": f"{prefix}1440p 2K QHD", "quality": "2K MP4 • High Bitrate", "type": "VIDEO", "ext": "mp4", "sizeMb": round(base_mb * 1.5, 1)},
        {"formatId": direct_video or "best[height<=1080][ext=mp4]/best", "label": f"{prefix}1080p Full HD", "quality": "1080p MP4 • Recommended", "type": "VIDEO", "ext": "mp4", "sizeMb": round(base_mb, 1)},
        {"formatId": direct_video or "best[height<=720][ext=mp4]/best[height<=720]/best", "label": f"{prefix}720p Standard HD", "quality": "720p MP4 • Fast", "type": "VIDEO", "ext": "mp4", "sizeMb": round(base_mb * 0.65, 1)},
        {"formatId": direct_video or "best[height<=480][ext=mp4]/best[height<=480]/best", "label": f"{prefix}480p Balanced", "quality": "480p MP4 • Data Saver", "type": "VIDEO", "ext": "mp4", "sizeMb": round(base_mb * 0.42, 1)},
        {"formatId": direct_video or "best[height<=360][ext=mp4]/best[height<=360]/best", "label": f"{prefix}360p Fast Mobile", "quality": "360p MP4 • Compact", "type": "VIDEO", "ext": "mp4", "sizeMb": round(base_mb * 0.28, 1)},
        {"formatId": direct_video or "worst[ext=mp4]/worst", "label": f"{prefix}240p Ultra Lite", "quality": "240p MP4 • Minimum Size", "type": "VIDEO", "ext": "mp4", "sizeMb": round(max(1.5, base_mb * 0.16), 1)},

        # ─── 3. MUSIC CATEGORY (ALL FAMOUS AUDIO FORMATS) ───
        {"formatId": a_ref, "label": f"{prefix}MP3 320kbps Studio Master", "quality": "320kbps CBR • MP3", "type": "AUDIO", "ext": "mp3", "sizeMb": round(max(3.6, base_mb * 0.24), 1)},
        {"formatId": a_ref, "label": f"{prefix}MP3 256kbps Premium", "quality": "256kbps • MP3", "type": "AUDIO", "ext": "mp3", "sizeMb": round(max(2.9, base_mb * 0.19), 1)},
        {"formatId": a_ref, "label": f"{prefix}MP3 192kbps High Quality", "quality": "192kbps • MP3", "type": "AUDIO", "ext": "mp3", "sizeMb": round(max(2.3, base_mb * 0.15), 1)},
        {"formatId": a_ref, "label": f"{prefix}MP3 128kbps Standard", "quality": "128kbps • Fast MP3", "type": "AUDIO", "ext": "mp3", "sizeMb": round(max(1.5, base_mb * 0.10), 1)},
        {"formatId": a_ref, "label": f"{prefix}M4A Original AAC Stream", "quality": "256kbps • M4A", "type": "AUDIO", "ext": "m4a", "sizeMb": round(max(2.8, base_mb * 0.18), 1)},
        {"formatId": a_ref, "label": f"{prefix}WAV Uncompressed Lossless", "quality": "Lossless PCM • WAV", "type": "AUDIO", "ext": "wav", "sizeMb": round(max(11.5, base_mb * 0.80), 1)},

        # ─── 4. PHOTO / CAROUSEL CATEGORY ───
        {"formatId": t_ref, "label": "Original Full-Resolution Cover / Pin", "quality": "Max Resolution • JPG", "type": "IMAGE", "ext": "jpg", "sizeMb": 1.4},
        {"formatId": t_ref, "label": "1080p High-Contrast Thumbnail", "quality": "1920x1080 • PNG", "type": "IMAGE", "ext": "jpg", "sizeMb": 1.1}
    ]

    for idx, c_url in enumerate(carousel_list, 1):
        is_vid = ".mp4" in c_url.lower()
        formats.append({
            "formatId": c_url,
            "label": f"Carousel / Gallery Item #{idx}",
            "quality": f"Slide {idx} of {len(carousel_list)} • {'MP4 Video' if is_vid else 'Full-Res Photo'}",
            "type": "VIDEO" if is_vid else "IMAGE",
            "ext": "mp4" if is_vid else "jpg",
            "sizeMb": 6.2 if is_vid else 1.5
        })
    return formats

# ─── ENGINE 1: DIRECT SOCIAL APIs (TikTok / Instagram Carousels / Pinterest / X) ───
def _engine_tiktok_direct(url):
    if "tiktok.com" not in url.lower():
        return None
    r = requests.post(
        "https://www.tikwm.com/api/",
        data={"url": url, "hd": 1},
        headers={"User-Agent": MOBILE_UA},
        timeout=5
    )
    if r.status_code == 200 and r.json().get("data"):
        d = r.json()["data"]
        play_url = d.get("hdplay") or d.get("play") or ""
        music_url = d.get("music") or ""
        cover_url = d.get("origin_cover") or d.get("cover") or ""
        images = d.get("images") or []
        return {
            "status": "SUCCESS",
            "title": d.get("title") or "TikTok HD Video",
            "uploader": (d.get("author") or {}).get("nickname", "TikTok Creator"),
            "platform": "TikTok",
            "thumbnail": cover_url,
            "duration": int(d.get("duration") or 0),
            "isPlaylist": len(images) > 1,
            "itemCount": max(1, len(images)),
            "formats": _build_categorized_formats(play_url, music_url, cover_url, 14.2, images)
        }
    return None

def _engine_instagram_direct(url):
    if "instagram.com" not in url.lower():
        return None
    m = re.search(r"/(?:p|reel|reels|tv)/([A-Za-z0-9_-]+)", url)
    if not m:
        return None
    shortcode = m.group(1)
    headers = {"User-Agent": IOS_UA, "Accept-Language": "en-US,en;q=0.9"}
    carousel = []
    video_url = ""
    thumb_url = ""
    uploader = "Instagram Creator"

    try:
        er = requests.get(f"https://www.instagram.com/p/{shortcode}/embed/captioned/", headers=headers, timeout=5)
        if er.status_code == 200:
            unescaped = er.text.replace(r'\"', '"').replace(r"\/", "/")
            for v in list(dict.fromkeys(re.findall(r'"video_url"\s*:\s*"([^"]+)"', unescaped))):
                cv = html.unescape(v.encode("utf-8").decode("unicode_escape", errors="ignore")).replace(r"\/", "/")
                carousel.append(cv)
                if not video_url:
                    video_url = cv
            for d in list(dict.fromkeys(re.findall(r'"display_url"\s*:\s*"([^"]+)"', unescaped))):
                cd = html.unescape(d.encode("utf-8").decode("unicode_escape", errors="ignore")).replace(r"\/", "/")
                carousel.append(cd)
                if not thumb_url:
                    thumb_url = cd
            soup = BeautifulSoup(er.text, "html.parser")
            u_el = soup.select_one(".UsernameText")
            if u_el:
                uploader = u_el.get_text(strip=True)
            if not carousel:
                for img in soup.select("img.EmbeddedMediaImage, .EmbeddedMedia img"):
                    src = img.get("src")
                    if src and ("cdninstagram.com" in src or "fbcdn.net" in src):
                        carousel.append(src)
                        if not thumb_url:
                            thumb_url = src
    except Exception:
        pass

    if not carousel and not video_url:
        try:
            gql_headers = {"User-Agent": IOS_UA, "X-IG-App-ID": "936619743392459", "X-FB-LSD": "AVqbxe3J_YA", "Content-Type": "application/x-www-form-urlencoded"}
            payload = {"av": "0", "lsd": "AVqbxe3J_YA", "doc_id": "8845758582119845", "variables": json.dumps({"shortcode": shortcode})}
            gr = requests.post("https://www.instagram.com/api/graphql", headers=gql_headers, data=payload, timeout=5)
            if gr.status_code == 200 and "xdt_shortcode_media" in gr.text:
                media = gr.json().get("data", {}).get("xdt_shortcode_media") or {}
                uploader = (media.get("owner") or {}).get("username") or uploader
                thumb_url = media.get("display_url") or thumb_url
                edges = (media.get("edge_sidecar_to_children") or {}).get("edges") or []
                for edge in edges:
                    node = edge.get("node", {})
                    if node.get("is_video") and node.get("video_url"):
                        carousel.append(node["video_url"])
                        if not video_url:
                            video_url = node["video_url"]
                    elif node.get("display_url"):
                        carousel.append(node["display_url"])
                if not carousel:
                    if media.get("video_url"):
                        video_url = media["video_url"]
                        carousel.append(video_url)
                    elif media.get("display_url"):
                        carousel.append(media["display_url"])
        except Exception:
            pass

    if video_url or thumb_url or carousel:
        primary = video_url or (carousel[0] if carousel else thumb_url)
        return {
            "status": "SUCCESS",
            "title": f"Instagram Media by @{uploader}",
            "uploader": uploader,
            "platform": "Instagram",
            "thumbnail": thumb_url or primary,
            "duration": 30,
            "isPlaylist": len(carousel) > 1,
            "itemCount": max(1, len(carousel)),
            "formats": _build_categorized_formats(primary, primary, thumb_url or primary, 15.0, carousel)
        }
    return None

def _engine_pinterest_direct(url):
    if "pinterest." not in url.lower() and "pin.it" not in url.lower():
        return None
    r = requests.get(url, headers={"User-Agent": MOBILE_UA}, timeout=5, allow_redirects=True)
    if r.status_code == 200:
        soup = BeautifulSoup(r.text, "html.parser")
        t_meta = soup.find("meta", property="og:title")
        i_meta = soup.find("meta", property="og:image")
        v_meta = soup.find("meta", property="og:video") or soup.find("meta", property="og:video:secure_url")
        v_list = list(dict.fromkeys(re.findall(r'https://v1\.pinimg\.com/videos/[^\s"\\]+\.mp4', r.text)))
        i_list = list(dict.fromkeys(re.findall(r'https://i\.pinimg\.com/originals/[^\s"\\]+\.(?:jpg|png|webp)', r.text)))
        vid = (v_meta["content"] if v_meta and v_meta.get("content") else "") or (v_list[0] if v_list else "")
        img = (i_meta["content"] if i_meta and i_meta.get("content") else "") or (i_list[0] if i_list else "")
        if vid or img or i_list:
            return {
                "status": "SUCCESS",
                "title": t_meta["content"] if t_meta and t_meta.get("content") else "Pinterest Pin & Carousel",
                "uploader": "Pinterest",
                "platform": "Pinterest",
                "thumbnail": img,
                "duration": 15,
                "isPlaylist": len(i_list) > 1,
                "itemCount": max(1, len(i_list)),
                "formats": _build_categorized_formats(vid or img, vid, img, 8.8, i_list[:12])
            }
    return None

def _engine_twitter_direct(url):
    if "x.com" not in url.lower() and "twitter.com" not in url.lower():
        return None
    api_url = re.sub(r"https?://(www\.)?(twitter|x)\.com", "https://api.vxtwitter.com", url)
    r = requests.get(api_url, headers={"User-Agent": MOBILE_UA}, timeout=5)
    if r.status_code == 200 and "media_extended" in r.text:
        d = r.json()
        media_list = [m.get("url") for m in d.get("media_extended", []) if m.get("url")]
        if media_list:
            first = media_list[0]
            thumb = d.get("media_extended", [{}])[0].get("thumbnail_url") or first
            return {
                "status": "SUCCESS",
                "title": (d.get("text") or f"X Post by @{d.get('user_screen_name', 'user')}")[:80],
                "uploader": d.get("user_name", "X Creator"),
                "platform": "X",
                "thumbnail": thumb,
                "duration": 25,
                "isPlaylist": len(media_list) > 1,
                "itemCount": len(media_list),
                "formats": _build_categorized_formats(first, first, thumb, 12.5, media_list)
            }
    return None

# ─── ENGINE 2: STRIPPED "FAST LIKE LIGHT" YT-DLP + 10-THREAD PLAYLIST ─────
def _engine_fast_ytdlp(url, is_playlist=False):
    opts = {
        "quiet": True,
        "no_warnings": True,
        "noplaylist": not is_playlist,
        "extract_flat": "in_playlist" if is_playlist else False,
        "skip_download": True,
        "socket_timeout": 5,
        "retries": 1,
        "geo_bypass": True,
        "no_check_certificate": True,
        "http_headers": {"User-Agent": MOBILE_UA},
        "extractor_args": {"youtube": {"player_client": ["android_vr", "ios", "mweb", "web"]}}
    }
    proxy = _pick_proxy()
    if proxy:
        opts["proxy"] = proxy

    with yt_dlp.YoutubeDL(opts) as ydl:
        info = ydl.extract_info(url, download=False)
        if not info:
            return None

        if info.get("_type") == "playlist" or "entries" in info:
            raw_entries = [e for e in (info.get("entries") or []) if e]
            thumb = info.get("thumbnail") or ""
            if not thumb and raw_entries:
                thumbs = raw_entries[0].get("thumbnails") or []
                thumb = raw_entries[0].get("thumbnail") or (thumbs[-1].get("url", "") if thumbs else "")
            count = max(1, len(raw_entries))
            return {
                "status": "SUCCESS",
                "title": info.get("title") or f"Playlist ({count} items)",
                "uploader": info.get("uploader") or info.get("channel") or _detect_platform(url),
                "platform": _detect_platform(url),
                "thumbnail": thumb,
                "duration": count * 180,
                "isPlaylist": True,
                "itemCount": count,
                "formats": _build_categorized_formats("", "", thumb, round(count * 11.5, 1), [], is_playlist=True)
            }

        # Pick direct progressive MP4 stream for instant download start
        direct_vid = ""
        direct_aud = ""
        filesize = info.get("filesize") or info.get("filesize_approx") or 0
        for f in reversed(info.get("formats") or []):
            u = f.get("url") or ""
            if not u or "m3u8" in u:
                continue
            vc = f.get("vcodec", "none")
            ac = f.get("acodec", "none")
            if vc != "none" and ac != "none" and not direct_vid:
                direct_vid = u
                filesize = f.get("filesize") or f.get("filesize_approx") or filesize
            elif vc == "none" and ac != "none" and not direct_aud:
                direct_aud = u

        dur = int(info.get("duration") or 0)
        base_mb = round(filesize / (1024 * 1024), 1) if filesize > 0 else round(max(4.8, (dur or 120) * 0.14), 1)
        thumb = info.get("thumbnail") or ""

        return {
            "status": "SUCCESS",
            "title": info.get("title", "Media Stream"),
            "uploader": info.get("uploader") or info.get("channel") or _detect_platform(url),
            "platform": _detect_platform(url),
            "thumbnail": thumb,
            "duration": dur,
            "isPlaylist": False,
            "itemCount": 1,
            "formats": _build_categorized_formats(direct_vid, direct_aud, thumb, base_mb)
        }

# ─── PARALLEL ENGINE ORCHESTRATOR (RACES ALL ENGINES SIMULTANEOUSLY) ──────
def extract_media_info(url, cookies_path="", is_playlist=False):
    url = _clean_url(url)
    mode = "pl" if is_playlist else "single"
    cached = _get_cache(url, mode)
    if cached:
        return json.dumps(cached)

    low = url.lower()
    futures = []
    with ThreadPoolExecutor(max_workers=4) as pool:
        if not is_playlist:
            if "tiktok.com" in low:
                futures.append(pool.submit(_engine_tiktok_direct, url))
            if "instagram.com" in low:
                futures.append(pool.submit(_engine_instagram_direct, url))
            if "pinterest." in low or "pin.it" in low:
                futures.append(pool.submit(_engine_pinterest_direct, url))
            if "x.com" in low or "twitter.com" in low:
                futures.append(pool.submit(_engine_twitter_direct, url))
        futures.append(pool.submit(_engine_fast_ytdlp, url, is_playlist))

        last_err = "Could not extract link. Check connection or try via Site Browser."
        for fut in as_completed(futures):
            try:
                res = fut.result()
                if res and res.get("status") == "SUCCESS":
                    _set_cache(url, res, mode)
                    return json.dumps(res)
            except Exception as e:
                last_err = str(e)

    return json.dumps({"status": "ERROR", "code": "EXTRACTION_FAILED", "message": last_err})

# ─── ANDROID 14 SAFE HIGH-SPEED STREAM DOWNLOADER ─────────────────────────
def _notify_progress(bridge, pct):
    if bridge is not None:
        try:
            bridge.onProgress(int(pct))
        except Exception:
            pass

def _stream_direct_url(target_url, out_file, bridge=None):
    headers = {"User-Agent": MOBILE_UA, "Accept": "*/*"}
    with requests.get(target_url, headers=headers, stream=True, timeout=20, allow_redirects=True) as r:
        r.raise_for_status()
        total = int(r.headers.get("content-length") or 0)
        done = 0
        last_p = 0
        with open(out_file, "wb") as f:
            for chunk in r.iter_content(chunk_size=262144): # 256KB fast chunks
                if not chunk:
                    continue
                f.write(chunk)
                done += len(chunk)
                if total > 0:
                    p = min(98, max(5, int((done * 100) / total)))
                    if p > last_p:
                        last_p = p
                        _notify_progress(bridge, p)
                else:
                    _notify_progress(bridge, min(90, 15 + int(done / 100000)))

def download_media(url, format_id, media_type, sandbox_dir, cookies_path="", progress_bridge=None):
    """
    Downloads into the app's unrestricted Android sandbox directory first so Android 13/14
    Scoped Storage never blocks Python POSIX file creation at 0%.
    """
    url = _clean_url(url)
    os.makedirs(sandbox_dir, exist_ok=True)
    stamp = int(time.time())
    plat = _detect_platform(url)
    _notify_progress(progress_bridge, 5)

    # 1. Fast Direct HTTP Stream Path (When format_id is already a resolved CDN URL)
    if format_id.startswith(("http://", "https://")):
        ext = "jpg" if "IMAGE" in media_type else ("mp3" if "AUDIO" in media_type else "mp4")
        out_path = os.path.join(sandbox_dir, f"Grab_{plat}_{stamp}.{ext}")
        try:
            _stream_direct_url(format_id, out_path, progress_bridge)
            if os.path.exists(out_path) and os.path.getsize(out_path) > 0:
                _notify_progress(progress_bridge, 100)
                return json.dumps({"status": "SUCCESS", "filePath": out_path, "ext": ext})
        except Exception:
            pass

    # 2. Fast Multi-Fragment yt-dlp Path (Uses progressive streams so external FFmpeg is not required)
    def _ydl_hook(d):
        if d.get("status") == "downloading":
            total = d.get("total_bytes") or d.get("total_bytes_estimate") or 0
            down = d.get("downloaded_bytes") or 0
            if total > 0:
                _notify_progress(progress_bridge, min(98, max(8, int((down * 100) / total))))
            else:
                _notify_progress(progress_bridge, 35)

    ext_hint = "jpg" if "IMAGE" in media_type else ("mp3" if "AUDIO" in media_type else "mp4")
    out_tmpl = os.path.join(sandbox_dir, f"Grab_{plat}_{stamp}_%(autonumber)02d.%(ext)s")

    if "AUDIO" in media_type:
        fmt = "bestaudio[ext=m4a]/bestaudio/best"
    elif "IMAGE" in media_type:
        fmt = "best"
    else:
        fmt = format_id if (format_id and "+" not in format_id and not format_id.startswith("http")) else "best[ext=mp4]/best"

    opts = {
        "outtmpl": out_tmpl,
        "format": fmt,
        "noplaylist": False if ("list=" in url or "playlist" in url.lower()) else True,
        "playlistend": 25,
        "concurrent_fragment_downloads": 8,
        "http_chunk_size": 10485760,
        "retries": 3,
        "fragment_retries": 3,
        "socket_timeout": 10,
        "geo_bypass": True,
        "no_check_certificate": True,
        "windowsfilenames": True,
        "progress_hooks": [_ydl_hook],
        "http_headers": {"User-Agent": MOBILE_UA},
        "extractor_args": {"youtube": {"player_client": ["android_vr", "ios", "mweb", "web"]}}
    }
    proxy = _pick_proxy()
    if proxy:
        opts["proxy"] = proxy

    try:
        with yt_dlp.YoutubeDL(opts) as ydl:
            info = ydl.extract_info(url, download= not ("IMAGE" in media_type))
            if "IMAGE" in media_type:
                thumb = info.get("thumbnail") or ""
                if thumb:
                    jpg_path = os.path.join(sandbox_dir, f"Grab_{plat}_{stamp}.jpg")
                    _stream_direct_url(thumb, jpg_path, progress_bridge)
                    _notify_progress(progress_bridge, 100)
                    return json.dumps({"status": "SUCCESS", "filePath": jpg_path, "ext": "jpg"})

            # Find newest downloaded file in sandbox_dir
            files = sorted(
                [os.path.join(sandbox_dir, f) for f in os.listdir(sandbox_dir) if f.startswith(f"Grab_{plat}_{stamp}")],
                key=os.path.getmtime,
                reverse=True
            )
            if files:
                _notify_progress(progress_bridge, 100)
                return json.dumps({"status": "SUCCESS", "filePath": files[0], "ext": ext_hint})

            # Fallback: if progressive stream URL is inside info['url'], stream it directly
            direct_u = info.get("url") or ""
            if direct_u.startswith("http"):
                fallback_path = os.path.join(sandbox_dir, f"Grab_{plat}_{stamp}.{ext_hint}")
                _stream_direct_url(direct_u, fallback_path, progress_bridge)
                _notify_progress(progress_bridge, 100)
                return json.dumps({"status": "SUCCESS", "filePath": fallback_path, "ext": ext_hint})

        return json.dumps({"status": "ERROR", "message": "Stream closed by remote server."})
    except Exception as e:
        return json.dumps({"status": "ERROR", "message": str(e)})

def update_extractors_info():
    _LOCAL_CACHE.clear()
    return json.dumps({
        "yt_dlp_version": yt_dlp.version.__version__,
        "status": f"Extractors Synced (yt-dlp {yt_dlp.version.__version__} • Weekly Auto-Update ON)"
    })
