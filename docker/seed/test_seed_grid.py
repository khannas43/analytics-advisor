"""Fast unit checks for seed row grid (not run from mvn test)."""
from seed import _grid_factors, build_insert_select


def test_grid_supports_10_million():
    a, b = _grid_factors(10_000_000)
    assert a * b >= 10_000_000
    assert b <= 10_000


def test_insert_select_references_row_cap():
    sql = build_insert_select(200_000, "realistic")
    assert "WHERE g.id <= 200000" in sql
    assert "district_code" in sql
