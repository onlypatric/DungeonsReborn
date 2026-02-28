"""Core primitives for Builder v2."""

from __future__ import annotations

from dataclasses import dataclass, field
from difflib import get_close_matches
from typing import Any, Dict, Iterable, Optional, Sequence

from .internal.normalize import snake_case
from .types import BuildProfile, DomainName

KNOWN_DOMAINS: tuple[DomainName, ...] = (
    "ability",
    "item",
    "mob",
    "recipe",
    "shop",
    "quest",
    "upgrade",
    "class",
    "bundle",
)


class BuildValidationError(ValueError):
    """Raised when v2 strict validation fails."""


@dataclass(frozen=True)
class Ref:
    symbol: str

    def __str__(self) -> str:
        return self.symbol


@dataclass
class BuildContext:
    strict: bool = True
    profile: BuildProfile = "dev"
    _symbol_to_id: Dict[str, str] = field(default_factory=dict)
    _domain_ids: Dict[str, set[str]] = field(default_factory=dict)
    _id_to_symbols: Dict[str, set[str]] = field(default_factory=dict)
    _diagnostics: list[str] = field(default_factory=list)

    def __post_init__(self) -> None:
        for domain in KNOWN_DOMAINS:
            self._domain_ids.setdefault(domain, set())

    def _fail(self, message: str) -> None:
        if self.strict:
            raise BuildValidationError(message)
        self._diagnostics.append(message)

    def diagnostics(self) -> list[str]:
        return list(self._diagnostics)

    def assert_valid(self) -> None:
        if self._diagnostics:
            raise BuildValidationError("\n".join(self._diagnostics))

    def ref(self, symbol: str, *, domain: Optional[DomainName] = None) -> Ref:
        if domain and "." not in symbol:
            symbol = f"{domain}.{symbol}"
        return Ref(symbol=self._normalize_symbol_ref(symbol))

    def auto_id(self, domain: DomainName, *parts: str) -> str:
        norm_domain = self._normalize_domain(domain)
        tokens: list[str] = [norm_domain]
        for raw in parts:
            token = snake_case(str(raw))
            if token:
                tokens.append(token)
        candidate = snake_case("_".join(tokens))
        if not candidate:
            self._fail(f"auto id generation failed: domain={domain!r} parts={parts!r}")
            return ""
        return candidate

    def register(
        self,
        domain: DomainName,
        *,
        symbol: Optional[str] = None,
        id_override: Optional[str] = None,
        parts: Sequence[str] = (),
    ) -> tuple[str, str]:
        norm_domain = self._normalize_domain(domain)
        canonical_symbol = self._canonical_symbol(norm_domain, symbol=symbol, parts=parts, id_override=id_override)
        final_id = snake_case(id_override) if id_override else self.auto_id(norm_domain, *self._symbol_tail_tokens(canonical_symbol))
        if not final_id:
            self._fail(f"{norm_domain}: generated empty id for symbol {canonical_symbol!r}")
            return "", canonical_symbol

        existing_id = self._symbol_to_id.get(canonical_symbol)
        if existing_id and existing_id != final_id:
            self._fail(
                f"{norm_domain}: symbol collision for {canonical_symbol!r}; already mapped to id={existing_id!r}, got id={final_id!r}"
            )
            return existing_id, canonical_symbol

        domain_ids = self._domain_ids.setdefault(norm_domain, set())
        if final_id in domain_ids:
            symbol_key = f"{norm_domain}:{final_id}"
            existing_symbols = sorted(self._id_to_symbols.get(symbol_key, set()))
            if canonical_symbol not in existing_symbols:
                self._fail(
                    f"{norm_domain}: id collision for {final_id!r}; already used by symbols={existing_symbols or ['<unknown>']}"
                )
                return final_id, canonical_symbol

        self._symbol_to_id[canonical_symbol] = final_id
        self._domain_ids.setdefault(norm_domain, set()).add(final_id)
        key = f"{norm_domain}:{final_id}"
        self._id_to_symbols.setdefault(key, set()).add(canonical_symbol)
        return final_id, canonical_symbol

    def resolve(
        self,
        value: Any,
        *,
        domain: Optional[DomainName] = None,
        field: Optional[str] = None,
        allow_external: bool = False,
    ) -> str:
        norm_domain = self._normalize_domain(domain) if domain else None
        if isinstance(value, Ref):
            return self._resolve_symbol(value.symbol, domain=norm_domain, field=field)

        if hasattr(value, "symbol") and hasattr(value, "id"):
            symbol = getattr(value, "symbol")
            if isinstance(symbol, str):
                return self._resolve_symbol(symbol, domain=norm_domain, field=field)

        if isinstance(value, str):
            raw = value.strip()
            if not raw:
                self._fail(self._field_message(field, "empty reference value"))
                return ""
            if self._looks_like_symbol(raw, norm_domain):
                return self._resolve_symbol(raw, domain=norm_domain, field=field)
            resolved_id = snake_case(raw)
            if norm_domain:
                known = self._domain_ids.get(norm_domain, set())
                if resolved_id in known:
                    return resolved_id
                if not allow_external:
                    self._fail(
                        self._field_message(
                            field,
                            f"unknown {norm_domain} id={resolved_id!r}; use Ref('{norm_domain}.<symbol>') or define it in this pack",
                        )
                    )
                    return ""
            return resolved_id

        self._fail(self._field_message(field, f"unsupported ref type: {type(value).__name__}"))
        return ""

    def id_map(self) -> Dict[str, str]:
        return {key: self._symbol_to_id[key] for key in sorted(self._symbol_to_id.keys())}

    def known_ids(self, domain: DomainName) -> set[str]:
        return set(self._domain_ids.get(self._normalize_domain(domain), set()))

    def _field_message(self, field: Optional[str], message: str) -> str:
        if field:
            return f"{field}: {message}"
        return message

    def _normalize_domain(self, domain: Optional[DomainName | str]) -> str:
        if not domain:
            return ""
        norm = snake_case(domain)
        if norm not in KNOWN_DOMAINS:
            self._fail(f"unknown domain={domain!r}; expected one of {', '.join(KNOWN_DOMAINS)}")
        return norm

    def _normalize_symbol_ref(self, symbol: str) -> str:
        raw = symbol.strip()
        if "." not in raw:
            self._fail(f"invalid symbol ref={symbol!r}; expected '<domain>.<symbol>'")
            return raw
        domain, tail = raw.split(".", 1)
        norm_domain = self._normalize_domain(domain)
        tokens = self._tail_tokens(tail)
        if not tokens:
            self._fail(f"invalid symbol ref={symbol!r}; missing symbol tail")
            return f"{norm_domain}."
        return f"{norm_domain}.{'.'.join(tokens)}"

    def _canonical_symbol(
        self,
        domain: str,
        *,
        symbol: Optional[str],
        parts: Sequence[str],
        id_override: Optional[str],
    ) -> str:
        if symbol:
            raw = symbol.strip()
            if "." in raw:
                prefix, tail = raw.split(".", 1)
                norm_prefix = self._normalize_domain(prefix)
                if norm_prefix != domain:
                    self._fail(
                        f"domain mismatch for symbol={symbol!r}: expected domain={domain!r}, got domain={norm_prefix!r}"
                    )
                    norm_prefix = domain
            else:
                norm_prefix = domain
                tail = raw
            tokens = self._tail_tokens(tail)
            if not tokens:
                self._fail(f"invalid symbol={symbol!r}; empty tail")
                tokens = [domain]
            return f"{norm_prefix}.{'.'.join(tokens)}"

        auto_tokens: list[str] = []
        for entry in parts:
            token = snake_case(str(entry))
            if token:
                auto_tokens.append(token)
        if not auto_tokens and id_override:
            token = snake_case(id_override)
            if token:
                auto_tokens.append(token)
        if not auto_tokens:
            self._fail(f"{domain}: cannot register symbol without symbol/parts/id_override")
            auto_tokens = [domain]
        return f"{domain}.{'.'.join(auto_tokens)}"

    def _tail_tokens(self, raw_tail: str) -> list[str]:
        tokens: list[str] = []
        for chunk in raw_tail.replace("/", ".").replace(" ", ".").split("."):
            token = snake_case(chunk)
            if token:
                tokens.append(token)
        return tokens

    def _symbol_tail_tokens(self, symbol: str) -> list[str]:
        _, tail = symbol.split(".", 1)
        return [snake_case(part) for part in tail.split(".") if snake_case(part)]

    def _looks_like_symbol(self, raw: str, domain: Optional[str]) -> bool:
        if "." not in raw:
            return False
        prefix = raw.split(".", 1)[0]
        if domain and snake_case(prefix) == domain:
            return True
        return snake_case(prefix) in KNOWN_DOMAINS

    def _resolve_symbol(self, symbol: str, *, domain: Optional[str], field: Optional[str]) -> str:
        raw = symbol.strip()
        query = raw
        if "." not in raw:
            if not domain:
                self._fail(self._field_message(field, f"invalid ref={symbol!r}; expected '<domain>.<symbol>'"))
                return ""
            query = f"{domain}.{raw}"
        query = self._normalize_symbol_ref(query)

        exact = self._symbol_to_id.get(query)
        if exact:
            return exact

        query_domain, query_tail = query.split(".", 1)
        candidates = [
            candidate
            for candidate in self._symbol_to_id.keys()
            if candidate.startswith(f"{query_domain}.") and candidate.endswith(f".{query_tail}")
        ]
        if len(candidates) == 1:
            return self._symbol_to_id[candidates[0]]

        if len(candidates) > 1:
            self._fail(self._field_message(field, f"ambiguous ref={query!r}; candidates={sorted(candidates)}"))
            return ""

        known = [entry for entry in self._symbol_to_id.keys() if entry.startswith(f"{query_domain}.")]
        suggestion = get_close_matches(query, known, n=3)
        hint = f" nearest={suggestion}" if suggestion else ""
        self._fail(self._field_message(field, f"unresolved ref={query!r}.{hint}"))
        return ""


def as_ref(value: str | Ref, *, domain: Optional[DomainName] = None) -> Ref:
    if isinstance(value, Ref):
        return value
    if domain and "." not in value:
        return Ref(f"{domain}.{value}")
    return Ref(value)


def ensure_iter(values: Optional[Iterable[Any]]) -> list[Any]:
    if values is None:
        return []
    if isinstance(values, (list, tuple, set)):
        return list(values)
    return [values]
