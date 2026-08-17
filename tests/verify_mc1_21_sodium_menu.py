#!/usr/bin/env python3
"""Regression checks for the 1.21.x Sodium and Reese's options pages."""
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1]


def read_json(path):
    return json.loads((ROOT / path).read_text())


def test_mc1_21_declares_sodium_config_entrypoint():
    metadata = read_json("versions/mc1_21/src/main/resources/fabric.mod.json")
    assert metadata["entrypoints"]["sodium:config_api_user"] == [
        "oldlightmap.sodium.OldLightmapSodiumConfig"
    ]


def test_mc1_21_declares_only_the_sodium_options_mixin():
    metadata = read_json("versions/mc1_21/src/main/resources/fabric.mod.json")
    assert "oldlightmap_sodium.mixins.json" in metadata["mixins"]
    assert "oldlightmap_reeses.mixins.json" not in metadata["mixins"]


def test_optional_sodium_mixin_targets_sodium_screen():
    mixins = read_json("versions/mc1_21/src/main/resources/oldlightmap_sodium.mixins.json")
    assert mixins["required"] is False
    assert mixins["client"] == ["SodiumOptionsMixin"]


def test_sodium_page_sources_exist():
    assert (
        ROOT / "versions/mc1_21/src/main/java/oldlightmap/mixin/SodiumOptionsMixin.java"
    ).is_file()
    assert (
        ROOT / "versions/mc1_21/src/main/java/oldlightmap/sodium/OldLightmapSodiumConfig.java"
    ).is_file()
    assert (
        ROOT / "versions/mc1_21/src/main/java/oldlightmap/sodium/OldLightmapSodiumOptions.java"
    ).is_file()


def test_end_flash_defaults_to_disabled_everywhere():
    config = (ROOT / "common/src/main/java/oldlightmap/OldLightmapConfig.java").read_text()
    assert "public boolean endFlash = false;" in config
    assert "setOldLightmap" in config
    assert "setEndFlash" in config

    for version in ("mc1_21", "mc26_1", "mc26_2"):
        sodium_config = (
            ROOT / f"versions/{version}/src/main/java/oldlightmap/sodium/OldLightmapSodiumConfig.java"
        ).read_text()
        end_flash_block = sodium_config.split("BooleanOptionBuilder endFlash", 1)[1]
        assert ".setDefaultValue(Boolean.FALSE)" in end_flash_block
        assert "config.setEndFlash(value)" in end_flash_block
        assert "config.setOldLightmap(value)" in sodium_config
        assert "setEnabledProvider" in end_flash_block

    readme = (ROOT / "README.md").read_text()
    assert '{"oldLightmap":true,"endFlash":false}' in readme
    assert "| `endFlash` | `false` |" in readme

    english = json.loads((ROOT / "common/src/main/resources/assets/oldlightmap/lang/en_us.json").read_text())
    chinese = json.loads((ROOT / "common/src/main/resources/assets/oldlightmap/lang/zh_cn.json").read_text())
    assert "disabled while the old lightmap is enabled" in english["options.oldlightmap.end_flash.tooltip"]
    assert "开启旧版光照时会自动关闭" in chinese["options.oldlightmap.end_flash.tooltip"]


def test_end_flash_is_suppressed_when_old_lightmap_is_enabled():
    for path in (
        "versions/mc1_21/src/main/java/oldlightmap/mixin/LightTextureMixin.java",
        "versions/mc26_1/src/main/java/oldlightmap/mixin/LightmapRenderStateExtractorMixin.java",
        "versions/mc26_2/src/main/java/oldlightmap/mixin/LightmapRenderStateExtractorMixin.java",
    ):
        source = (ROOT / path).read_text()
        assert "if (OldLightmapConfig.isOldLightmap() || !OldLightmapConfig.isEndFlash())" in source, path


def test_legacy_reflective_options_use_the_config_invariant_setters():
    source = (
        ROOT / "versions/mc1_21/src/main/java/oldlightmap/sodium/OldLightmapSodiumOptions.java"
    ).read_text()
    assert "config.setOldLightmap(value)" in source
    assert "config.setEndFlash(value)" in source


def test_reflective_option_proxies_support_object_methods():
    source = (
        ROOT / "versions/mc1_21/src/main/java/oldlightmap/sodium/OldLightmapSodiumOptions.java"
    ).read_text()
    assert 'case "hashCode"' in source
    assert 'case "equals"' in source
    assert 'case "toString"' in source


def test_mod_author_is_broken_dream_star_in_all_distributions():
    for version in ("mc1_21", "mc26_1", "mc26_2"):
        metadata = read_json(f"versions/{version}/src/main/resources/fabric.mod.json")
        assert metadata["authors"] == ["BrokenDream_Star"]

    license_text = (ROOT / "LICENSE").read_text()
    assert "Copyright (c) 2026 BrokenDream_Star" in license_text
