import argparse
import hashlib
import json
import math
import statistics
import sys
import time
import urllib.error
import urllib.request
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


BASE_DIR = Path(__file__).resolve().parent
DEFAULT_DATASET = BASE_DIR / "evaluation" / "recommendation_queries_v1.json"
DEFAULT_OUTPUT = BASE_DIR / "evaluation" / "results" / "recommendation_evaluation_latest.json"


def _arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Run the FOODUCK natural-language recommendation golden evaluation set."
    )
    parser.add_argument("--dataset", type=Path, default=DEFAULT_DATASET)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--base-url", default="http://127.0.0.1:8081")
    parser.add_argument("--ai-health-url", default="http://127.0.0.1:8000/embedding/health")
    parser.add_argument("--timeout", type=float, default=30.0)
    parser.add_argument("--ids", help="Comma-separated case IDs to run")
    parser.add_argument("--strict", action="store_true", help="Exit non-zero when a check fails")
    return parser.parse_args()


def _request_json(url: str, payload: dict[str, Any] | None, timeout: float) -> tuple[int, Any]:
    body = None if payload is None else json.dumps(payload, ensure_ascii=False).encode("utf-8")
    request = urllib.request.Request(
        url,
        data=body,
        method="GET" if payload is None else "POST",
        headers={"Content-Type": "application/json; charset=utf-8"},
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            raw = response.read().decode("utf-8")
            return response.status, json.loads(raw)
    except urllib.error.HTTPError as error:
        raw = error.read().decode("utf-8", errors="replace")
        try:
            parsed = json.loads(raw)
        except json.JSONDecodeError:
            parsed = {"message": raw}
        return error.code, parsed


def _percentile(values: list[float], percentile: float) -> float | None:
    if not values:
        return None
    ordered = sorted(values)
    position = (len(ordered) - 1) * percentile
    lower = math.floor(position)
    upper = math.ceil(position)
    if lower == upper:
        return ordered[lower]
    return ordered[lower] + (ordered[upper] - ordered[lower]) * (position - lower)


def _check(name: str, passed: bool, expected: Any = None, actual: Any = None) -> dict[str, Any]:
    return {
        "name": name,
        "passed": bool(passed),
        "expected": expected,
        "actual": actual,
    }


def _evaluate_case(
    case: dict[str, Any],
    defaults: dict[str, Any],
    endpoint: str,
    timeout: float,
) -> dict[str, Any]:
    request_payload = dict(defaults)
    request_payload.update(case.get("request", {}))
    request_payload["query"] = case["query"]
    expected = case.get("expected", {})

    started = time.perf_counter()
    try:
        status, envelope = _request_json(endpoint, request_payload, timeout)
        elapsed_ms = round((time.perf_counter() - started) * 1000, 1)
    except Exception as error:
        elapsed_ms = round((time.perf_counter() - started) * 1000, 1)
        return {
            "id": case["id"],
            "group": case["group"],
            "query": case["query"],
            "supportLevel": case["supportLevel"],
            "request": request_payload,
            "expected": expected,
            "httpStatus": None,
            "elapsedMs": elapsed_ms,
            "passed": False,
            "checks": [_check("request_completed", False, True, str(error))],
            "issues": ["REQUEST_FAILED"],
        }

    data = envelope.get("data") if isinstance(envelope, dict) else None
    data = data if isinstance(data, dict) else {}
    parsed = data.get("parsedQuery") if isinstance(data.get("parsedQuery"), dict) else {}
    items = data.get("items") if isinstance(data.get("items"), list) else []
    relaxed = data.get("relaxedFilters") if isinstance(data.get("relaxedFilters"), list) else []
    resolved = data.get("resolvedConstraints") \
        if isinstance(data.get("resolvedConstraints"), list) else []
    checks: list[dict[str, Any]] = []

    checks.append(_check("http_status", status == 200, 200, status))
    checks.append(_check("api_success", envelope.get("success") is True, True, envelope.get("success")))

    expected_location = expected.get("location")
    if expected_location:
        checks.append(
            _check("parsed_location", parsed.get("locationText") == expected_location,
                   expected_location, parsed.get("locationText"))
        )
        checks.append(
            _check("location_resolved", parsed.get("locationResolved") is True,
                   True, parsed.get("locationResolved"))
        )

    expected_category = expected.get("category")
    if expected_category:
        actual_category = parsed.get("categoryMedium") or parsed.get("category")
        checks.append(
            _check("parsed_category", actual_category == expected_category,
                   expected_category, actual_category)
        )

    expected_excluded = expected.get("excludedCategories", [])
    if expected_excluded:
        actual_excluded = parsed.get("excludedCategories", [])
        checks.append(
            _check(
                "parsed_excluded_categories",
                set(expected_excluded).issubset(set(actual_excluded)),
                expected_excluded,
                actual_excluded,
            )
        )

    expected_resolved = expected.get("resolvedConstraints", [])
    if expected_resolved:
        checks.append(
            _check(
                "resolved_constraints",
                set(expected_resolved).issubset(set(resolved)),
                expected_resolved,
                resolved,
            )
        )
    expected_resolved_any = expected.get("resolvedConstraintAny", [])
    if expected_resolved_any:
        checks.append(
            _check(
                "resolved_constraint_any",
                bool(set(expected_resolved_any).intersection(resolved)),
                expected_resolved_any,
                resolved,
            )
        )

    effective_radius = expected.get("radiusMeters", request_payload.get("radiusMeters"))
    checks.append(
        _check("effective_radius", parsed.get("radiusMeters") == effective_radius,
               effective_radius, parsed.get("radiusMeters"))
    )

    if "maxPrice" in expected:
        checks.append(
            _check(
                "parsed_max_price",
                parsed.get("maxPrice") == expected["maxPrice"],
                expected["maxPrice"],
                parsed.get("maxPrice"),
            )
        )

    minimum_results = expected.get("minimumResults", 1)
    checks.append(_check("minimum_results", len(items) >= minimum_results, minimum_results, len(items)))

    scores_valid = all(
        isinstance(item.get("score"), (int, float)) and 0.0 <= item["score"] <= 1.0
        for item in items
    )
    checks.append(_check("score_range", scores_valid, "0.0 <= score <= 1.0", None))

    distances = [item.get("distanceMeters") for item in items if item.get("distanceMeters") is not None]
    distance_valid = all(distance <= effective_radius + 2.0 for distance in distances)
    checks.append(_check("distance_compliance", distance_valid, f"<= {effective_radius}m", distances))

    category_relaxed = "CATEGORY" in relaxed
    if expected_category:
        checks.append(_check("category_not_relaxed", not category_relaxed, False, category_relaxed))
    if expected_category and not category_relaxed:
        actual_categories = [item.get("categoryName") for item in items]
        checks.append(
            _check(
                "result_category_compliance",
                all(value == expected_category for value in actual_categories),
                expected_category,
                actual_categories,
            )
        )

    if expected_excluded:
        actual_categories = [item.get("categoryName") for item in items]
        checks.append(
            _check(
                "result_excluded_category_compliance",
                not any(value in expected_excluded for value in actual_categories),
                f"not in {expected_excluded}",
                actual_categories,
            )
        )

    if case["supportLevel"] == "UNSUPPORTED":
        acknowledged = (
            any(str(value).endswith("_DATA_UNAVAILABLE") for value in relaxed)
            or bool(resolved)
        )
        checks.append(_check(
            "unsupported_acknowledged",
            acknowledged,
            True,
            {"relaxedFilters": relaxed, "resolvedConstraints": resolved},
        ))

    issues = [check["name"].upper() for check in checks if not check["passed"]]
    if data.get("fallback"):
        issues.append("ENGINE_FALLBACK")
    if category_relaxed:
        issues.append("CATEGORY_RELAXED")

    semantic_raw_scores = [
        item.get("semanticRawScore")
        for item in items
        if isinstance(item.get("semanticRawScore"), (int, float))
    ]
    return {
        "id": case["id"],
        "group": case["group"],
        "query": case["query"],
        "supportLevel": case["supportLevel"],
        "request": request_payload,
        "expected": expected,
        "httpStatus": status,
        "elapsedMs": elapsed_ms,
        "passed": not any(not check["passed"] for check in checks),
        "checks": checks,
        "issues": list(dict.fromkeys(issues)),
        "parsedQuery": parsed,
        "engine": {
            "used": data.get("engineUsed"),
            "fallback": bool(data.get("fallback")),
            "fallbackReason": data.get("fallbackReason"),
            "indexVersion": data.get("indexVersion"),
            "documentVersion": data.get("documentVersion"),
            "candidateCount": data.get("candidateCount"),
            "relaxedFilters": relaxed,
            "resolvedConstraints": resolved,
            "semanticDiagnostics": data.get("semanticDiagnostics"),
        },
        "semanticRawScoreRange": {
            "min": min(semantic_raw_scores) if semantic_raw_scores else None,
            "max": max(semantic_raw_scores) if semantic_raw_scores else None,
        },
        "items": items,
    }


def _summary(results: list[dict[str, Any]]) -> dict[str, Any]:
    elapsed = [float(result["elapsedMs"]) for result in results]
    issue_counts = Counter(issue for result in results for issue in result.get("issues", []))
    relaxed_counts = Counter(
        value
        for result in results
        for value in result.get("engine", {}).get("relaxedFilters", [])
    )
    resolved_counts = Counter(
        value
        for result in results
        for value in result.get("engine", {}).get("resolvedConstraints", [])
    )
    evidence_tag_counts = Counter(
        value
        for result in results
        for item in result.get("items", [])
        for value in (
            item.get("evidenceTags")
            if isinstance(item.get("evidenceTags"), list) else []
        )
    )
    evidence_source_counts = Counter(
        value
        for result in results
        for item in result.get("items", [])
        for value in (
            item.get("evidenceSources")
            if isinstance(item.get("evidenceSources"), list) else []
        )
    )
    group_counts: dict[str, dict[str, int]] = {}
    for result in results:
        group = result["group"]
        stats = group_counts.setdefault(group, {"total": 0, "passed": 0, "failed": 0})
        stats["total"] += 1
        if result["passed"]:
            stats["passed"] += 1
        else:
            stats["failed"] += 1

    check_totals: Counter[str] = Counter()
    check_passes: Counter[str] = Counter()
    for result in results:
        for check in result.get("checks", []):
            check_totals[check["name"]] += 1
            if check["passed"]:
                check_passes[check["name"]] += 1

    check_rates = {
        name: {
            "passed": check_passes[name],
            "total": total,
            "rate": round(check_passes[name] / total, 4) if total else None,
        }
        for name, total in sorted(check_totals.items())
    }
    return {
        "total": len(results),
        "passed": sum(1 for result in results if result["passed"]),
        "failed": sum(1 for result in results if not result["passed"]),
        "fallbackCount": sum(1 for result in results if result.get("engine", {}).get("fallback")),
        "emptyResultCount": sum(1 for result in results if not result.get("items")),
        "latencyMs": {
            "mean": round(statistics.fmean(elapsed), 1) if elapsed else None,
            "p50": round(_percentile(elapsed, 0.50), 1) if elapsed else None,
            "p95": round(_percentile(elapsed, 0.95), 1) if elapsed else None,
            "max": round(max(elapsed), 1) if elapsed else None,
        },
        "checkRates": check_rates,
        "issues": dict(issue_counts.most_common()),
        "relaxedFilters": dict(relaxed_counts.most_common()),
        "resolvedConstraints": dict(resolved_counts.most_common()),
        "evidenceTags": dict(evidence_tag_counts.most_common()),
        "evidenceSources": dict(evidence_source_counts.most_common()),
        "groups": group_counts,
    }


def _improvement_candidates(summary: dict[str, Any]) -> list[dict[str, Any]]:
    issue_guidance = {
        "PARSED_LOCATION": "Extend location aliases or phrase extraction.",
        "LOCATION_RESOLVED": "Inspect Kakao geocoding coverage, trusted categories, or the location alias.",
        "PARSED_CATEGORY": "Extend the category alias ontology using the public category taxonomy.",
        "PARSED_EXCLUDED_CATEGORIES": "Improve category negation and exclusion parsing.",
        "EFFECTIVE_RADIUS": "Improve textual distance and walking-time parsing.",
        "RESULT_CATEGORY_COMPLIANCE": "Inspect candidate category filtering or category relaxation.",
        "CATEGORY_NOT_RELAXED": "Review an overly strict small-category mapping or missing nearby category data.",
        "RESULT_EXCLUDED_CATEGORY_COMPLIANCE": "Apply excluded categories before candidate limiting.",
        "DISTANCE_COMPLIANCE": "Inspect geocoding and exact Haversine radius filtering.",
        "MINIMUM_RESULTS": "Inspect location resolution, candidate recall, and category relaxation.",
        "RESOLVED_CONSTRAINTS": "Inspect official evidence coverage or the deterministic evidence filter.",
        "RESOLVED_CONSTRAINT_ANY": "Inspect rating-source coverage or the deterministic evidence filter.",
        "ENGINE_FALLBACK": "Inspect KURE health, timeout, and index compatibility.",
    }
    improvements: list[dict[str, Any]] = []
    for issue, count in summary.get("issues", {}).items():
        guidance = issue_guidance.get(issue)
        if guidance:
            improvements.append({"source": "failedCheck", "code": issue, "count": count, "action": guidance})

    relaxed_guidance = {
        "ATMOSPHERE_DATA_UNAVAILABLE": "Add evidence-backed atmosphere tags before rebuilding Document V2.",
        "SUITABILITY_DATA_UNAVAILABLE": "Collect evidence-backed companion and occasion suitability tags.",
        "AMENITY_DATA_UNAVAILABLE": "Add structured amenity data such as parking and accessibility.",
        "HOURS_DATA_UNAVAILABLE": "Add structured opening-hours data and current-open filtering.",
        "MENU_ATTRIBUTE_DATA_UNAVAILABLE": "Add verified menu and dietary attribute data.",
        "PRICE_DATA_UNAVAILABLE": "Add comparable public-restaurant menu price data.",
        "RATING_DATA_UNAVAILABLE": "Increase review coverage before enabling rating filters.",
        "SEMANTIC_EVIDENCE_LOW": "Inspect raw KURE scores and enrich documents instead of trusting relative rank alone.",
    }
    for code, count in summary.get("relaxedFilters", {}).items():
        guidance = relaxed_guidance.get(code)
        if guidance:
            improvements.append({"source": "dataGap", "code": code, "count": count, "action": guidance})
    return improvements


def main() -> int:
    args = _arguments()
    dataset_path = args.dataset.resolve()
    output_path = args.output.resolve()
    dataset_bytes = dataset_path.read_bytes()
    dataset = json.loads(dataset_bytes.decode("utf-8"))
    cases = dataset.get("cases", [])
    selected_ids = None
    if args.ids:
        selected_ids = {value.strip() for value in args.ids.split(",") if value.strip()}
        cases = [case for case in cases if case.get("id") in selected_ids]
        missing = selected_ids - {case.get("id") for case in cases}
        if missing:
            raise ValueError(f"Unknown evaluation case IDs: {', '.join(sorted(missing))}")
    if not cases:
        raise ValueError("The evaluation dataset contains no selected cases.")

    health_status = None
    health_body: Any = None
    try:
        health_status, health_body = _request_json(args.ai_health_url, None, args.timeout)
    except Exception as error:
        health_body = {"error": str(error)}

    endpoint = args.base_url.rstrip("/") + "/api/recommendations/query"
    results = [
        _evaluate_case(case, dataset["defaults"], endpoint, args.timeout)
        for case in cases
    ]
    summary = _summary(results)
    generated_at = datetime.now(timezone.utc)
    report_id = generated_at.strftime("%Y%m%dT%H%M%SZ")
    report = {
        "reportVersion": 1,
        "reportId": report_id,
        "generatedAt": generated_at.isoformat(),
        "dataset": {
            "path": str(dataset_path),
            "version": dataset.get("datasetVersion"),
            "sha256": hashlib.sha256(dataset_bytes).hexdigest(),
            "selectedIds": sorted(selected_ids) if selected_ids else None,
        },
        "environment": {
            "recommendationEndpoint": endpoint,
            "aiHealthStatus": health_status,
            "aiHealth": health_body,
        },
        "summary": summary,
        "improvementCandidates": _improvement_candidates(summary),
        "results": results,
    }
    output_path.parent.mkdir(parents=True, exist_ok=True)
    report_json = json.dumps(report, ensure_ascii=False, indent=2) + "\n"
    output_path.write_text(report_json, encoding="utf-8")
    history_path = None
    if output_path == DEFAULT_OUTPUT.resolve():
        history_path = output_path.with_name(f"recommendation_evaluation_{report_id}.json")
        history_path.write_text(report_json, encoding="utf-8")

    print(
        f"Evaluation completed: total={summary['total']} passed={summary['passed']} "
        f"failed={summary['failed']} fallback={summary['fallbackCount']} "
        f"p95Ms={summary['latencyMs']['p95']}"
    )
    print(f"JSON report: {output_path}")
    if history_path is not None:
        print(f"Historical report: {history_path}")
    return 1 if args.strict and summary["failed"] else 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:
        print(f"[ERROR] {error}", file=sys.stderr)
        raise SystemExit(2)
