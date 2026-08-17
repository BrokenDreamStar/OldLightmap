from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def shader(path):
    return (ROOT / path).read_text()


def test_non_flash_shaders_use_the_1218_per_channel_gamma_in_the_end():
    for path in (
        "versions/mc1_21/src/main/resources/assets/oldlightmap/shaders/core/lightmap_old_1_21.fsh",
        "common/src/main/resources/assets/oldlightmap/shaders/core/lightmap_old_26_1.fsh",
    ):
        source = shader(path)
        assert "vec3 ng = notGamma(color);" in source, path
        assert "endFlashNotGamma" not in source, path


def test_flash_shaders_only_use_saturated_gamma_while_a_flash_is_active():
    for path in (
        "versions/mc1_21/src/main/resources/assets/oldlightmap/shaders/core/lightmap_old_flash_1_21.fsh",
        "common/src/main/resources/assets/oldlightmap/shaders/core/lightmap_old_flash_26_1.fsh",
    ):
        source = shader(path)
        assert "lightmapInfo.SkyFactor > 0.0" in source, path
        assert "maxComponent" in source, path
