// Security rules tests for ZeroChat V2. Run with: npm run test:rules
const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach, describe } = require("node:test");
const fs = require("node:fs");
const firebase = require("firebase/compat/app");
require("firebase/compat/firestore");

const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE = "alice_123";
const BOB = "bob_456";
const EVE = "eve_789";
const CAROL = "carol_321";
const DM = `dm_${ALICE}_${BOB}`;

const [host, portStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const ts = () => firebase.firestore.FieldValue.serverTimestamp();
const IV = "AAAAAAAAAAAAAAAA";
const SIG = "c2lnbmF0dXJlc2lnbmF0dXJl";

let env;
const db = (uid) => env.authenticatedContext(uid).firestore();

before(async () => {
  env = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: { rules: fs.readFileSync("./firestore.rules", "utf8"), host, port: parseInt(portStr, 10) },
  });
});
after(async () => env && env.cleanup());
beforeEach(async () => env.clearFirestore());

async function seed(fn) {
  await env.withSecurityRulesDisabled(async (ctx) => fn(ctx.firestore()));
}

function roomDoc(overrides = {}) {
  return {
    ownerId: ALICE,
    participantUids: [ALICE, BOB],
    participantNames: "alice,bob",
    title: "@bob",
    isGroup: false,
    status: "ACTIVE",
    requesterUid: ALICE,
    keyVersion: 1,
    defaultTtlSeconds: 0,
    lastPreviewKv: 1,
    lastPreviewIv: IV,
    lastPreviewCt: "Y3Q=",
    lastSenderUid: ALICE,
    lastActivityAt: new Date(),
    createdAt: new Date(),
    updatedAt: new Date(),
    ...overrides,
  };
}

function keysDoc(uids, by = ALICE) {
  const wrappedKeys = {};
  const recipientKeyIds = {};
  uids.forEach((u) => {
    wrappedKeys[u] = "eph.iv.ct";
    recipientKeyIds[u] = "kid";
  });
  return { createdBy: by, wrappedKeys, recipientKeyIds, createdAt: ts() };
}

function envelope(sender, roomId, participants, overrides = {}) {
  return {
    roomId,
    senderId: sender,
    participantUids: participants,
    v: 2,
    keyVersion: 1,
    iv: IV,
    ciphertext: "Y2lwaGVydGV4dA==",
    sig: SIG,
    signerKeyId: "kid",
    ttlSeconds: 0,
    edited: false,
    createdAt: ts(),
    updatedAt: ts(),
    ...overrides,
  };
}

async function seedActiveDm(extra = {}) {
  await seed(async (s) => {
    await s.doc(`rooms/${DM}`).set(roomDoc(extra));
    await s.doc(`rooms/${DM}/keys/1`).set({ ...keysDoc([ALICE, BOB]), createdAt: new Date() });
  });
}

// ------------------------------------------------------------------ users & usernames

describe("users and usernames", () => {
  const profile = (uid, username) => ({
    userId: uid,
    username,
    displayName: "Alice",
    bio: "",
    publicKey: "pk",
    signingKey: "sk",
    safetyFingerprint: "12345",
    onlineStatus: "ONLINE",
    protocolVersion: 2,
    createdAt: ts(),
    updatedAt: ts(),
  });

  test("unauthenticated users cannot read anything", async () => {
    const anon = env.unauthenticatedContext().firestore();
    await assertFails(anon.doc(`users/${ALICE}`).get());
    await assertFails(anon.collection("rooms").get());
    await assertFails(anon.collection("envelopes").get());
  });

  test("profile requires a username claimed by the same user (in the same batch)", async () => {
    const a = db(ALICE);
    await assertFails(a.doc(`users/${ALICE}`).set(profile(ALICE, "alice")));
    const batch = a.batch();
    batch.set(a.doc("usernames/alice"), { uid: ALICE, createdAt: ts() });
    batch.set(a.doc(`users/${ALICE}`), profile(ALICE, "alice"));
    await assertSucceeds(batch.commit());
  });

  test("cannot claim a handle for someone else or take an existing handle", async () => {
    await assertFails(db(EVE).doc("usernames/bob").set({ uid: BOB, createdAt: ts() }));
    await seed((s) => s.doc("usernames/alice").set({ uid: ALICE }));
    await assertFails(db(EVE).doc("usernames/alice").set({ uid: EVE, createdAt: ts() }));
    await assertFails(db(EVE).doc("usernames/alice").delete());
  });

  test("cannot write another user's profile or add unknown fields", async () => {
    await seed(async (s) => {
      await s.doc("usernames/alice").set({ uid: ALICE });
      await s.doc(`users/${ALICE}`).set({ ...profile(ALICE, "alice"), createdAt: new Date(), updatedAt: new Date() });
    });
    await assertFails(db(BOB).doc(`users/${ALICE}`).update({ bio: "hacked", updatedAt: ts() }));
    await assertFails(db(ALICE).doc(`users/${ALICE}`).update({ isAdmin: true, updatedAt: ts() }));
    await assertSucceeds(db(ALICE).doc(`users/${ALICE}`).update({ bio: "hi", updatedAt: ts() }));
  });

  test("no directory dumps: list needs a small limit", async () => {
    await assertFails(db(EVE).collection("users").get());
    await assertSucceeds(db(EVE).collection("users").limit(30).get());
    await assertFails(db(EVE).collection("usernames").get());
    await assertSucceeds(db(EVE).collection("usernames").limit(20).get());
  });
});

