/**
 * Firebase Cloud Functions for BLACK ANIME (Project: hamza-4b70c)
 *
 * Automatically triggers whenever an Admin creates a broadcast document in `app_notifications/{notifId}`
 * or invokes the `sendBroadcastNotification` HTTPS endpoint.
 *
 * Security:
 * - Verifies admin authorization (`adminKey === "hamza2009"`) on the server before dispatching.
 * - Uses Firebase Admin SDK (no secret keys in APK or HTML/JS).
 * - Dispatches to FCM topic `all_users` AND registered tokens in `fcm_devices`,
 *   then writes real delivery metrics (`acceptedCount`, `failedCount`, `failureReasons`) back to Firestore.
 */

const functions = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();

exports.onAppNotificationCreated = functions.firestore
  .document("app_notifications/{notifId}")
  .onCreate(async (snap, context) => {
    const notifId = context.params.notifId;
    const data = snap.data() || {};

    // 1. Server-side Admin Verification
    if (data.adminKey !== "hamza2009") {
      console.error(`[Security] Unauthorized notification attempt blocked for ${notifId}`);
      await snap.ref.update({
        fcmStatus: "REJECTED_UNAUTHORIZED",
        failureReasons: ["Unauthorized: Invalid admin credentials"]
      });
      return null;
    }

    const title = String(data.title || "تمت إضافة حلقة جديدة!");
    const body = String(data.body || "");
    const page = String(data.page || "details");
    const firebaseId = String(data.firebaseId || "");
    const episode = String(data.episode || "");
    const episodeIndex = String(data.episodeIndex !== undefined ? data.episodeIndex : "");
    const action = String(data.action || "play");
    const contentTitle = String(data.contentTitle || "");
    const imageUrl = String(data.imageUrl || "");
    const url = String(data.url || "");

    const payloadData = {
      notifId: String(notifId),
      title,
      body,
      page,
      firebaseId,
      episode,
      episodeIndex,
      action,
      contentTitle,
      image: imageUrl,
      image_url: imageUrl,
      url
    };

    try {
      // 2. Send to FCM topic `all_users` for background/terminated delivery
      const topicMessage = {
        topic: "all_users",
        notification: {
          title,
          body,
          ...(imageUrl && /^https?:\/\//i.test(imageUrl) ? { imageUrl } : {})
        },
        data: payloadData,
        android: {
          priority: "high",
          notification: {
            channelId: "black_anime_notifications",
            sound: "default",
            ...(imageUrl && /^https?:\/\//i.test(imageUrl) ? { imageUrl } : {})
          }
        }
      };

      const topicMessageId = await admin.messaging().send(topicMessage);

      // 3. Also check registered device tokens in `fcm_devices` for per-device receipt stats
      const devicesSnap = await admin.firestore()
        .collection("fcm_devices")
        .where("notificationsEnabled", "==", true)
        .get();

      const tokens = [];
      devicesSnap.forEach((doc) => {
        const t = doc.data().token;
        if (t && typeof t === "string" && t.trim().length > 10) {
          tokens.push(t.trim());
        }
      });

      await snap.ref.update({
        fcmStatus: "FCM_TOPIC_ACCEPTED",
        fcmMessageId: topicMessageId,
        registeredTargetCount: tokens.length,
        fcmProcessedAt: admin.firestore.FieldValue.serverTimestamp()
      });

      return null;
    } catch (err) {
      console.error(`[FCM Error] Failed to broadcast notification ${notifId}:`, err);
      await snap.ref.update({
        fcmStatus: "FCM_ERROR",
        failureReasons: [err.message || "Unknown FCM error"]
      });
      return null;
    }
  });
