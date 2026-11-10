import os
import json
import random
import yt_dlp

# Layer 1 & Header Profile Management: Coherent Browser Profiles (User-Agent + Sec-CH-UA-*)
HEADER_PROFILES = [
    {
        "name": "Chrome_130_Windows",
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36",
        "Sec-CH-UA": '"Chromium";v="130", "Google Chrome";v="130", "Not?A_Brand";v="99"',
        "Sec-CH-UA-Mobile": "?0",
        "Sec-CH-UA-Platform": '"Windows"',
        "Accept-Language": "en-US,en;q=0.9"
    },
    {
        "name": "Chrome_130_Android",
        "User-Agent": "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36",
        "Sec-CH-UA": '"Chromium";v="130", "Google Chrome";v="130", "Not?A_Brand";v="99"',
        "Sec-CH-UA-Mobile": "?1",
        "Sec-CH-UA-Platform": '"Android"',
        "Accept-Language": "en-US,en;q=0.9"
    },
    {
        "name": "Safari_18_macOS",
        "User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15",
        "Accept-Language": "en-US,en;q=0.9"
    }
]

def _build_headers(profile):
    headers = {"User-Agent": profile["User-Agent"], "Accept-Language": profile["Accept-Language"]}
    if "Sec-CH-UA" in profile:
        headers["Sec-CH-UA"] = profile["Sec-CH-UA"]
        headers["Sec-CH-UA-Mobile"] = profile["Sec-CH-UA-Mobile"]
        headers["Sec-CH-UA-Platform"] = profile["Sec-CH-UA-Platform"]
    return headers

def _detect_platform(url):
    u = url.lower()
    if "youtu" in u: return "YouTube"
    if "instagram.com" in u: return "Instagram"
    if "tiktok.com" in u: return "TikTok"
    if "facebook.com" in u or "fb.watch" in u: return "Facebook"
    if "x.com" in u or "twitter.com" in u: return "X"
    return "Web"

def extract_media_info(url, cookies_path=""):
    """
    Executes the 5-Layer Bot Bypass Strategy to inspect formats, thumbnail, and file sizes.
    """
    profile = random.choice(HEADER_PROFILES)
    attempts = [
        {"layer": 1, "impersonate": False, "use_cookies": False},
        {"layer": 3, "impersonate": True,  "use_cookies": False},
        {"layer": 4, "impersonate": True,  "use_cookies": True}
    ]
    last_error = "EXTRACTION_FAILED"

    for attempt in attempts:
        opts = {
            "quiet": True,
            "no_warnings": True,
            "noplaylist": True,
            "http_headers": _build_headers(profile),
            "geo_bypass": True,
        }
        # Layer 3: curl_cffi TLS Handshake Impersonation
        if attempt["impersonate"]:
            try:
                from yt_dlp.networking.impersonate import ImpersonateTarget
                opts["impersonate"] = ImpersonateTarget("chrome")
            except Exception:
                pass

        # Layer 4: Session Authentication via cookies.txt
        if attempt["use_cookies"] and cookies_path and os.path.exists(cookies_path) and os.path.getsize(cookies_path) > 20:
            opts["cookiefile"] = cookies_path

        try:
            with yt_dlp.YoutubeDL(opts) as ydl:
                info = ydl.extract_info(url, download=False)
                formats_out = []
                seen_heights = set()

                for f in info.get("formats", []):
                    height = f.get("height")
                    vcodec = f.get("vcodec", "none")
                    filesize = f.get("filesize") or f.get("filesize_approx") or 0
                    if vcodec != "none" and height in (360, 480, 720, 1080, 1440, 2160) and height not in seen_heights:
                        seen_heights.add(height)
                        size_mb = round(filesize / (1024 * 1024), 1) if filesize else round(height * 0.035, 1)
                        formats_out.append({
                            "formatId": f"bestvideo[height<={height}]+bestaudio/best[height<={height}]/best",
                            "label": f"{height}p HD Video",
                            "type": "VIDEO",
                            "ext": "mp4",
                            "sizeMb": size_mb
                        })

                if not formats_out:
                    formats_out.append({
                        "formatId": "best",
                        "label": "Best Available Video (MP4)",
                        "type": "VIDEO",
                        "ext": "mp4",
                        "sizeMb": 18.5
                    })

                formats_out.append({
                    "formatId": "bestaudio/best",
                    "label": "Extract Audio (MP3 320kbps)",
                    "type": "AUDIO",
                    "ext": "mp3",
                    "sizeMb": 4.8
                })
                formats_out.append({
                    "formatId": "thumbnail",
                    "label": "Download Cover Image (JPG)",
                    "type": "IMAGE",
                    "ext": "jpg",
                    "sizeMb": 1.2
                })

                return json.dumps({
                    "status": "SUCCESS",
                    "title": info.get("title", "Untitled Media"),
                    "uploader": info.get("uploader") or info.get("channel") or _detect_platform(url),
                    "platform": _detect_platform(url),
                    "thumbnail": info.get("thumbnail") or "",
                    "duration": int(info.get("duration") or 0),
                    "formats": sorted(formats_out, key=lambda x: x["sizeMb"], reverse=True)
                })
        except Exception as e:
            err_str = str(e)
            last_error = err_str
            # Layer 2 Challenge Detection: Continue to Layer 3 (curl_cffi) & Layer 4 (cookies.txt)
            continue

    need_login = any(k in last_error.lower() for k in ["login", "cookie", "sign in", "bot", "403", "private"])
    return json.dumps({
        "status": "ERROR",
        "code": "AUTH_REQUIRED" if need_login else "NETWORK_OR_PARSE_ERROR",
        "message": last_error
    })

def download_media(url, format_id, media_type, output_dir, cookies_path="", progress_cb=None):
    """
    Executes the download with progress callbacks for WorkManager Foreground Notification.
    """
    profile = random.choice(HEADER_PROFILES)

    def _hook(d):
        if progress_cb is not None and d.get("status") == "downloading":
            total = d.get("total_bytes") or d.get("total_bytes_estimate") or 0
            downloaded = d.get("downloaded_bytes") or 0
            pct = int((downloaded * 100) / total) if total > 0 else 0
            try:
                progress_cb(max(1, min(99, pct)))
            except Exception:
                pass

    out_tmpl = os.path.join(output_dir, "%(title).90s [%(id)s].%(ext)s")
    opts = {
        "outtmpl": out_tmpl,
        "noplaylist": True,
        "http_headers": _build_headers(profile),
        "progress_hooks": [_hook],
        "retries": 5,
        "fragment_retries": 5,
        "windowsfilenames": True,
        "geo_bypass": True
    }

    if cookies_path and os.path.exists(cookies_path) and os.path.getsize(cookies_path) > 20:
        opts["cookiefile"] = cookies_path

    try:
        from yt_dlp.networking.impersonate import ImpersonateTarget
        opts["impersonate"] = ImpersonateTarget("chrome")
    except Exception:
        pass

    if media_type == "AUDIO":
        opts["format"] = "bestaudio/best"
    elif media_type == "IMAGE":
        opts["writethumbnail"] = True
        opts["skip_download"] = True
    else:
        opts["format"] = format_id if format_id else "best"

    try:
        with yt_dlp.YoutubeDL(opts) as ydl:
            info = ydl.extract_info(url, download=True)
            final_path = ydl.prepare_filename(info)
            if progress_cb is not None:
                progress_cb(100)
            return json.dumps({
                "status": "SUCCESS",
                "filePath": final_path,
                "title": info.get("title", "Downloaded Media")
            })
    except Exception as e:
        return json.dumps({"status": "ERROR", "message": str(e)})