// ------------------------------------------------------------------ rooms

describe("rooms", () => {
  test("direct message starts as a request with its key version in the same batch", async () => {
    const a = db(ALICE);
    const batch = a.batch();
    batch.set(a.doc(`rooms/${DM}`), { ...roomDoc({ status: "PENDING" }), lastActivityAt: ts(), createdAt: ts(), updatedAt: ts() });
    batch.set(a.doc(`rooms/${DM}/keys/1`), keysDoc([ALICE, BOB]));
    await assertSucceeds(batch.commit());
  });

  test("direct message cannot be created pre-approved, under a wrong id, or without keys", async () => {
    const a = db(ALICE);
    const pending = { ...roomDoc({ status: "PENDING" }), lastActivityAt: ts(), createdAt: ts(), updatedAt: ts() };
    let batch = a.batch();
    batch.set(a.doc(`rooms/${DM}`), { ...pending, status: "ACTIVE" });
    batch.set(a.doc(`rooms/${DM}/keys/1`), keysDoc([ALICE, BOB]));
    await assertFails(batch.commit());

    batch = a.batch();
    batch.set(a.doc("rooms/dm_custom"), pending);
    batch.set(a.doc("rooms/dm_custom/keys/1"), keysDoc([ALICE, BOB]));
    await assertFails(batch.commit());

    await assertFails(a.doc(`rooms/${DM}`).set(pending));
  });

  test("only the recipient can accept a request", async () => {
    await seedActiveDm({ status: "PENDING", requesterUid: ALICE });
    await assertFails(db(ALICE).doc(`rooms/${DM}`).update({ status: "ACTIVE", updatedAt: ts() }));
    await assertSucceeds(db(BOB).doc(`rooms/${DM}`).update({ status: "ACTIVE", updatedAt: ts() }));
  });

  test("outsiders cannot read rooms; participants cannot add people to a DM", async () => {
    await seedActiveDm();
    await assertFails(db(EVE).doc(`rooms/${DM}`).get());
    await assertFails(db(EVE).collection("rooms").where("participantUids", "array-contains", ALICE).get());
    await assertSucceeds(db(BOB).doc(`rooms/${DM}`).get());
    await assertFails(
      db(BOB).doc(`rooms/${DM}`).update({ participantUids: [ALICE, BOB, EVE], updatedAt: ts() })
    );
    await assertFails(db(BOB).doc(`rooms/${DM}`).update({ ownerId: BOB, updatedAt: ts() }));
  });

  test("group membership: owner only, and only together with a new key version", async () => {
    await seed(async (s) => {
      await s.doc("rooms/g1").set(roomDoc({ isGroup: true, title: "G", participantUids: [ALICE, BOB, CAROL] }));
      await s.doc("rooms/g1/keys/1").set({ ...keysDoc([ALICE, BOB, CAROL]), createdAt: new Date() });
    });
    await assertFails(db(BOB).doc("rooms/g1").update({ participantUids: [ALICE, BOB, CAROL, EVE], updatedAt: ts() }));
    await assertFails(db(ALICE).doc("rooms/g1").update({ participantUids: [ALICE, BOB], updatedAt: ts() }));

    const a = db(ALICE);
    const batch = a.batch();
    batch.update(a.doc("rooms/g1"), { participantUids: [ALICE, BOB], participantNames: "alice,bob", keyVersion: 2, updatedAt: ts() });
    batch.set(a.doc("rooms/g1/keys/2"), keysDoc([ALICE, BOB]));
    await assertSucceeds(batch.commit());
  });

  test("a member can leave a group but cannot remove others", async () => {
    await seed((s) => s.doc("rooms/g1").set(roomDoc({ isGroup: true, title: "G", participantUids: [ALICE, BOB, CAROL] })));
    await assertFails(db(BOB).doc("rooms/g1").update({ participantUids: [BOB], participantNames: "", updatedAt: ts() }));
    await assertSucceeds(
      db(BOB).doc("rooms/g1").update({ participantUids: [ALICE, CAROL], participantNames: "alice,carol", updatedAt: ts() })
    );
  });

  test("key versions must wrap for exactly the participants", async () => {
    await seedActiveDm();
    const b = db(BOB);
    let batch = b.batch();
    batch.update(b.doc(`rooms/${DM}`), { keyVersion: 2, updatedAt: ts() });
    batch.set(b.doc(`rooms/${DM}/keys/2`), keysDoc([ALICE, BOB, EVE], BOB));
    await assertFails(batch.commit());

    batch = b.batch();
    batch.update(b.doc(`rooms/${DM}`), { keyVersion: 2, updatedAt: ts() });
    batch.set(b.doc(`rooms/${DM}/keys/2`), keysDoc([ALICE, BOB], BOB));
    await assertSucceeds(batch.commit());
    await assertFails(db(EVE).doc(`rooms/${DM}/keys/2`).get());
  });
});

