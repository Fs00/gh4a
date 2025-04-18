/*
 * Copyright 2011 Azwan Adli Abdullah
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gh4a.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.collection.LongSparseArray;
import androidx.appcompat.widget.PopupMenu;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import com.gh4a.Gh4Application;
import com.gh4a.R;
import com.gh4a.fragment.ConfirmationDialogFragment;
import com.gh4a.utils.ActivityResultHelpers;
import com.gh4a.utils.ApiHelpers;
import com.gh4a.utils.FileUtils;
import com.gh4a.utils.HtmlUtils;
import com.gh4a.utils.IntentUtils;
import com.gh4a.utils.RxUtils;
import com.gh4a.utils.StringUtils;
import com.gh4a.widget.ReactionBar;
import com.meisolsson.githubsdk.model.PositionalCommentBase;
import com.meisolsson.githubsdk.model.Reactions;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.reactivex.Single;
import retrofit2.Response;

public abstract class DiffViewerActivity<C extends PositionalCommentBase> extends WebViewerActivity
        implements ReactionBar.Callback, ReactionBar.ReactionDetailsCache.Listener,
        ConfirmationDialogFragment.Callback {
    protected static <C extends PositionalCommentBase> Intent fillInIntent(Intent baseIntent,
            String repoOwner, String repoName, String commitSha, String path, String diff,
            List<C> comments, int initialLine, int highlightStartLine, int highlightEndLine,
            boolean highlightisRight, IntentUtils.InitialCommentMarker initialComment) {
        Intent intent = baseIntent.putExtra("owner", repoOwner)
                .putExtra("repo", repoName)
                .putExtra("sha", commitSha)
                .putExtra("path", path)
                .putExtra("diff", diff)
                .putExtra("initial_line", initialLine)
                .putExtra("highlight_start", highlightStartLine)
                .putExtra("highlight_end", highlightEndLine)
                .putExtra("highlight_right", highlightisRight)
                .putExtra("initial_comment", initialComment);
        if (comments != null) {
            // When there are lots of comments containing a huge amount of text, the extras bundle may
            // become too large, causing a TransactionTooLargeException when launching the activity.
            // In order to avoid this problem, we store the data in compressed form
            IntentUtils.putCompressedExtra(intent, "comments", comments);
        }
        return intent;
    }

    private static final String COMMENT_ADD_URI_FORMAT =
            "comment://add?position=%d&l=%d&r=%d&isRightLine=%b";
    private static final String COMMENT_EDIT_URI_FORMAT =
            "comment://edit?position=%d&l=%d&r=%d&isRightLine=%b&id=%d";

    private static final String REACTION_PLUS_ONE_PATH = "M1 21h4V9H1v12zm22-11c0-1.1-.9-2-2-2"
            + "h-6.31l.95-4.57.03-.32c0-.41-.17-.79-.44-1.06L14.17 1 7.59 7.59C7.22 7.95 7 8.45 7 9"
            + "v10c0 1.1.9 2 2 2h9c.83 0 1.54-.5 1.84-1.22l3.02-7.05c.09-.23.14-.47.14-.73"
            + "v-1.91l-.01-.01L23 10z";
    private static final String REACTION_MINUS_ONE_PATH = "M19,15H23V3H19M15,3H6"
            + "C5.17,3 4.46,3.5 4.16,4.22L1.14,11.27C1.05,11.5 1,11.74 1,12V13.91L1,14"
            + "A2,2 0 0,0 3,16H9.31L8.36,20.57C8.34,20.67 8.33,20.77 8.33,20.88"
            + "C8.33,21.3 8.5,21.67 8.77,21.94L9.83,23L16.41,16.41"
            + "C16.78,16.05 17,15.55 17,15V5C17,3.89 16.1,3 15,3Z";
    private static final String REACTION_CONFUSED_PATH = "M8.5,11A1.5,1.5 0 0,1 7,9.5"
            + "A1.5,1.5 0 0,1 8.5,8A1.5,1.5 0 0,1 10,9.5A1.5,1.5 0 0,1 8.5,11M15.5,11"
            + "A1.5,1.5 0 0,1 14,9.5A1.5,1.5 0 0,1 15.5,8A1.5,1.5 0 0,1 17,9.5"
            + "A1.5,1.5 0 0,1 15.5,11M12,20A8,8 0 0,0 20,12A8,8 0 0,0 12,4A8,8 0 0,0 4,12"
            + "A8,8 0 0,0 12,20M12,2A10,10 0 0,1 22,12A10,10 0 0,1 12,22C6.47,22 2,17.5 2,12"
            + "A10,10 0 0,1 12,2M9,14H15A1,1 0 0,1 16,15A1,1 0 0,1 15,16H9"
            + "A1,1 0 0,1 8,15A1,1 0 0,1 9,14Z";
    private static final String REACTION_HEART_PATH = "M12,21.35L10.55,20.03"
            + "C5.4,15.36 2,12.27 2,8.5C2,5.41 4.42,3 7.5,3C9.24,3 10.91,3.81 12,5.08"
            + "C13.09,3.81 14.76,3 16.5,3C19.58,3 22,5.41 22,8.5C22,12.27 18.6,15.36 13.45,20.03"
            + "L12,21.35Z";
    private static final String REACTION_HOORAY_PATH = "M11.5,0.5C12,0.75 13,2.4 13,3.5"
            + "C13,4.6 12.33,5 11.5,5C10.67,5 10,4.85 10,3.75C10,2.65 11,2 11.5,0.5M18.5,9"
            + "C21,9 23,11 23,13.5C23,15.06 22.21,16.43 21,17.24V23H12L3,23V17.24"
            + "C1.79,16.43 1,15.06 1,13.5C1,11 3,9 5.5,9H10V6H13V9H18.5M12,16"
            + "A2.5,2.5 0 0,0 14.5,13.5H16A2.5,2.5 0 0,0 18.5,16A2.5,2.5 0 0,0 21,13.5"
            + "A2.5,2.5 0 0,0 18.5,11H5.5A2.5,2.5 0 0,0 3,13.5A2.5,2.5 0 0,0 5.5,16"
            + "A2.5,2.5 0 0,0 8,13.5H9.5A2.5,2.5 0 0,0 12,16Z";
    private static final String REACTION_LAUGH_PATH = "M12,17.5C14.33,17.5 16.3,16.04 17.11,14"
            + "H6.89C7.69,16.04 9.67,17.5 12,17.5M8.5,11A1.5,1.5 0 0,0 10,9.5A1.5,1.5 0 0,0 8.5,8"
            + "A1.5,1.5 0 0,0 7,9.5A1.5,1.5 0 0,0 8.5,11M15.5,11A1.5,1.5 0 0,0 17,9.5"
            + "A1.5,1.5 0 0,0 15.5,8A1.5,1.5 0 0,0 14,9.5A1.5,1.5 0 0,0 15.5,11M12,20"
            + "A8,8 0 0,1 4,12A8,8 0 0,1 12,4A8,8 0 0,1 20,12A8,8 0 0,1 12,20M12,2"
            + "C6.47,2 2,6.5 2,12A10,10 0 0,0 12,22A10,10 0 0,0 22,12A10,10 0 0,0 12,2Z";
    private static final String REACTION_ROCKET_PATH = "M2.81,14.12L5.64,11.29L8.17,10.79C11.39,"
            + "6.41 17.55,4.22 19.78,4.22C19.78,6.45 17.59,12.61 13.21,15.83L12.71,18.36L9.88,21.19"
            + "L9.17,17.66C7.76,17.66 7.76,17.66 7.05,16.95C6.34,16.24 6.34,16.24 6.34,14.83L2.81,"
            + "14.12M5.64,16.95L7.05,18.36L4.39,21.03H2.97V19.61L5.64,16.95M4.22,15.54L5.46,15.71L3"
            + ",18.16V16.74L4.22,15.54M8.29,18.54L8.46,19.78L7.26,21H5.84L8.29,18.54M13,9.5A1.5,1.5"
            + " 0 0,0 11.5,11A1.5,1.5 0 0,0 13,12.5A1.5,1.5 0 0,0 14.5,11A1.5,1.5 0 0,0 13,9.5Z";
    private static final String REACTION_EYES_PATH = "m6.859,3.0605c-1.5576,0 -2.81,1.1899 "
            + "-3.6426,2.8164 -0.8326,1.6265 -1.3164,3.7842 -1.3164,6.1602 0,2.3759 0.4838,4.5337 "
            + "1.3164,6.1602 0.8326,1.6265 2.085,2.8164 3.6426,2.8164 1.5576,0 2.812,-1.1899 3.6445,"
            + "-2.8164 0.8326,-1.6265 1.3164,-3.7842 1.3164,-6.1602 0,-2.3759 -0.4838,-4.5337 "
            + "-1.3164,-6.1602 -0.8326,-1.6265 -2.0869,-2.8164 -3.6445,-2.8164zM6.859,4.5547c0.769,"
            + "0 1.6223,0.6518 2.3145,2.0039 0.6921,1.3521 1.1523,3.3092 1.1523,5.4785 -0,2.1693 "
            + "-0.4602,4.1264 -1.1523,5.4785 -0.6921,1.3521 -1.5455,2.0039 -2.3145,2.0039 -0.769,0 "
            + "-1.6223,-0.6518 -2.3145,-2.0039 -0.4294,-0.8388 -0.7515,-1.9261 -0.9473,-3.1367"
            + "a2.4463,3.8986 0,0 0,2.252 2.3867,2.4463 3.8986,0 0,0 2.4473,-3.8984 2.4463,3.8986 "
            + "0,0 0,-2.4473 -3.8984,2.4463 3.8986,0 0,0 -2.4336,3.5c-0.0042,-0.1459 -0.0215,-0.2837 "
            + "-0.0215,-0.4316 0,-2.1693 0.4583,-4.1264 1.1504,-5.4785 0.6921,-1.3521 1.5455,-2.0039 "
            + "2.3145,-2.0039z M17.139,3.0234c-1.5576,0 -2.81,1.1899 -3.6426,2.8164 -0.8326,1.6265 "
            + "-1.3164,3.7842 -1.3164,6.1602 0,2.3759 0.4838,4.5337 1.3164,6.1602 0.8326,1.6265 "
            + "2.085,2.8164 3.6426,2.8164 1.5576,0 2.812,-1.1899 3.6445,-2.8164 0.8326,-1.6265 "
            + "1.3164,-3.7842 1.3164,-6.1602 0,-2.3759 -0.4838,-4.5337 -1.3164,-6.1602 -0.8326,"
            + "-1.6265 -2.0869,-2.8164 -3.6445,-2.8164zM17.139,4.5176c0.769,0 1.6223,0.6518 2.3145,"
            + "2.0039 0.6921,1.3521 1.1523,3.3092 1.1523,5.4785 -0,2.1693 -0.4602,4.1264 -1.1523,"
            + "5.4785 -0.6921,1.3521 -1.5455,2.0039 -2.3145,2.0039 -0.769,0 -1.6223,-0.6518 -2.3145,"
            + "-2.0039 -0.4294,-0.8388 -0.7515,-1.9261 -0.9473,-3.1367a2.4463,3.8986 0,0 0,2.252 "
            + "2.3867,2.4463 3.8986,0 0,0 2.4473,-3.8984 2.4463,3.8986 0,0 0,-2.4473 -3.8984,2.4463 "
            + "3.8986,0 0,0 -2.4336,3.5c-0.0042,-0.1459 -0.0215,-0.2837 -0.0215,-0.4316 0,-2.1693 "
            + "0.4583,-4.1264 1.1504,-5.4785 0.6921,-1.3521 1.5455,-2.0039 2.3145,-2.0039z";

    protected final ActivityResultLauncher<Intent> mEditLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultHelpers.ActivityResultSuccessCallback(() -> refresh())
    );

    private static final int ID_LOADER_COMMENTS = 0;

    protected String mRepoOwner;
    protected String mRepoName;
    protected String mPath;
    protected String mSha;
    private int mInitialLine;
    private int mHighlightStartLine;
    private int mHighlightEndLine;
    private boolean mHighlightIsRight;
    private IntentUtils.InitialCommentMarker mInitialComment;
    private final ReactionBar.ReactionDetailsCache mReactionDetailsCache =
            new ReactionBar.ReactionDetailsCache(this);

    protected static class CommentWrapper implements ReactionBar.Item {
        public PositionalCommentBase comment;
        public CommentWrapper(PositionalCommentBase comment) {
            this.comment = comment;
        }
        @Override
        public Object getCacheKey() {
            return comment.id();
        }
    }

    private String mDiff;
    private String[] mDiffLines;
    private final SparseArray<List<PositionalCommentBase>> mCommentsByPosition = new SparseArray<>();
    private final LongSparseArray<CommentWrapper> mWrappedComments = new LongSparseArray<>();

    private static final int MENU_ITEM_VIEW = 10;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        loadComments(true, false);
    }

    @Nullable
    @Override
    protected String getActionBarTitle() {
        return FileUtils.getFileName(mPath);
    }

    @Nullable
    @Override
    protected String getActionBarSubtitle() {
        return mRepoOwner + "/" + mRepoName;
    }

    @Override
    protected void onDestroy() {
        mReactionDetailsCache.destroy();
        super.onDestroy();
    }

    @Override
    protected void onInitExtras(Bundle extras) {
        super.onInitExtras(extras);
        mRepoOwner = extras.getString("owner");
        mRepoName = extras.getString("repo");
        mPath = extras.getString("path");
        mSha = extras.getString("sha");
        mDiff = extras.getString("diff");
        mInitialLine = extras.getInt("initial_line", -1);
        mHighlightStartLine = extras.getInt("highlight_start", -1);
        mHighlightEndLine = extras.getInt("highlight_end", -1);
        mHighlightIsRight = extras.getBoolean("highlight_right", false);
        mInitialComment = extras.getParcelable("initial_comment");
        extras.remove("initial_comment");
    }

    @Override
    protected boolean canSwipeToRefresh() {
        // no need for pull-to-refresh if everything was passed in the intent extras
        return !getIntent().hasExtra("comments");
    }

    @Override
    public void onRefresh() {
        setContentShown(false);
        loadComments(true, true);
        super.onRefresh();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.file_viewer_menu, menu);

        String viewAtTitle = getString(R.string.object_view_file_at, mSha.substring(0, 7));
        menu.add(0, MENU_ITEM_VIEW, Menu.NONE, viewAtTitle)
                .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.removeItem(R.id.download);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public void onReactionsUpdated(ReactionBar.Item item, Reactions reactions) {
        CommentWrapper wrapper = (CommentWrapper) item;
        wrapper.comment = wrapper.comment.withReactions(reactions);
        replaceOutdatedComment(wrapper.comment);
        onDataReady();
    }

    private void replaceOutdatedComment(PositionalCommentBase updatedComment) {
        List<PositionalCommentBase> comments = mCommentsByPosition.get(updatedComment.position());
        int outdatedCommentIndex = -1;
        for (int i = 0; i < comments.size(); i++) {
            if (comments.get(i).id().equals(updatedComment.id())) {
                outdatedCommentIndex = i;
                break;
            }
        }
        if (outdatedCommentIndex != -1) {
            comments.set(outdatedCommentIndex, updatedComment);
        }
    }

    @Override
    protected String generateHtml(String cssTheme, boolean addTitleHeader) {
        StringBuilder content = new StringBuilder();
        boolean authorized = Gh4Application.get().isAuthorized();
        String title = addTitleHeader ? getDocumentTitle() : null;

        content.append("<html><head><title>");
        if (title != null) {
            content.append(title);
        }
        content.append("</title>");
        HtmlUtils.writeCssInclude(content, "diff", cssTheme);
        HtmlUtils.writeScriptInclude(content, "codeutils");
        content.append("</head><body");

        int highlightInsertPos = content.length();
        content.append(">");
        if (title != null) {
            content.append("<h2>").append(title).append("</h2>");
        }
        content.append("<pre>");

        mDiffLines = mDiff != null ? mDiff.split("\n") : new String[0];

        int highlightStartLine = -1, highlightEndLine = -1;
        int leftDiffPosition = -1, rightDiffPosition = -1;

        for (int i = 0; i < mDiffLines.length; i++) {
            String line = mDiffLines[i];
            String cssClass = null;
            if (line.startsWith("@@")) {
                int[] lineNumbers = StringUtils.extractDiffHunkLineNumbers(line);
                if (lineNumbers != null) {
                    leftDiffPosition = lineNumbers[0];
                    rightDiffPosition = lineNumbers[1];
                }
                cssClass = "change";
            } else if (line.startsWith("+")) {
                ++rightDiffPosition;
                cssClass = "add";
            } else if (line.startsWith("-")) {
                ++leftDiffPosition;
                cssClass = "remove";
            } else {
                ++leftDiffPosition;
                ++rightDiffPosition;
            }

            int pos = mHighlightIsRight ? rightDiffPosition : leftDiffPosition;
            if (pos != -1 && pos == mHighlightStartLine && highlightStartLine == -1) {
                highlightStartLine = i;
            }
            if (pos != -1 && pos == mHighlightEndLine && highlightEndLine == -1) {
                highlightEndLine = i;
            }

            content.append("<div id=\"line").append(i).append("\"");
            if (cssClass != null) {
                content.append("class=\"").append(cssClass).append("\"");
            }
            if (authorized) {
                String uri = String.format(Locale.US, COMMENT_ADD_URI_FORMAT,
                        i, leftDiffPosition, rightDiffPosition, line.startsWith("+"));
                content.append(" onclick=\"javascript:location.href='");
                content.append(uri).append("'\"");
            }
            content.append(">").append(TextUtils.htmlEncode(line)).append("</div>");

            List<PositionalCommentBase> comments = mCommentsByPosition.get(i);
            if (comments != null) {
                for (PositionalCommentBase comment : comments) {
                    long id = comment.id();
                    mWrappedComments.put(id, new CommentWrapper(comment));
                    content.append("<div ").append("id=\"comment").append(id).append("\"");
                    content.append(" class=\"comment");
                    if (mInitialComment != null && mInitialComment.matches(id, null)) {
                        content.append(" highlighted");
                    }
                    content.append("\"");
                    if (authorized) {
                        String uri = String.format(Locale.US, COMMENT_EDIT_URI_FORMAT,
                                i, leftDiffPosition, rightDiffPosition, line.startsWith("+"), id);
                        content.append(" onclick=\"javascript:location.href='");
                        content.append(uri).append("'\"");
                    }
                    content.append("><div class=\"change\">");
                    content.append(getString(R.string.commit_comment_header,
                            "<b>" + ApiHelpers.getUserLogin(this, comment.user()) + "</b>",
                            StringUtils.formatRelativeTime(DiffViewerActivity.this, comment.createdAt(), true)));
                    content.append("</div>").append(comment.bodyHtml());

                    Reactions reactions = comment.reactions();
                    if (reactions.totalCount() > 0) {
                        content.append("<div>");
                        appendReactionSpan(content, reactions.plusOne(), REACTION_PLUS_ONE_PATH);
                        appendReactionSpan(content, reactions.minusOne(), REACTION_MINUS_ONE_PATH);
                        appendReactionSpan(content, reactions.confused(), REACTION_CONFUSED_PATH);
                        appendReactionSpan(content, reactions.heart(), REACTION_HEART_PATH);
                        appendReactionSpan(content, reactions.laugh(), REACTION_LAUGH_PATH);
                        appendReactionSpan(content, reactions.hooray(), REACTION_HOORAY_PATH);
                        appendReactionSpan(content, reactions.rocket(), REACTION_ROCKET_PATH);
                        appendReactionSpan(content, reactions.eyes(), REACTION_EYES_PATH);
                        content.append("</div>");
                    }
                    content.append("</div>");
                }
            }
        }

        if (mInitialLine > 0) {
            content.insert(highlightInsertPos, " onload='scrollToElement(\"line"
                    + mInitialLine + "\")' onresize='scrollToHighlight();'");
        } else if (mInitialComment != null) {
            content.insert(highlightInsertPos, " onload='scrollToElement(\"comment"
                    + mInitialComment.commentId + "\")' onresize='scrollToHighlight();'");
        } else if (highlightStartLine != -1 && highlightEndLine != -1) {
            content.insert(highlightInsertPos, " onload='highlightDiffLines("
                    + highlightStartLine + "," + highlightEndLine
                    + ")' onresize='scrollToHighlight();'");
        }

        content.append("</pre></body></html>");
        return content.toString();
    }

    private void appendReactionSpan(StringBuilder content, int count, String iconPathContents) {
        if (count == 0) {
            return;
        }
        content.append("<span class='reaction'>");
        content.append("<svg width='14px' height='14px' viewBox='0 0 24 24'><path d='");
        content.append(iconPathContents).append("' /></svg>");
        content.append(count).append("</span>");
    }

    @Override
    protected String getDocumentTitle() {
        return getString(R.string.diff_print_document_title,
                FileUtils.getFileName(mPath), mSha.substring(0, 7), mRepoOwner, mRepoName);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Uri url = createUrl("", 0L);

        switch (item.getItemId()) {
            case R.id.browser:
                IntentUtils.launchBrowser(this, url);
                return true;
            case R.id.share:
                IntentUtils.share(this, getString(R.string.share_commit_subject,
                        mSha.substring(0, 7), mRepoOwner + "/" + mRepoName), url);
                return true;
            case MENU_ITEM_VIEW:
                startActivity(FileViewerActivity.makeIntent(this, mRepoOwner, mRepoName, mSha, mPath));
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onConfirmed(String tag, Parcelable data) {
        long id = ((Bundle) data).getLong("id");
        deleteComment(id);
    }

    private void addCommentsToMap(List<C> comments) {
        mCommentsByPosition.clear();
        for (PositionalCommentBase comment : comments) {
            if (!TextUtils.equals(comment.path(), mPath)) {
                continue;
            }
            int position = comment.position();
            List<PositionalCommentBase> commentsByPos = mCommentsByPosition.get(position);
            if (commentsByPos == null) {
                commentsByPos = new ArrayList<>();
                mCommentsByPosition.put(position, commentsByPos);
            }
            commentsByPos.add(comment);
        }
    }

    @Override
    protected void handleUrlLoad(Uri uri) {
        if (!uri.getScheme().equals("comment")) {
            super.handleUrlLoad(uri);
            return;
        }

        int line = Integer.parseInt(uri.getQueryParameter("position"));
        int leftLine = Integer.parseInt(uri.getQueryParameter("l"));
        int rightLine = Integer.parseInt(uri.getQueryParameter("r"));
        boolean isRightLine = Boolean.parseBoolean(uri.getQueryParameter("isRightLine"));
        String lineText = mDiffLines[line];
        String idParam = uri.getQueryParameter("id");
        long id = idParam != null ? Long.parseLong(idParam) : 0L;

        CommentActionPopup p = new CommentActionPopup(id, line, lineText, leftLine, rightLine,
                mLastTouchDown.x, mLastTouchDown.y, isRightLine);
        p.show();
    }

    @Override
    public boolean canAddReaction() {
        return true;
    }

    private void refresh() {
        // Make sure we load the comments from remote, as we now know they've changed
        getIntent().removeExtra("comments");

        // Make sure our callers are aware of the change
        setResult(RESULT_OK);

        mWrappedComments.clear();
        loadComments(false, true);
        setContentShown(false);
    }

    protected abstract Single<List<C>> getCommentsSingle(boolean bypassCache);
    protected abstract void openCommentDialog(long id, long replyToId, String line,
            int position, int leftLine, int rightLine, PositionalCommentBase commitComment);
    protected abstract Single<Response<Void>> deleteCommentSingle(long id);
    protected abstract boolean canReply();
    protected abstract Uri createUrl(String lineId, long replyId);

    private String createLineLinkId(int line, boolean isRight) {
        return (isRight ? "R" : "L") + line;
    }

    private void deleteComment(long id) {
        deleteCommentSingle(id)
                .map(ApiHelpers::mapToBooleanOrThrowOnFailure)
                .compose(RxUtils.wrapForBackgroundTask(this, R.string.deleting_msg,
                        getString(R.string.error_delete_commit_comment)))
                .subscribe(result -> refresh(),
                        error -> handleActionFailure("Comment deletion failed", error));
    }

    private void loadComments(boolean useIntentExtraIfPresent, boolean force) {
        List<C> intentComments = useIntentExtraIfPresent
                ? IntentUtils.getCompressedExtra(getIntent(), "comments") : null;
        Single<List<C>> commentsSingle = intentComments != null
                ? Single.just(intentComments)
                : getCommentsSingle(force).compose(makeLoaderSingle(ID_LOADER_COMMENTS, force));

        commentsSingle.subscribe(result -> {
            addCommentsToMap(result);
            onDataReady();
        }, this::handleLoadFailure);
    }

    private class CommentActionPopup extends PopupMenu implements
            PopupMenu.OnMenuItemClickListener {
        private final long mId;
        private final int mPosition;
        private final int mLeftLine;
        private final int mRightLine;
        private final String mLineText;
        private final boolean mIsRightLine;
        private ReactionBar.AddReactionMenuHelper mReactionMenuHelper;

        public CommentActionPopup(long id, int position, String lineText,
                int leftLine, int rightLine, int x, int y, boolean isRightLine) {
            super(DiffViewerActivity.this, findViewById(R.id.popup_helper));

            mId = id;
            mPosition = position;
            mLeftLine = leftLine;
            mRightLine = rightLine;
            mLineText = lineText;
            mIsRightLine = isRightLine;

            Menu menu = getMenu();
            CommentWrapper comment = mWrappedComments.get(mId);
            String ownLogin = Gh4Application.get().getAuthLogin();

            getMenuInflater().inflate(R.menu.commit_comment_actions, menu);
            if (id == 0 || !canReply()) {
                menu.removeItem(R.id.reply);
            }
            if (id == 0 || !ApiHelpers.loginEquals(comment.comment.user(), ownLogin)) {
                menu.removeItem(R.id.edit);
                menu.removeItem(R.id.delete);
            }

            if (id != 0) {
                Menu reactionMenu = menu.findItem(R.id.react).getSubMenu();
                getMenuInflater().inflate(R.menu.reaction_menu, reactionMenu);

                mReactionMenuHelper = new ReactionBar.AddReactionMenuHelper(DiffViewerActivity.this,
                        reactionMenu, DiffViewerActivity.this, comment, mReactionDetailsCache);
                mReactionMenuHelper.startLoadingIfNeeded();
            } else {
                menu.removeItem(R.id.react);
            }

            View anchor = findViewById(R.id.popup_helper);
            anchor.layout(x, y, x + 1, y + 1);

            setOnMenuItemClickListener(this);
        }

        @Override
        public boolean onMenuItemClick(MenuItem item) {
            if (mReactionMenuHelper != null && mReactionMenuHelper.onItemClick(item)) {
                return true;
            }

            switch (item.getItemId()) {
                case R.id.delete: {
                    Bundle data = new Bundle();
                    data.putLong("id", mId);

                    ConfirmationDialogFragment.show(DiffViewerActivity.this,
                            R.string.delete_comment_message, R.string.delete, data,
                            "deleteconfirm");
                    break;
                }
                case R.id.reply:
                    openCommentDialog(0L, mId, mLineText, mPosition, mLeftLine, mRightLine, null);
                    break;
                case R.id.edit:
                    CommentWrapper wrapper = mWrappedComments.get(mId);
                    PositionalCommentBase comment = wrapper != null ? wrapper.comment : null;
                    openCommentDialog(mId, 0L, mLineText, mPosition, mLeftLine, mRightLine, comment);
                    break;
                case R.id.add_comment:
                    openCommentDialog(0L, 0L, mLineText, mPosition, mLeftLine, mRightLine, null);
                    break;
                case R.id.share:
                    Uri url = createUrl(createLineLinkId(mIsRightLine ? mRightLine : mLeftLine,
                            mIsRightLine), mId);
                    String subject = getString(R.string.share_commit_subject, mSha.substring(0, 7),
                            mRepoOwner + "/" + mRepoName);
                    IntentUtils.share(DiffViewerActivity.this, subject, url);
                    break;
            }
            return true;
        }
    }
}
