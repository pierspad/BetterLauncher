#!/usr/bin/env python3
"""Publish a signed GitHub AAB, reusing it when already uploaded to Google Play."""
import argparse
import hashlib
import json
import os
from pathlib import Path
from urllib.parse import quote

PACKAGE = "com.pierspad.betterlauncher"
API = f"https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{PACKAGE}"
UPLOAD = f"https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/{PACKAGE}"


def request(session, method, url, **kwargs):
    response = session.request(method, url, timeout=180, **kwargs)
    if not response.ok:
        try:
            message = response.json()["error"]["message"]
        except (ValueError, KeyError, TypeError):
            message = "Google Play returned an unsuccessful response"
        raise RuntimeError(f"Google Play HTTP {response.status_code}: {message}")
    return response.json() if response.content else {}


PLAY_LOCALES = (
    "en-US", "it-IT", "ca", "es-ES", "es-419", "de-DE", "de", "fr-FR",
    "ru-RU", "pt-BR", "zh-CN", "ja-JP", "nl-NL", "pl-PL", "tr-TR",
    "uk", "ar", "id", "iw-IL", "hu-HU", "hr", "sv-SE",
)
LISTING_LIMITS = {"title.txt": 30, "short_description.txt": 80, "full_description.txt": 4000}


def read_listings(metadata_dir, locales):
    """Read and validate all requested listing files before opening an API edit."""
    if not locales:
        return {}
    if metadata_dir is None:
        raise ValueError("--metadata-dir is required with --metadata-locales")
    listings = {}
    for language in locales:
        if language not in PLAY_LOCALES:
            raise ValueError(f"Unsupported Play listing locale: {language}")
        values = {}
        for filename, limit in LISTING_LIMITS.items():
            path = Path(metadata_dir) / language / filename
            try:
                value = path.read_text(encoding="utf-8").strip()
            except (OSError, UnicodeError) as error:
                raise ValueError(f"Cannot read Play listing file: {path}") from error
            if not value:
                raise ValueError(f"Play listing file is empty: {path}")
            if len(value) > limit:
                raise ValueError(f"{path} exceeds the {limit}-character Play limit ({len(value)})")
            values[{"title.txt": "title", "short_description.txt": "shortDescription",
                    "full_description.txt": "fullDescription"}[filename]] = value
        listings[language] = values
    return listings


