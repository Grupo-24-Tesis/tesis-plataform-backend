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
