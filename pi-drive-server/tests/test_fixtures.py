"""
L1 unit tests for the test fixtures themselves.

The most important assertion here: `single_payload()` must equal the verbatim
`sample_payload.json` (the documented §5.5 contract). If the Python factory and
the JSON sample ever drift apart, this fails loudly — protecting the guarantee
that every contract test runs against the exact payload the Android app sends.
"""

from __future__ import annotations

import json
from pathlib import Path

from tests.fixtures import compression, payloads

_FIXTURE_DIR = Path(__file__).parent / "fixtures"


def test_single_payload_matches_sample_json():
    """The Python factory and the JSON sample must be identical."""
    sample = json.loads((_FIXTURE_DIR / "sample_payload.json").read_text())
    assert payloads.single_payload() == sample


def test_batch_shapes():
    """Batch helpers produce the three accepted shapes with distinct timestamps."""
    bare = payloads.batch_bare_array(3)
    assert isinstance(bare, list) and len(bare) == 3
    timestamps = {row["timestamp"] for row in bare}
    assert len(timestamps) == 3  # all distinct → distinct (vin, time) rows

    wrapped = payloads.batch_wrapped(2)
    assert list(wrapped.keys()) == ["snapshots"]
    assert len(wrapped["snapshots"]) == 2


def test_type_fidelity_payload_preserves_python_types():
    """rpm/speed stay int; throttle/voltage stay float in the source fixture."""
    p = payloads.payload_type_fidelity()
    assert isinstance(p["obd"]["rpm"], int)
    assert isinstance(p["obd"]["speed_kmh"], int)
    assert isinstance(p["obd"]["throttle_pct"], float)
    assert isinstance(p["obd"]["battery_voltage"], float)


def test_missing_vin_payload_has_no_vin():
    assert "vin" not in payloads.payload_missing_vin()


def test_zstd_roundtrip():
    """zstd_compress output decompresses back to the original JSON."""
    import zstandard as zstd

    original = compression.to_json_bytes(payloads.single_payload())
    compressed = compression.zstd_compress(original)
    assert compressed != original
    restored = zstd.ZstdDecompressor().decompress(compressed)
    assert json.loads(restored) == payloads.single_payload()
