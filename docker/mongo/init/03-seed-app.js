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
  {"id":"6a9118b682f5bc49446654c7","title":"personas no autorizadas","status":"solucionado","priority":"alta","description":"ingreso personas sospechosas","incidentDate": ISODate("2026-08-21T00:12:00"),"createdAt": ISODate("2026-08-28T05:12:22.206"),"resolvedAt": ISODate("2026-08-29T00:12:00"),"resolutionDescription":"se llama a la policia","faceIds":["person_001","person_002"]},
  {"id":"6a911c9b82f5bc49446654c8","title":"robo","status":"investigacion","priority":"alta","description":"presuntos sospechosos en tobo","incidentDate": ISODate("2026-08-20T00:28:00"),"createdAt": ISODate("2026-08-28T05:28:59.082"),"resolvedAt":null,"resolutionDescription":"","faceIds":["person_004","person_003"]}
]);

db.marked.insertMany([
  { faceId: "person_001", reason: "Merodeo repetitivo en zona restringida" }
]);