"""base schema: vehicles, telemetry, driving_events

Revision ID: 0001
Revises:
Create Date: 2026-06-09

Creates the three core tables (REQUIREMENTS.md §4) with FKs and indexes. The
telemetry table is converted into a TimescaleDB hypertable in migration 0002.
"""

from typing import Sequence, Union

import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

revision: str = "0001"
down_revision: Union[str, None] = None
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    # ── vehicles ──────────────────────────────────────────────────────────────
    op.create_table(
        "vehicles",
        sa.Column("vin", sa.Text(), nullable=False),
        sa.Column("device_id", sa.Text(), nullable=True),
        sa.Column("make", sa.Text(), nullable=True),
        sa.Column("model", sa.Text(), nullable=True),
        sa.Column("year", sa.Integer(), nullable=True),
        sa.Column("nickname", sa.Text(), nullable=True),
        sa.Column("first_seen", sa.DateTime(timezone=True), nullable=True),
        sa.Column("last_seen", sa.DateTime(timezone=True), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
        sa.PrimaryKeyConstraint("vin"),
    )

    # ── telemetry (becomes a hypertable in 0002) ──────────────────────────────
    op.create_table(
        "telemetry",
        sa.Column("vin", sa.Text(), nullable=False),
        sa.Column("time", sa.DateTime(timezone=True), nullable=False),
        sa.Column("device_id", sa.Text(), nullable=True),
        sa.Column("lat", sa.Double(), nullable=True),
        sa.Column("lng", sa.Double(), nullable=True),
        sa.Column("speed_gps", sa.Double(), nullable=True),
        sa.Column("speed_kmh", sa.Double(), nullable=True),
        sa.Column("rpm", sa.Integer(), nullable=True),
        sa.Column("coolant_temp_c", sa.Double(), nullable=True),
        sa.Column("intake_air_temp_c", sa.Double(), nullable=True),
        sa.Column("throttle_pct", sa.Double(), nullable=True),
        sa.Column("fuel_level_pct", sa.Double(), nullable=True),
        sa.Column("oil_temp_c", sa.Double(), nullable=True),
        sa.Column("maf_gps", sa.Double(), nullable=True),
        sa.Column("fuel_rate_lph", sa.Double(), nullable=True),
        sa.Column("battery_voltage", sa.Double(), nullable=True),
        sa.Column("fuel_economy_mpg", sa.Double(), nullable=True),
        sa.Column("fuel_economy_kml", sa.Double(), nullable=True),
        sa.Column("accel_mps2", sa.Double(), nullable=True),
        sa.Column("extra", postgresql.JSONB(), nullable=True),
        sa.Column("source", sa.Text(), server_default="device", nullable=False),
        sa.ForeignKeyConstraint(["vin"], ["vehicles.vin"], ondelete="CASCADE"),
        # (vin, time) composite PK = the idempotency key; includes the partition
        # column `time`, which TimescaleDB requires for the hypertable in 0002.
        sa.PrimaryKeyConstraint("vin", "time"),
    )
    # Optimizes "latest snapshot for a vin" (ORDER BY time DESC LIMIT 1).
    op.execute("CREATE INDEX ix_telemetry_vin_time_desc ON telemetry (vin, time DESC);")

    # ── driving_events ────────────────────────────────────────────────────────
    op.create_table(
        "driving_events",
        sa.Column("id", sa.BigInteger(), autoincrement=True, nullable=False),
        sa.Column("vin", sa.Text(), nullable=False),
        sa.Column("time", sa.DateTime(timezone=True), nullable=False),
        sa.Column("strategy", sa.Text(), nullable=False),
        sa.Column("type", sa.Text(), nullable=False),
        sa.Column("duration_ms", sa.Integer(), nullable=True),
        sa.Column("rate_mph_s", sa.Double(), nullable=True),
        sa.Column("peak_g", sa.Double(), nullable=True),
        sa.Column("peak_accel_mps2", sa.Double(), nullable=True),
        sa.Column("start_speed_mph", sa.Double(), nullable=True),
        sa.Column("end_speed_mph", sa.Double(), nullable=True),
        sa.Column("sources", postgresql.ARRAY(sa.Text()), nullable=True),
        sa.Column("source", sa.Text(), server_default="device", nullable=False),
        sa.ForeignKeyConstraint(["vin"], ["vehicles.vin"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index("ix_driving_events_vin_time", "driving_events", ["vin", "time"])


def downgrade() -> None:
    op.drop_index("ix_driving_events_vin_time", table_name="driving_events")
    op.drop_table("driving_events")
    op.execute("DROP INDEX IF EXISTS ix_telemetry_vin_time_desc;")
    op.drop_table("telemetry")
    op.drop_table("vehicles")