def publish(session, bundle, track, status, version_name, metadata_dir=None,
            metadata_locales=(), changes_not_sent_for_review=False):
    if track not in ("production", "beta"):
        raise ValueError("Choose production or beta (open testing)")
    if track == "production" and "-" in version_name.removeprefix("v"):
        raise ValueError("Prereleases can only be published to beta")
    if status not in ("draft", "completed"):
        raise ValueError("Release status must be draft or completed")
    desired_listings = read_listings(metadata_dir, metadata_locales) if metadata_locales else {}
    edit_id = request(session, "POST", f"{API}/edits", json={})["id"]
    edit = f"{API}/edits/{quote(edit_id, safe='')}"
    committed = False
    try:
        tracks = request(session, "GET", f"{edit}/tracks").get("tracks", [])
        names = [item["track"] for item in tracks]
        if track not in names:
            raise ValueError(f"Track {track!r} does not exist. Available tracks: {', '.join(names)}")
        digest = hashlib.sha256(Path(bundle).read_bytes()).hexdigest()
        existing = request(session, "GET", f"{edit}/bundles").get("bundles", [])
        uploaded = next((item for item in existing if item.get("sha256", "").lower() == digest), None)
        if uploaded is None:
            with Path(bundle).open("rb") as source:
                uploaded = request(
                    session, "POST", f"{UPLOAD}/edits/{quote(edit_id, safe='')}/bundles",
                    params={"uploadType": "media"},
                    headers={"Content-Type": "application/octet-stream"}, data=source,
                )
        version_code = str(uploaded["versionCode"])
        current = next(item for item in tracks if item["track"] == track)
        releases = current.get("releases", [])
        track_changed = not (len(releases) == 1 and releases[0].get("versionCodes") == [version_code]
                             and releases[0].get("status") == status)
        listings_changed = False
        if desired_listings:
            existing_listings = request(session, "GET", f"{edit}/listings").get("listings", [])
            by_language = {item["language"]: item for item in existing_listings}
            for language, desired in desired_listings.items():
                existing_listing = by_language.get(language, {})
                if any(existing_listing.get(field) != value for field, value in desired.items()):
                    merged = dict(existing_listing)
                    merged.update(desired)
                    merged["language"] = language
                    request(session, "PUT", f"{edit}/listings/{quote(language, safe='')}", json=merged)
                    listings_changed = True
        if not track_changed and not listings_changed:
            return version_code  # Already published; finally discards the read-only edit.
        if track_changed:
            release = {"name": version_name, "versionCodes": [version_code], "status": status}
            request(session, "PUT", f"{edit}/tracks/{quote(track, safe='')}",
                    json={"track": track, "releases": [release]})
        # The maintainer's policy: replace an older pending review with the latest release.
        commit_params = {}
        if changes_not_sent_for_review:
            commit_params["changesNotSentForReview"] = "true"
        else:
            commit_params["changesInReviewBehavior"] = "CANCEL_IN_REVIEW_AND_SUBMIT"
        try:
            request(session, "POST", f"{edit}:commit", params=commit_params)
        except RuntimeError as error:
            if not changes_not_sent_for_review and "changesNotSentForReview" in str(error):
                print("Notice: Automatic review rejected by Google Play; committing with changesNotSentForReview=true.")
                request(session, "POST", f"{edit}:commit",
                        params={"changesNotSentForReview": "true"})
            else:
                raise
        committed = True
        return version_code
    finally:
        if not committed:
            try:
                request(session, "DELETE", edit)
            except Exception:
                pass  # Preserve the original upload/validation error.


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bundle", required=True, type=Path)
    parser.add_argument("--track", default="beta", choices=("production", "beta"), help="production or beta (open testing)")
    parser.add_argument("--status", choices=("draft", "completed"), default="draft")
    parser.add_argument("--version-name", required=True)
    parser.add_argument("--metadata-dir", type=Path)
    parser.add_argument("--metadata-locales", nargs="+", choices=PLAY_LOCALES, default=[])
    parser.add_argument(
        "--changes-not-sent-for-review",
        action="store_true",
        default=os.environ.get("PLAY_CHANGES_NOT_SENT_FOR_REVIEW", "").lower() in ("1", "true", "yes"),
        help="Commit changes without automatically sending them for review (send from Console UI)",
    )
    args = parser.parse_args()
    if bool(args.metadata_dir) != bool(args.metadata_locales):
        parser.error("Use --metadata-dir and --metadata-locales together")
    if not args.bundle.is_file() or args.bundle.suffix != ".aab":
        parser.error("--bundle must point to a signed .aab file")
    credentials_json = os.environ.get("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON")
    if not credentials_json:
        parser.error("Set the GOOGLE_PLAY_SERVICE_ACCOUNT_JSON GitHub secret first")
    from google.oauth2 import service_account
    from google.auth.transport.requests import AuthorizedSession
    credentials = service_account.Credentials.from_service_account_info(
        json.loads(credentials_json), scopes=["https://www.googleapis.com/auth/androidpublisher"]
    )
    with AuthorizedSession(credentials) as session:
        code = publish(session, args.bundle, args.track, args.status, args.version_name,
                       args.metadata_dir, args.metadata_locales,
                       changes_not_sent_for_review=args.changes_not_sent_for_review)
    print(f"Published {PACKAGE} versionCode {code} to {args.track} ({args.status}).")
    print("Google Play review and managed publishing settings still apply.")


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, ValueError) as error:
        raise SystemExit(str(error))
