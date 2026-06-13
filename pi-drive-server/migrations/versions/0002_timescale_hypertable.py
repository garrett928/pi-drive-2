"""timescaledb: enable extension + telemetry hypertable

Revision ID: 0002
Revises: 0001
Create Date: 2026-06-09

Enables the TimescaleDB extension and converts `telemetry` into a hypertable
partitioned on `time` (7-day chunks). The `(vin, time)` PK from 0001 includes
the partition column, satisfying Timescale's requirement.
"""

from typing import Sequence, Union

from alembic import op

from app.db import timescale

revision: str = "0002"
down_revision: Union[str, None] = "0001"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.execute(timescale.enable_extension())
    op.execute(timescale.create_hypertable("telemetry", "time", chunk_interval="7 days"))


def downgrade() -> None:
    # The hypertable is removed when the telemetry table is dropped (migration
    # 0001 downgrade). We leave the extension installed (harmless, shared).
    pass
