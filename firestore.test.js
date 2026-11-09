const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const EVE_UID = "eve_789";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: cannot read users or rooms or envelopes", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("rooms").get());
  await assertFails(unauthDb.collection("envelopes").get());
});

test("UserProfile: owner can create and update own profile, non-owner cannot spoof or update", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date();

  const validProfile = {
    userId: ALICE_UID,
    username: "alice_zero",
    displayName: "Alice Crypto",
    bio: "E2EE Verified Node",
    publicKey: "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A",
    safetyFingerprint: "94810 28491 77261 00291 88412",
    onlineStatus: "ONLINE",
    createdAt: now,
    updatedAt: now,
  };

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validProfile));

  // Shadow update test (ghost field)
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      ...validProfile,
      isAdmin: true,
    })
  );

  // Cross-user update test
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      bio: "Hacked bio",
      updatedAt: new Date(),
    })
  );

  // Authenticated peer can look up Alice's public key
  await assertSucceeds(bobDb.collection("users").doc(ALICE_UID).get());
});

test("ChatRoom & EncryptedEnvelope: only participants can read/write, outsider Eve is blocked", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore();
  const now = new Date();

  const roomData = {
    ownerId: ALICE_UID,
    participantUids: [ALICE_UID, BOB_UID],
    participantNames: "alice_zero,bob_cipher",
    title: "Alice & Bob Secret",
    isGroup: false,
    roomKeyHint: "seed_alice_bob_256",
    lastCipherPreview: "U2FsdGVkX1+encrypted==",
    lastSenderName: "alice_zero",
    defaultTtlSeconds: 60,
    createdAt: now,
    updatedAt: now,
  };

  await assertSucceeds(aliceDb.collection("rooms").doc("room_1").set(roomData));

  // Bob (participant) can read and query room
  await assertSucceeds(bobDb.collection("rooms").doc("room_1").get());
  await assertSucceeds(
    bobDb.collection("rooms").where("participantUids", "array-contains", BOB_UID).get()
  );

  // Eve (non-participant) cannot read or query Alice & Bob's room
  await assertFails(eveDb.collection("rooms").doc("room_1").get());
  await assertFails(eveDb.collection("rooms").get());

  // Alice sends an E2EE envelope
  const envelopeData = {
    roomId: "room_1",
    senderId: ALICE_UID,
    senderUsername: "alice_zero",
    participantUids: [ALICE_UID, BOB_UID],
    ciphertext: "k9J2mP0xL7vQ4wR1zT8yB5nM3cV6==",
    iv: "a1b2c3d4e5f6g7h8",
    signatureHash: "sha256_998877665544332211",
    messageType: "TEXT",
    encryptedMeta: "{}",
    reactionsSummary: "",
    ttlSeconds: 60,
    createdAt: now,
    updatedAt: now,
  };

  await assertSucceeds(aliceDb.collection("envelopes").doc("env_1").set(envelopeData));

  // Bob can query envelopes for room_1 with participantUids filter
  await assertSucceeds(
    bobDb
      .collection("envelopes")
      .where("roomId", "==", "room_1")
      .where("participantUids", "array-contains", BOB_UID)
      .get()
  );

  // Eve cannot read envelope
  await assertFails(eveDb.collection("envelopes").doc("env_1").get());
});
