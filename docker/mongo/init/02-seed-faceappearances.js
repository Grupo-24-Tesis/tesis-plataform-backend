db = db.getSiblingDB(process.env.MONGO_APP_DB);

db.face_appearances.insertMany([
  // Persona A
  { faceId: "person_001", timestamp: "00:00:05", cameraId: "cam_1", frameUrl: "http://localhost:8080/persons/cristiano/01.jpg", capturedAt: ISODate("2026-08-27T14:35:22Z") },
  { faceId: "person_001", timestamp: "00:01:18", cameraId: "cam_2", frameUrl: "http://localhost:8080/persons/cristiano/02.jpg", capturedAt: ISODate("2026-08-27T14:35:22Z") },
  { faceId: "person_001", timestamp: "00:03:42", cameraId: "cam_4", frameUrl: "http://localhost:8080/persons/cristiano/03.jpg", capturedAt: ISODate("2026-08-27T14:35:22Z") },
  { faceId: "person_001", timestamp: "00:06:20", cameraId: "cam_1", frameUrl: "http://localhost:8080/persons/cristiano/04.jpg", capturedAt: ISODate("2026-08-27T14:35:22Z") },

  // Persona B
  { faceId: "person_002", timestamp: "00:00:13", cameraId: "cam_1", frameUrl: "http://localhost:8080/persons/messi/01.jpg", capturedAt: ISODate("2026-08-27T14:35:22Z") },
  { faceId: "person_002", timestamp: "00:02:01", cameraId: "cam_1", frameUrl: "http://localhost:8080/persons/messi/02.jpg", capturedAt: ISODate("2026-08-27T14:35:22Z") },
  { faceId: "person_002", timestamp: "00:04:25", cameraId: "cam_5", frameUrl: "http://localhost:8080/persons/messi/03.jpg", capturedAt: ISODate("2026-08-27T14:35:22Z") },
  { faceId: "person_002", timestamp: "00:07:31", cameraId: "cam_5", frameUrl: "http://localhost:8080/persons/messi/04.jpg", capturedAt: ISODate("2026-08-27T14:35:22Z") },

  // Persona C
  { faceId: "person_003", timestamp: "00:01:02", cameraId: "cam_2", frameUrl: "http://localhost:8080/persons/jordan/01.jpg", capturedAt: ISODate("2026-08-27T14:35:22Z") },
  { faceId: "person_003", timestamp: "00:02:59", cameraId: "cam_3", frameUrl: "http://localhost:8080/persons/jordan/02.jpg", capturedAt: ISODate("2026-08-27T14:35:22Z") },
  { faceId: "person_003", timestamp: "00:05:36", cameraId: "cam_3", frameUrl: "http://localhost:8080/persons/jordan/03.jpg", capturedAt: ISODate("2026-08-27T14:35:22Z") },
  
  // Persona D
  { faceId: "person_004", timestamp: "00:02:27", cameraId: "cam_4", frameUrl: "http://localhost:8080/persons/phelps/01.jpg", capturedAt: ISODate("2026-08-27T14:35:22Z") },
  { faceId: "person_004", timestamp: "00:05:11", cameraId: "cam_2", frameUrl: "http://localhost:8080/persons/phelps/02.jpg", capturedAt: ISODate("2026-08-27T14:35:22Z") },
  { faceId: "person_004", timestamp: "00:06:02", cameraId: "cam_4", frameUrl: "http://localhost:8080/persons/phelps/03.jpg", capturedAt: ISODate("2026-08-27T14:35:22Z") },

  // Persona E
  { faceId: "person_005", timestamp: "00:01:45", cameraId: "cam_3", frameUrl: "http://localhost:8080/persons/ibarguen/01.jpg", capturedAt: ISODate("2026-08-27T14:35:22Z") },
  { faceId: "person_005", timestamp: "00:04:58", cameraId: "cam_5", frameUrl: "http://localhost:8080/persons/ibarguen/02.jpg", capturedAt: ISODate("2026-08-27T14:35:22Z") },
  { faceId: "person_005", timestamp: "00:07:07", cameraId: "cam_2", frameUrl: "http://localhost:8080/persons/ibarguen/03.jpg", capturedAt: ISODate("2026-08-27T14:35:22Z") }
]);
