#!/usr/bin/env python3
"""Automated verification tests for Android string translations and Fastlane store metadata."""

import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

from publish_google_play import PLAY_LOCALES, read_listings

ROOT = Path(__file__).resolve().parent.parent
RES_DIR = ROOT / "app" / "src" / "main" / "res"
FASTLANE_DIR = ROOT / "fastlane" / "metadata" / "android"
BUILD_GRADLE = ROOT / "app" / "build.gradle"
LOCALE_CONFIG = RES_DIR / "xml" / "locales_config.xml"
LANGUAGE_PICKER = ROOT / "app/src/main/java/app/olauncher/ui/LanguagePickerFragment.kt"
ANDROID = "{http://schemas.android.com/apk/res/android}"

FORMAT_SPECIFIER_RE = re.compile(r"%(?:\d+\$)?[-+# 0,(]*\d*(?:\.\d+)?[bBhHsScCdoxXeEfgGaAtT%]")
UNESCAPED_APOSTROPHE_RE = re.compile(r"(?<!\\)'")


def normalize_format_specifiers(text: str) -> list[str]:
    """Return sorted format specifiers (converting implicit %s/%d to positional types for comparison)."""
    specs = []
    implicit_idx = 1
    for match in FORMAT_SPECIFIER_RE.finditer(text):
        token = match.group(0)
        if token == "%%":
            continue
        if "$" in token:
            pos, kind = token[1:].split("$", 1)
            specs.append(f"%{pos}${kind[-1]}")
        else:
            specs.append(f"%{implicit_idx}${token[-1]}")
            implicit_idx += 1
    return sorted(specs)


def parse_strings_xml(path: Path) -> tuple[dict[str, str], set[str], list[str]]:
    """Return (strings_by_name, non_translatable_names, duplicate_names)."""
    tree = ET.parse(path)
    root = tree.getroot()
    strings: dict[str, str] = {}
    non_translatable: set[str] = set()
    duplicates: list[str] = []
    for elem in root.findall("string"):
        name = elem.attrib.get("name")
        if not name:
            continue
        if name in strings:
            duplicates.append(name)
        if elem.attrib.get("translatable") == "false":
            non_translatable.add(name)
        strings[name] = "".join(elem.itertext())
    return strings, non_translatable, duplicates


class TranslationsAndMetadataTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.base_path = RES_DIR / "values" / "strings.xml"
        cls.base_strings, cls.non_translatable, cls.base_duplicates = parse_strings_xml(cls.base_path)
        cls.translatable_keys = {k for k in cls.base_strings if k not in cls.non_translatable}
        cls.localized_dirs = sorted(RES_DIR.glob("values-*"))
        cls.localized_files = [d / "strings.xml" for d in cls.localized_dirs if (d / "strings.xml").is_file()]

    def test_base_strings_have_no_duplicates_or_empty_values(self):
        self.assertEqual(self.base_duplicates, [], "Duplicate <string> keys in values/strings.xml")
        for key in self.translatable_keys:
            self.assertTrue(
                self.base_strings[key].strip(),
                f"Translatable string {key!r} in values/strings.xml must not be empty",
            )

    def test_shortcut_icon_catalog_is_translated(self):
        filename = "shortcut_icons.xml"
        default, _, duplicates = parse_strings_xml(RES_DIR / "values" / filename)
        self.assertFalse(duplicates)
        for locale in self.localized_files:
            with self.subTest(locale=locale.parent.name):
                translations, _, duplicates = parse_strings_xml(locale.with_name(filename))
                self.assertFalse(duplicates)
                self.assertEqual(set(default), set(translations))
                self.assertTrue(all(value.strip() for value in translations.values()))

    def test_gradle_resource_configurations_match_values_folders(self):
        gradle_text = BUILD_GRADLE.read_text(encoding="utf-8")
        match = re.search(r"resourceConfigurations\s*\+=\s*\[([^\]]+)\]", gradle_text)
        self.assertIsNotNone(match, "resourceConfigurations not found in app/build.gradle")
        configured = {item.strip().strip('"\'') for item in match.group(1).split(",") if item.strip()}
        actual_locales = {"en"} | {p.parent.name.removeprefix("values-") for p in self.localized_files}
        self.assertEqual(configured, actual_locales)

    def test_every_selectable_language_has_packaged_strings(self):
        configured = {node.get(ANDROID + "name") for node in ET.parse(LOCALE_CONFIG).iter("locale")}
        advertised = set(re.findall(r'LanguageOption\("([^"]+)"', LANGUAGE_PICKER.read_text(encoding="utf-8")))
        packaged = {"en"} | {
            path.parent.name.removeprefix("values-").replace("-r", "-") for path in self.localized_files
        }
        self.assertEqual(configured, advertised, "Android and in-app language pickers must agree")
        self.assertFalse(advertised - packaged, f"Selectable languages without translations: {sorted(advertised - packaged)}")

    def test_layouts_use_resources_for_user_visible_text(self):
        attributes = ("text", "hint", "contentDescription", "textOff", "textOn")
        literals = []
        for path in sorted(RES_DIR.glob("layout*/*.xml")):
            for node in ET.parse(path).iter():
                for attribute in attributes:
                    value = node.get(ANDROID + attribute, "").strip()
                    if value and not value.startswith(("@", "?")) and any(char.isalpha() for char in value):
                        literals.append(f"{path.relative_to(RES_DIR)} {attribute}={value!r}")
        self.assertEqual(literals, [], "User-facing XML strings must use resources")

    def test_all_locales_have_complete_and_clean_translations(self):
        for path in self.localized_files:
            locale = path.parent.name
            with self.subTest(locale=locale):
                strings, _, duplicates = parse_strings_xml(path)
                self.assertEqual(duplicates, [], f"{locale}: duplicate string keys {duplicates}")

                missing = sorted(self.translatable_keys - set(strings))
                self.assertEqual(missing, [], f"{locale}: missing translations for {missing}")

                extra = sorted(set(strings) - set(self.base_strings))
                self.assertEqual(extra, [], f"{locale}: orphaned keys not in values/strings.xml: {extra}")

                forbidden = sorted(set(strings) & self.non_translatable)
                self.assertEqual(
                    forbidden,
                    [],
                    f"{locale}: overrides non-translatable keys {forbidden}",
                )

                raw_lines = path.read_text(encoding="utf-8")
                for key, value in strings.items():
                    self.assertTrue(value.strip(), f"{locale}: empty translation for {key!r}")
                    base_specs = normalize_format_specifiers(self.base_strings[key])
                    loc_specs = normalize_format_specifiers(value)
                    self.assertEqual(
                        base_specs,
                        loc_specs,
                        f"{locale}: format specifier mismatch in {key!r}: expected {base_specs}, got {loc_specs}",
                    )
                # Ensure raw XML text does not contain unescaped apostrophes outside quoted strings
                for m in re.finditer(r"<string\s+[^>]*>(.*?)</string>", raw_lines, flags=re.DOTALL):
                    body = m.group(1).strip()
                    if not (body.startswith('"') and body.endswith('"')):
                        self.assertIsNone(
                            UNESCAPED_APOSTROPHE_RE.search(body),
                            f"{locale}: unescaped apostrophe in XML segment {body[:60]!r}",
                        )

    def test_settings_search_tags_reference_translated_strings(self):
        android = "{http://schemas.android.com/apk/res/android}"
        for layout in sorted(RES_DIR.glob("layout*/fragment_settings.xml")):
            tags = [
                elem.get(android + "tag", "")
                for elem in ET.parse(layout).iter()
            ]
            keys = {
                key
                for tag in tags
                if tag.startswith("settings:")
                for key in tag.removeprefix("settings:").split(",")
            }
            with self.subTest(layout=layout.parent.name):
                self.assertTrue(keys, "Settings must expose localized search tags")
                self.assertFalse(
                    keys - self.base_strings.keys(),
                    f"Search keys missing string resources: {sorted(keys - self.base_strings.keys())}",
                )

    def test_hebrew_folders_are_synchronized(self):
        he_strings, _, _ = parse_strings_xml(RES_DIR / "values-he" / "strings.xml")
        iw_strings, _, _ = parse_strings_xml(RES_DIR / "values-iw" / "strings.xml")
        self.assertEqual(he_strings, iw_strings, "values-he and values-iw must stay identical")

    def test_fastlane_metadata_listings_and_screenshots(self):
        listings = read_listings(FASTLANE_DIR, PLAY_LOCALES)
        self.assertEqual(set(listings), set(PLAY_LOCALES))

        actual_dirs = {
            p.name for p in FASTLANE_DIR.iterdir() if p.is_dir()
        }
        self.assertEqual(actual_dirs, set(PLAY_LOCALES) | {"de"})
        # F-Droid also serves a language-only German listing.
        for name in ("title.txt", "short_description.txt", "full_description.txt"):
            self.assertEqual(
                (FASTLANE_DIR / "de" / name).read_text(encoding="utf-8"),
                (FASTLANE_DIR / "de-DE" / name).read_text(encoding="utf-8"),
            )

        for locale, data in listings.items():
            with self.subTest(locale=locale):
                self.assertEqual(data["title"], "BetterLauncher")

        # Verify store images live under en-US (used as F-Droid/Play fallback) and are non-empty
        images_dir = FASTLANE_DIR / "en-US" / "images"
        self.assertTrue((images_dir / "icon.png").is_file())
        self.assertTrue((images_dir / "featureGraphic.jpg").is_file())
        screenshots = sorted((images_dir / "phoneScreenshots").glob("*.jpg"))
        self.assertEqual(
            [p.name for p in screenshots],
            ["01.jpg", "02.jpg", "03.jpg", "04.jpg"],
        )
        for shot in screenshots:
            self.assertGreater(
                shot.stat().st_size,
                50_000,
                f"Screenshot {shot} is unexpectedly small ({shot.stat().st_size} bytes)",
            )

        # Ensure no other locale folder has stale/leftover phoneScreenshots overriding en-US
        for locale_dir in FASTLANE_DIR.iterdir():
            if locale_dir.is_dir() and locale_dir.name != "en-US":
                extra_shots = list((locale_dir / "images" / "phoneScreenshots").glob("*")) if (locale_dir / "images" / "phoneScreenshots").exists() else []
                self.assertEqual(
                    extra_shots,
                    [],
                    f"Unexpected localized screenshots in {locale_dir.name}: {extra_shots}",
                )


if __name__ == "__main__":
    unittest.main()
