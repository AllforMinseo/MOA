"""
Runtime schema compatibility helpers.

The project currently uses SQLAlchemy ``create_all`` instead of a migration tool.
``create_all`` creates missing tables, but it does not add columns to tables that
already exist on a deployed server. These small compatibility migrations keep old
local/server databases usable after model fields are added.
"""

from __future__ import annotations

from collections.abc import Iterable

from sqlalchemy import inspect, text
from sqlalchemy.engine import Engine


def _existing_columns(engine: Engine, table_name: str) -> set[str]:
    inspector = inspect(engine)
    if not inspector.has_table(table_name):
        return set()
    return {column["name"] for column in inspector.get_columns(table_name)}


def _existing_indexes(engine: Engine, table_name: str) -> set[str]:
    inspector = inspect(engine)
    if not inspector.has_table(table_name):
        return set()
    return {index["name"] for index in inspector.get_indexes(table_name)}


def _add_missing_columns(
    engine: Engine,
    table_name: str,
    columns: Iterable[tuple[str, str, str]],
) -> None:
    existing = _existing_columns(engine, table_name)
    if not existing:
        return

    dialect = engine.dialect.name
    with engine.begin() as connection:
        for column_name, sqlite_definition, mysql_definition in columns:
            if column_name in existing:
                continue

            definition = mysql_definition if dialect == "mysql" else sqlite_definition
            connection.execute(text(f"ALTER TABLE {table_name} ADD COLUMN {definition}"))
            print(f"[DB] Added missing column: {table_name}.{column_name}")


def _backfill_updated_at(engine: Engine, table_name: str) -> None:
    columns = _existing_columns(engine, table_name)
    if "updated_at" not in columns or "created_at" not in columns:
        return

    with engine.begin() as connection:
        connection.execute(
            text(
                f"""
                UPDATE {table_name}
                SET updated_at = created_at
                WHERE updated_at IS NULL
                """
            )
        )


def _ensure_meetings_user_index(engine: Engine) -> None:
    if "user_id" not in _existing_columns(engine, "meetings"):
        return
    if "ix_meetings_user_id" in _existing_indexes(engine, "meetings"):
        return

    with engine.begin() as connection:
        connection.execute(text("CREATE INDEX ix_meetings_user_id ON meetings (user_id)"))
        print("[DB] Added missing index: ix_meetings_user_id")


def ensure_schema_compatibility(engine: Engine) -> None:
    """
    Apply non-destructive compatibility updates for already-created databases.

    This is intentionally small in scope. It only adds nullable/defaulted columns
    required by the current ORM models so deployed SQLite/MySQL databases from an
    older branch do not fail with 500 errors after authentication/meeting changes.
    """

    _add_missing_columns(
        engine,
        "meetings",
        (
            ("user_id", "user_id INTEGER", "user_id INT NULL"),
            ("description", "description TEXT", "description TEXT NULL"),
            ("updated_at", "updated_at DATETIME", "updated_at DATETIME NULL"),
        ),
    )
    _ensure_meetings_user_index(engine)
    _backfill_updated_at(engine, "meetings")

    _add_missing_columns(
        engine,
        "summaries",
        (
            ("updated_at", "updated_at DATETIME", "updated_at DATETIME NULL"),
        ),
    )
    _backfill_updated_at(engine, "summaries")

    _add_missing_columns(
        engine,
        "transcripts",
        (
            ("updated_at", "updated_at DATETIME", "updated_at DATETIME NULL"),
        ),
    )
    _backfill_updated_at(engine, "transcripts")

    _add_missing_columns(
        engine,
        "images",
        (
            ("image_type", "image_type VARCHAR(50) DEFAULT 'image'", "image_type VARCHAR(50) NULL DEFAULT 'image'"),
            ("analysis_text", "analysis_text TEXT", "analysis_text TEXT NULL"),
            ("updated_at", "updated_at DATETIME", "updated_at DATETIME NULL"),
        ),
    )
    _backfill_updated_at(engine, "images")
