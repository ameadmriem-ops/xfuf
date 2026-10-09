package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * =========================================================================
 * 🔔 إعدادات وتخصيص نظام الإشعارات (BLACK ANIME NOTIFICATION CONFIG)
 * =========================================================================
 * يمكنك من هذا الملف التحكم الكامل في الإشعارات وتحديد:
 * 1. عنوان الإشعار (title)
 * 2. نص الإشعار / الرسالة (message)
 * 3. الصفحة المراد فتحها (targetPage): "home", "details", "anime", "movies", إلخ
 * 4. معرف العمل / الأنمي في Firebase (contentId) أو اسم العمل (contentTitle)
 * 5. رقم الحلقة المراد فتحها أو تشغيلها مباشرة (episodeNumber)
 * 6. رابط صورة البوستر الاختياري (imageUrl)
 * 7. نوع الإجراء عند الضغط (action): "play" لتشغيل الحلقة فوراً، أو "details" لفتح صفحة العمل
 * 8. رابط خارجي اختياري (externalUrl)
 * =========================================================================
 */
object NotificationConfig {

    private const val TAG = "NotificationConfig"

    // =====================================================================
    // 📌 مثال الإشعار المطلوب (قابل للتعديل المباشر من هنا):
    // =====================================================================
    // 1. عنوان الإشعار:
    var title: String = "حلقة جديدة"

    // 2. رسالة الإشعار:
    var message: String = "تم إصدار الحلقة الجديدة، اضغط للمشاهدة"

    // 3. الصفحة المستهدفة: "details" (تفاصيل العمل والحلقات), "home", "anime", "movies", "favorites"
    var targetPage: String = "details"

    // 4. معرف الأنمي أو الفيلم في Firebase Firestore (Document ID):
    // اتركه فارغاً إذا كنت تريد البحث بالاسم، أو ضع المعرف مثل: "k8Z12abc..."
    var contentId: String = ""

    // 5. أو اسم الأنمي/الفيلم للبحث عنه تلقائياً:
    var contentTitle: String = ""

    // 6. رقم الحلقة المحددة (مثلاً: 25 أو "25" أو "الحلقة 25"):
    var episodeNumber: String = "25"

    // 7. نوع الإجراء:
    // "play": يقوم بفتح التطبيق وتشغيل الحلقة مباشرة عبر مشغل الفيديو (ExoPlayer)
    // "details": يقوم بفتح صفحة تفاصيل العمل وقائمة الحلقات
    var action: String = "play"

    // 8. رابط صورة البوستر أو الإشعار (اختياري - يدعم روابط JPG/PNG):
    var imageUrl: String = ""

    // 9. رابط خارجي اختياري (إذا أردت توجيه المستخدم لصفحة أو رابط خارجي):
    var externalUrl: String = ""

    /**
     * إرسال الإشعار المضبوط بالقيم الافتراضية أعلاه مباشرة على هذا الجهاز
     */
    fun sendConfiguredNotification(context: Context) {
        sendCustomNotification(
            context = context,
            title = title,
            message = message,
            page = targetPage,
            contentId = contentId,
            episode = episodeNumber,
            url = externalUrl,
            action = action,
            imageUrl = imageUrl,
            contentTitle = contentTitle
        )
    }

    /**
     * إرسال إشعار مخصص بأي قيم تريدها برمجياً
     */
    fun sendCustomNotification(
        context: Context,
        title: String,
        message: String,
        page: String = "details",
        contentId: String = "",
        episode: String = "",
        url: String = "",
        action: String = "play",
        imageUrl: String = "",
        contentTitle: String = ""
    ) {
        if (imageUrl.isNotBlank()) {
            CoroutineScope(Dispatchers.IO).launch {
                val bitmap = downloadBitmap(imageUrl)
                MyFirebaseMessagingService.showNotification(
                    context = context.applicationContext,
                    title = title,
                    body = message,
                    page = page,
                    firebaseId = contentId,
                    episode = episode,
                    url = url,
                    action = action,
                    bitmap = bitmap,
                    contentTitle = contentTitle
                )
            }
        } else {
            MyFirebaseMessagingService.showNotification(
                context = context.applicationContext,
                title = title,
                body = message,
                page = page,
                firebaseId = contentId,
                episode = episode,
                url = url,
                action = action,
                bitmap = null,
                contentTitle = contentTitle
            )
        }
    }

    private fun downloadBitmap(imageUrl: String): Bitmap? {
        return try {
            val url = URL(imageUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                doInput = true
                connectTimeout = 8000
                readTimeout = 8000
                instanceFollowRedirects = true
                connect()
            }
            val input: InputStream = connection.inputStream
            BitmapFactory.decodeStream(input)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to download notification image: ${e.message}")
            null
        }
    }
}