// ------------------------------------------------------------------ envelopes

describe("envelopes", () => {
  test("participant can send; outsider cannot inject into someone else's DM", async () => {
    await seedActiveDm();
    await assertSucceeds(db(ALICE).doc("envelopes/env_1").set(envelope(ALICE, DM, [ALICE, BOB])));
    await assertFails(db(EVE).doc("envelopes/env_2").set(envelope(EVE, DM, [ALICE, BOB])));
    await assertFails(db(EVE).doc("envelopes/env_3").set(envelope(EVE, DM, [EVE, ALICE, BOB])));
    await assertFails(db(ALICE).doc("envelopes/env_4").set(envelope(BOB, DM, [ALICE, BOB])));
  });

  test("participant list must match the room and legacy v1 writes are rejected", async () => {
    await seedActiveDm();
    await assertFails(db(ALICE).doc("envelopes/env_1").set(envelope(ALICE, DM, [ALICE, BOB, EVE])));
    await assertFails(db(ALICE).doc("envelopes/env_2").set(envelope(ALICE, DM, [ALICE, BOB], { v: 1 })));
  });

  test("pending request: only the requester may write", async () => {
    await seedActiveDm({ status: "PENDING", requesterUid: ALICE });
    await assertSucceeds(db(ALICE).doc("envelopes/env_1").set(envelope(ALICE, DM, [ALICE, BOB])));
    await assertFails(db(BOB).doc("envelopes/env_2").set(envelope(BOB, DM, [ALICE, BOB])));
  });

  test("only the sender can edit or delete a message", async () => {
    await seedActiveDm();
    await seed((s) => s.doc("envelopes/env_1").set({ ...envelope(ALICE, DM, [ALICE, BOB]), createdAt: new Date(), updatedAt: new Date() }));
    const edit = { ciphertext: "bmV3", iv: IV, sig: SIG, edited: true, updatedAt: ts() };
    await assertFails(db(BOB).doc("envelopes/env_1").update(edit));
    await assertFails(db(BOB).doc("envelopes/env_1").delete());
    await assertSucceeds(db(ALICE).doc("envelopes/env_1").update(edit));
    await assertSucceeds(db(ALICE).doc("envelopes/env_1").delete());
  });

  test("expired messages can be deleted by any participant", async () => {
    await seedActiveDm();
    await seed((s) =>
      s.doc("envelopes/env_1").set({
        ...envelope(ALICE, DM, [ALICE, BOB], { ttlSeconds: 10 }),
        expireAt: new Date(Date.now() - 60_000),
        createdAt: new Date(),
        updatedAt: new Date(),
      })
    );
    await assertFails(db(EVE).doc("envelopes/env_1").delete());
    await assertSucceeds(db(BOB).doc("envelopes/env_1").delete());
  });

  test("participants can query their room's envelopes, outsiders cannot", async () => {
    await seedActiveDm();
    await seed((s) => s.doc("envelopes/env_1").set({ ...envelope(ALICE, DM, [ALICE, BOB]), createdAt: new Date(), updatedAt: new Date() }));
    await assertSucceeds(
      db(BOB).collection("envelopes").where("roomId", "==", DM).where("participantUids", "array-contains", BOB).get()
    );
    await assertFails(db(EVE).doc("envelopes/env_1").get());
  });
});

