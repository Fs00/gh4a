package com.gh4a.job;

import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;

import com.evernote.android.job.Job;
import com.evernote.android.job.JobManager;
import com.evernote.android.job.JobRequest;
import com.gh4a.Gh4Application;
import com.gh4a.R;
import com.gh4a.activities.home.HomeActivity;
import com.gh4a.loader.NotificationHolder;
import com.gh4a.loader.NotificationListLoader;
import com.gh4a.resolver.BrowseFilter;
import com.gh4a.utils.ApiHelpers;
import com.gh4a.utils.AvatarHandler;
import com.gh4a.utils.UiUtils;

import org.eclipse.egit.github.core.Notification;
import org.eclipse.egit.github.core.Repository;
import org.eclipse.egit.github.core.User;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class NotificationsJob extends Job {
    private static final String GROUP_ID_GITHUB = "github_notifications";
    private static final int ID_SUMMARY = 1;
    public static final String TAG = "job_notifications";

    public static void scheduleJob(int intervalMinutes) {
        new JobRequest.Builder(TAG)
                .setPeriodic(TimeUnit.MINUTES.toMillis(intervalMinutes),
                        TimeUnit.MINUTES.toMillis(5))
                .setRequiredNetworkType(JobRequest.NetworkType.CONNECTED)
                .setRequirementsEnforced(true)
                .setUpdateCurrent(true)
                .build()
                .schedule();
    }

    public static void cancelJob() {
        JobManager.instance().cancelAllForTag(NotificationsJob.TAG);
    }

    @NonNull
    @Override
    protected Result onRunJob(Params params) {
        try {
            List<NotificationHolder> notifications =
                    NotificationListLoader.loadNotifications(false, false);
            // strip out repo items
            Iterator<NotificationHolder> iter = notifications.iterator();
            while (iter.hasNext()) {
                if (iter.next().notification == null) {
                    iter.remove();
                }
            }

            if (notifications.isEmpty()) {
                return Result.SUCCESS;
            }

            NotificationManagerCompat notificationManager =
                    NotificationManagerCompat.from(getContext());
            notificationManager.cancelAll();

            int accentColor = UiUtils.resolveColor(getContext(), R.attr.colorAccent);

            showSummaryNotification(notificationManager, notifications.size(), accentColor);
            for (NotificationHolder holder : notifications) {
                showSingleNotification(notificationManager, accentColor, holder.notification);
            }
        } catch (IOException e) {
            return Result.FAILURE;
        }

        return Result.SUCCESS;
    }

    private void showSingleNotification(NotificationManagerCompat notificationManager,
            int accentColor, Notification notification) {
        Repository repository = notification.getRepository();
        User owner = repository.getOwner();
        String title = owner.getLogin() + "/" + repository.getName();
        long when = notification.getUpdatedAt() != null
                ? notification.getUpdatedAt().getTime()
                : System.currentTimeMillis();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getContext(),
                Gh4Application.CHANNEL_GITHUB_NOTIFICATIONS)
                .setSmallIcon(R.drawable.octodroid)
                .setLargeIcon(loadRoundUserAvatar(owner))
                .setGroup(GROUP_ID_GITHUB)
                .setWhen(when)
                .setShowWhen(true)
                .setColor(accentColor)
                .setContentTitle(title)
                .setAutoCancel(true)
                .setContentText(notification.getSubject().getTitle());

        String url = notification.getSubject().getUrl();
        if (url != null) {
            Uri uri = ApiHelpers.normalizeUri(Uri.parse(url));
            Intent intent = BrowseFilter.makeRedirectionIntent(getContext(), uri, null);
            PendingIntent pendingIntent =
                    PendingIntent.getActivity(getContext(), 0, intent, 0);
            builder.setContentIntent(pendingIntent);
        }

        int id = ID_SUMMARY + 1;
        try {
            id = Integer.parseInt(notification.getId());
        } catch (NumberFormatException e) {
            // ignored
        }
        notificationManager.notify(id, builder.build());
    }

    private void showSummaryNotification(NotificationManagerCompat notificationManager,
            int numNotifications, int accentColor) {
        String title = getContext().getString(R.string.unread_notifications_summary_title);
        String text = getContext().getResources()
                .getQuantityString(R.plurals.unread_notifications_summary_text, numNotifications,
                        numNotifications);
        PendingIntent contentIntent = PendingIntent.getActivity(getContext(), 0,
                HomeActivity.makeIntent(getContext(), R.id.notifications), 0);
        notificationManager.notify(ID_SUMMARY, new NotificationCompat.Builder(
                getContext(), Gh4Application.CHANNEL_GITHUB_NOTIFICATIONS)
                .setSmallIcon(R.drawable.octodroid)
                .setGroup(GROUP_ID_GITHUB)
                .setGroupSummary(true)
                .setColor(accentColor)
                .setContentIntent(contentIntent)
                .setContentTitle(title)
                .setContentText(text)
                .setNumber(numNotifications)
                .build());
    }

    private Bitmap loadRoundUserAvatar(User user) {
        Bitmap avatar = AvatarHandler.loadUserAvatarSynchronously(getContext(), user);
        if (avatar == null) {
            return null;
        }

        final Bitmap output = Bitmap.createBitmap(avatar.getWidth(), avatar.getHeight(),
                Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(output);

        final Paint paint = new Paint();
        final Rect rect = new Rect(0, 0, avatar.getWidth(), avatar.getHeight());

        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);
        paint.setColor(Color.BLACK);
        canvas.drawOval(new RectF(rect), paint);

        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(avatar, rect, rect, paint);

        return output;
    }
}