db = db.getSiblingDB(process.env.MONGO_APP_DB);

db.face_appearances.insertMany([
  // Persona A
  { faceId: "person_001", timestamp: "00:00:05", cameraId: "cam_1", frameUrl: "https://dummy.local/frame/a1.jpg" },
  { faceId: "person_001", timestamp: "00:01:18", cameraId: "cam_2", frameUrl: "https://dummy.local/frame/a2.jpg" },
  { faceId: "person_001", timestamp: "00:03:42", cameraId: "cam_4", frameUrl: "https://dummy.local/frame/a3.jpg" },
  { faceId: "person_001", timestamp: "00:06:20", cameraId: "cam_1", frameUrl: "https://dummy.local/frame/a4.jpg" },

  // Persona B
  { faceId: "person_002", timestamp: "00:00:13", cameraId: "cam_1", frameUrl: "https://dummy.local/frame/b1.jpg" },
  { faceId: "person_002", timestamp: "00:02:01", cameraId: "cam_1", frameUrl: "https://dummy.local/frame/b2.jpg" },
  { faceId: "person_002", timestamp: "00:04:25", cameraId: "cam_5", frameUrl: "https://dummy.local/frame/b3.jpg" },
  { faceId: "person_002", timestamp: "00:07:31", cameraId: "cam_5", frameUrl: "https://dummy.local/frame/b4.jpg" },

  // Persona C
  { faceId: "person_003", timestamp: "00:01:02", cameraId: "cam_2", frameUrl: "https://dummy.local/frame/c1.jpg" },
  { faceId: "person_003", timestamp: "00:02:59", cameraId: "cam_3", frameUrl: "https://dummy.local/frame/c2.jpg" },
  { faceId: "person_003", timestamp: "00:05:36", cameraId: "cam_3", frameUrl: "https://dummy.local/frame/c3.jpg" },

  // Persona D
  { faceId: "person_004", timestamp: "00:02:27", cameraId: "cam_4", frameUrl: "https://dummy.local/frame/d1.jpg" },
  { faceId: "person_004", timestamp: "00:05:11", cameraId: "cam_2", frameUrl: "https://dummy.local/frame/d2.jpg" },
  { faceId: "person_004", timestamp: "00:06:02", cameraId: "cam_4", frameUrl: "https://dummy.local/frame/d3.jpg" },

  // Persona E
  { faceId: "person_005", timestamp: "00:01:45", cameraId: "cam_3", frameUrl: "https://dummy.local/frame/e1.jpg" },
  { faceId: "person_005", timestamp: "00:04:58", cameraId: "cam_5", frameUrl: "https://dummy.local/frame/e2.jpg" },
  { faceId: "person_005", timestamp: "00:07:07", cameraId: "cam_2", frameUrl: "https://dummy.local/frame/e3.jpg" }
]);