// ------------------------------------------------------------------ annotations & presence

describe("annotations and presence", () => {
  test("users write only their own annotations", async () => {
    await seedActiveDm();
    const ann = (uid) => ({ envelopeId: "env_1", uid, kind: "REACTION", keyVersion: 1, iv: IV, ciphertext: "eA==", updatedAt: ts() });
    await assertSucceeds(db(BOB).doc(`rooms/${DM}/annotations/env_1_${BOB}_REACTION`).set(ann(BOB)));
    await assertFails(db(BOB).doc(`rooms/${DM}/annotations/env_1_${ALICE}_REACTION`).set(ann(ALICE)));
    await assertFails(db(EVE).doc(`rooms/${DM}/annotations/env_1_${EVE}_REACTION`).set(ann(EVE)));
  });

  test("presence is per user", async () => {
    await seedActiveDm();
    await assertSucceeds(db(BOB).doc(`rooms/${DM}/presence/${BOB}`).set({ lastReadAt: ts() }));
    await assertFails(db(BOB).doc(`rooms/${DM}/presence/${ALICE}`).set({ lastReadAt: ts() }));
  });
});

// ------------------------------------------------------------------ statuses

describe("statuses", () => {
  const status = (overrides = {}) => ({
    userId: ALICE,
    audienceUids: [ALICE, BOB],
    wrappedKeys: { [ALICE]: "w", [BOB]: "w" },
    iv: IV,
    ciphertext: "Y3Q=",
    sig: SIG,
    themeColorHex: "#082E1E",
    createdAt: ts(),
    expireAt: new Date(Date.now() + 3600_000),
    ...overrides,
  });

  test("only the audience can read; nobody can edit; views go to a subcollection", async () => {
    await assertSucceeds(db(ALICE).doc("statuses/st_1").set(status()));
    await assertSucceeds(db(BOB).doc("statuses/st_1").get());
    await assertFails(db(EVE).doc("statuses/st_1").get());
    await assertFails(db(BOB).doc("statuses/st_1").update({ themeColorHex: "#000000" }));
    await assertSucceeds(db(BOB).doc(`statuses/st_1/views/${BOB}`).set({ viewedAt: ts() }));
    await assertFails(db(EVE).doc(`statuses/st_1/views/${EVE}`).set({ viewedAt: ts() }));
    await assertFails(db(BOB).collection("statuses/st_1/views").get());
    await assertSucceeds(db(ALICE).collection("statuses/st_1/views").get());
  });

  test("status keys must match the audience", async () => {
    await assertFails(db(ALICE).doc("statuses/st_1").set(status({ wrappedKeys: { [ALICE]: "w" } })));
    await assertFails(db(BOB).doc("statuses/st_1").set(status()));
  });
});

// ------------------------------------------------------------------ calls

describe("calls", () => {
  const call = (overrides = {}) => ({
    callerId: ALICE,
    calleeUid: BOB,
    roomId: DM,
    keyVersion: 1,
    participantUids: [ALICE, BOB],
    callType: "AUDIO",
    status: "RINGING",
    createdAt: ts(),
    updatedAt: ts(),
    ...overrides,
  });

  test("calls need an active DM; only the callee can answer; ended calls stay ended", async () => {
    await seedActiveDm();
    await assertFails(db(EVE).doc("calls/c1").set(call({ callerId: EVE, participantUids: [EVE, BOB] })));
    await assertSucceeds(db(ALICE).doc("calls/c1").set(call()));
    await assertFails(db(ALICE).doc("calls/c1").update({ status: "CONNECTED", updatedAt: ts() }));
    await assertSucceeds(db(BOB).doc("calls/c1").update({ status: "CONNECTED", updatedAt: ts() }));

    await assertSucceeds(db(BOB).doc(`calls/c1/media/${BOB}`).set({ seq: 1, iv: IV, ciphertext: "Y3Q=", updatedAt: ts() }));
    await assertFails(db(BOB).doc(`calls/c1/media/${ALICE}`).set({ seq: 1, iv: IV, ciphertext: "Y3Q=", updatedAt: ts() }));
    await assertFails(db(EVE).doc(`calls/c1/media/${BOB}`).get());

    await assertSucceeds(db(ALICE).doc("calls/c1").update({ status: "ENDED", updatedAt: ts() }));
    await assertFails(db(BOB).doc("calls/c1").update({ status: "CONNECTED", updatedAt: ts() }));
  });
});
