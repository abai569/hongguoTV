export function mergeSearchItems(primary, candidates, limit = 20) {
  const result = [];
  const seen = new Set();
  for (const item of [...primary, ...candidates]) {
    const id = String(item?.id || item?.url || "");
    if (!id || seen.has(id)) continue;
    seen.add(id);
    result.push(item);
    if (result.length >= limit) break;
  }
  return result;
}

export function isInitialsQuery(value) {
  return /^[a-z0-9]{2,20}$/i.test(value.trim());
}

export async function searchWithInitialCandidates(keyword, page, search, suggest) {
  const primary = await search(keyword, page);
  if (page !== 1 || primary.items.length || !isInitialsQuery(keyword)) return primary;
  let candidates = [];
  try {
    candidates = await suggest(keyword);
  } catch {
    return primary;
  }
  const extra = [];
  for (const candidate of candidates.slice(0, 5)) {
    try {
      extra.push(...(await search(candidate, 1)).items);
    } catch {
      /* One unavailable candidate must not hide other suggestions. */
    }
  }
  return { ...primary, items: mergeSearchItems(primary.items, extra) };
}
