export function locationText(value) {
  return value
    .trim()
    .toLocaleLowerCase("vi")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/đ/g, "d");
}

export function locationOptions(routes, field, opposite, query) {
  const other = field === "origin" ? "destination" : "origin";
  const hasOpposite = routes.some(
    (route) => locationText(route[other]) === locationText(opposite),
  );
  const options = new Set();
  for (const route of routes) {
    if (hasOpposite && locationText(route[other]) !== locationText(opposite))
      continue;
    const name = route[field];
    if (locationText(name).includes(locationText(query))) options.add(name);
  }
  return [...options].sort((a, b) => a.localeCompare(b, "vi"));
}

export function canonicalLocation(routes, field, value) {
  for (const route of routes) {
    if (locationText(route[field]) === locationText(value)) return route[field];
  }
  return value.trim();
}
