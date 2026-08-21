db = db.getSiblingDB(process.env.MONGO_APP_DB);

db.guards.insertMany([
  { name: "Carlos Ramírez", shift: "mañana", schedule: "06:00-14:00" },
  { name: "Andrés Torres", shift: "tarde", schedule: "14:00-22:00" },
  { name: "Patricia Méndez", shift: "noche", schedule: "22:00-06:00" }
]);

db.cameras.insertMany([
  { id: "CAM-01", name: "Entrada Principal", active: true },
  { id: "CAM-02", name: "Parqueadero A", active: true },
  { id: "CAM-03", name: "Zona Común Piscina", active: true }
]);

db.reports.insertMany([
  { title: "Ingreso sospechoso", status: "investigacion", priority: "alta", description: "Persona no identificada en cuarto de bombas." },
  { title: "Vehículo abandonado", status: "investigacion", priority: "media", description: "Más de 72 horas sin autorización." }
]);

db.marked.insertMany([
  { faceId: "person_001", reason: "Merodeo repetitivo en zona restringida" }
]);