export const catalogs = {
  routes: {
    title: "Tuyến đường",
    fields: [
      { name: "origin", label: "Điểm đi", maxLength: 160 },
      { name: "destination", label: "Điểm đến", maxLength: 160 },
      {
        name: "active",
        label: "Đang hoạt động",
        type: "checkbox",
        value: true,
      },
    ],
  },
  vehicles: {
    title: "Phương tiện",
    fields: [
      { name: "vehicleCode", label: "Mã phương tiện", maxLength: 40 },
      { name: "displayName", label: "Tên phương tiện", maxLength: 120 },
      {
        name: "vehicleType",
        label: "Loại phương tiện",
        options: [
          ["BUS", "Xe khách"],
          ["TRAIN", "Tàu hỏa"],
        ],
        value: "BUS",
      },
      {
        name: "active",
        label: "Đang hoạt động",
        type: "checkbox",
        value: true,
      },
    ],
  },
  seats: {
    title: "Ghế",
    fields: [
      { name: "seatCode", label: "Mã ghế", maxLength: 20 },
      {
        name: "seatClass",
        label: "Hạng ghế",
        maxLength: 40,
        value: "STANDARD",
      },
    ],
  },
  trips: {
    title: "Chuyến đi",
    fields: [
      { name: "routeId", label: "Tuyến đường", reference: "routes" },
      { name: "vehicleId", label: "Phương tiện", reference: "vehicles" },
      {
        name: "departureAt",
        label: "Giờ khởi hành (Việt Nam)",
        type: "datetime-local",
      },
      {
        name: "arrivalAt",
        label: "Giờ đến (Việt Nam)",
        type: "datetime-local",
      },
      { name: "basePrice", label: "Giá vé (VND)", type: "number" },
      {
        name: "status",
        label: "Trạng thái",
        options: [
          ["SCHEDULED", "Mở bán"],
          ["CANCELLED", "Đã hủy"],
          ["COMPLETED", "Hoàn tất"],
        ],
        value: "SCHEDULED",
      },
    ],
  },
};

export function vietnamInput(value) {
  return new Date(value)
    .toLocaleString("sv-SE", { timeZone: "Asia/Ho_Chi_Minh" })
    .replace(" ", "T")
    .slice(0, 16);
}

export function catalogBody(kind, form) {
  const body = {};
  for (const field of catalogs[kind].fields) {
    const value = form[field.name];
    if (field.type === "checkbox") body[field.name] = Boolean(value);
    else if (field.reference || field.type === "number")
      body[field.name] = Number(value);
    else if (field.type === "datetime-local")
      body[field.name] = new Date(value + ":00+07:00").toISOString();
    else body[field.name] = value.trim();
  }
  return body;
}

// Load every page for selectors, so a route beyond the first 100 rows can still be chosen.
export async function catalogOptions(api, path, options) {
  const items = [];
  let page = 0;
  let totalPages = 1;
  while (page < totalPages) {
    const data = await api(path + "?size=100&page=" + page, options);
    items.push(...data.content);
    totalPages = data.totalPages;
    page += 1;
  }
  return items;
}
