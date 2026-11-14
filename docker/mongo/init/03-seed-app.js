db = db.getSiblingDB(process.env.MONGO_APP_DB);

db.guards.insertMany([
  { name: "Carlos Ramírez", shift: "mañana", schedule: "06:00-14:00" },
  { name: "Andrés Torres", shift: "tarde", schedule: "14:00-22:00" },
  { name: "Patricia Méndez", shift: "noche", schedule: "22:00-06:00" }
]);

db.cameras.insertMany([
  { _id: "CAM-01", name: "Entrada Principal", active: true },
  { _id: "CAM-02", name: "Parqueadero A", active: true },
  { _id: "CAM-03", name: "Zona Común Piscina", active: true }
]);

db.reports.insertMany([
  {"title":"personas no autorizadas","status":"solucionado","priority":"alta","description":"ingreso personas sospechosas","incidentDate": ISODate("2026-08-21T00:12:00"),"createdAt": ISODate("2026-08-28T05:12:22.206"),"resolvedAt": ISODate("2026-08-29T00:12:00"),"resolutionDescription":"se llama a la policia","faceIds":[]},
  {"title":"robo","status":"investigacion","priority":"alta","description":"presuntos sospechosos en tobo","incidentDate": ISODate("2026-08-20T00:28:00"),"createdAt": ISODate("2026-08-28T05:28:59.082"),"resolvedAt":null,"resolutionDescription":"","faceIds":[]}
]);
