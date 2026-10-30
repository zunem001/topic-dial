package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getPluralString;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import org.telegram.ui.recyclerview.ChatListItemAnimator;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.R;
import org.telegram.messenger.TopicsController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.Forum.ForumUtilities;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.GradientClip;
import org.telegram.ui.Stories.recorder.HintView2;
import org.telegram.ui.TopicCreateFragment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.BitwiseUtils;

@SuppressLint("ViewConstructor")
public class TopicsTabsView extends FrameLayout implements NotificationCenter.NotificationCenterDelegate, FactorAnimator.Target {
    public static final int ANIMATOR_ID_TOPICS_VISIBILITY = 0;
    public static final int ANIMATOR_ID_ROLL_RESERVE = 1;
    public static final int ANIMATOR_ID_ROLL_DISPLAY = 2;
    public static final int ANIMATOR_ID_ISLAND_JOIN = 3;

    public static final int TOP_TABS_HEIGHT = 36;
    public static final int SIDE_TABS_WIDTH = 64;

    private static final int ROLL_BUTTON_TOP_WIDTH = 36;
    private static final int ROLL_BUTTON_TOP_KEEP_WIDTH = 112;
    private static final int ROLL_BUTTON_SIDE_HEIGHT = 52;

    // Rolled up, the wheel sits in a well 6 dp inside a 48 dp glass island, so their rounded ends
    // are concentric. With a single top panel (the pinned message) the island spans the row, like a
    // radio: the wheel is the dial and the panel is its display, its own line the seam between them.
    private static final int ISLAND_HEIGHT = 48;
    private static final int ISLAND_RIM = TopicsRollWheel.INSET;
    private static final int ISLAND_ALONE = TopicsRollWheel.WIDTH + ISLAND_RIM * 2;
    /** Where a panel in the island starts, so its line (13 dp in) is 10 dp after the wheel. */
    private static final int DISPLAY_LEFT = 7 + ISLAND_RIM + TopicsRollWheel.WIDTH + 10 - 13;
    /** Where the display starts in the bottom island, its line 12 dp in and 10 dp after the wheel. */
    private static final int BOTTOM_DISPLAY_LEFT = 7 + TopicsRollWheel.WIDTH + 10 - 12;
    // Rolled out, the bar keeps the size it always had, and its surface is the wheel's track: the
    // toggle sits in its left end and « (or Keep open) in its right end, each BAR_KEY wide, and the
    // tabs lie between them on the wheel's scale, a tick every SCALE_TICK. A puck of glass LENS_HEIGHT
    // tall sits under the topic you are in. For side tabs the island stands up at the top of the rail,
    // V_ISLAND_W by V_ISLAND_H, the wheel on its side in it; at the bottom the wheel's track is the
    // island, in the bar's own row.
    private static final int BAR_KEY = 36;
    private static final int SCALE_TICK = 17;
    private static final int LENS_HEIGHT = 30;
    private static final int V_ISLAND_W = ISLAND_HEIGHT, V_ISLAND_H = ISLAND_ALONE;

    private final BoolAnimator animatorTopicsVisibility = new BoolAnimator(ANIMATOR_ID_TOPICS_VISIBILITY,
        this, CubicBezierInterpolator.EASE_OUT_QUINT, 380L, true);

    // Roll. The bar either keeps its own place (the classic tabs) or is "rollable": it winds up
    // into a small wheel that shares a glass island with the pinned message, in the row the tabs
    // used to take. Swiping the wheel turns it to the next or previous topic while the island's
    // display tunes to the topic under the lens; tapping it rolls the bar back out across the row,
    // like a carpet, over the chat. "Keep open" on a rolled-out bar keeps it open.
    // reserve: 1 while the bar takes its own space in the chat, 0 while it is rolled up (rollable).
    // rollT:   how far the bar is rolled out, 0 = just the wheel, 1 = the whole bar.
    private final BoolAnimator animatorRollReserve = new BoolAnimator(ANIMATOR_ID_ROLL_RESERVE,
        this, CubicBezierInterpolator.EASE_OUT_QUINT, 420L, true);
    private static final CubicBezierInterpolator EASE_IN_OUT_CUBIC = new CubicBezierInterpolator(0.65, 0, 0.35, 1);
    private final TopicsRollWheel rollWheel;
    private final RollButton rollButtonTop;
    private final RollButton rollButtonSide;
    private boolean rollable;
    private boolean rolledOut = true;
    private float rollT = 1f;
    private ValueAnimator rollAnimator;
    private float topPanelsHeight;
    private int topPanelsCount;
    private int pendingSwitchDirection;
    // The lens's glide on the bar and on the rail (see LensGlide).
    private LensGlide topGlide, sideGlide;
    // A topic picked from a bar out over the chat is opening: the bar winds up once it has (see onTopicOpened).
    private boolean topicOpening, rollUpOnOpen;
    private long pickTime;
    private Runnable onRollChanged;
    private final Runnable rollUpAfterSelect = this::rollUpAfterPick;

    // Picked a topic from a bar out over the chat: it winds back up a moment later, but not while the
    // topic is still opening, whose frames would make it stutter; it goes as soon as the topic is in.
    private void rollUpAfterPick() {
        final long waited = SystemClock.uptimeMillis() - pickTime;
        if (topicOpening && waited < 1200) {
            rollUpOnOpen = true;
            AndroidUtilities.runOnUIThread(rollUpAfterSelect, 1200 - waited);
            return;
        }
        rollUpOnOpen = false;
        setRolledOut(false, true);
    }

    /** The chat has opened the topic and drawn its messages: a bar waiting for it can wind up now. */
    public void onTopicOpened() {
        topicOpening = false;
        if (rollUpOnOpen) {
            rollUpOnOpen = false;
            AndroidUtilities.cancelRunOnUIThread(rollUpAfterSelect);
            setRolledOut(false, true);
        }
    }

    /**
     * Whether the glass or the wheel is on the fast part of a move, where a frame held up by opening a
     * topic would show as a stop and a jump: the chat waits for it before opening one.
     */
    public boolean isGlassMoving() {
        return topGlide != null && topGlide.isMoving() || sideGlide != null && sideGlide.isMoving()
            || rollWheel != null && rollWheel.isShown() && rollWheel.getAlpha() > 0 && rollWheel.isTurningFast();
    }
    private HintView2 rollHint;

    // The island: whether the chat's top panel shares it, and its display while the wheel turns.
    private final BoolAnimator animatorIslandJoin = new BoolAnimator(ANIMATOR_ID_ISLAND_JOIN,
        this, CubicBezierInterpolator.EASE_OUT_QUINT, 420L);
    private final BoolAnimator animatorRollDisplay = new BoolAnimator(ANIMATOR_ID_ROLL_DISPLAY,
        this, CubicBezierInterpolator.EASE_OUT_QUINT, 260L);
    private final Runnable hideRollDisplay = () -> animatorRollDisplay.setValue(false, true);

    // Controls sit in wells, readouts sit on the glass: Back and the menu in the chat's header sit in
    // wells 6 dp inside their keys, like the wheel's in its island and the tabs' in the bar.
    private ActionBar header;
    private final TopicsRollWheel.Well keyWell = new TopicsRollWheel.Well();
    private final RectF keyWellRect = new RectF();
    private final AnimatedFloat animatedDisplayWidth = new AnimatedFloat(this, 0, 220, CubicBezierInterpolator.EASE_OUT_QUINT);
    private int displayIndex = -1, displayDirection;
    private long displayChangedAt;
    private Text displayTitle, displayChip, displaySender, displayMessage;
    private boolean displayPinned, displayChipMuted, displayTyping;
    private float displayContentWidth;
    private Drawable displayPinDrawable;
    private final Paint displayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF displayRect = new RectF();
    private final HashMap<Long, CharSequence[]> displayPreviews = new HashMap<>();

    private final int currentAccount;
    private final long dialogId;
    private final Theme.ResourcesProvider resourcesProvider;
    private final boolean mono;
    private final boolean bot;
    private final BaseFragment fragment;
    private final boolean canShowProgress;

    private final FrameLayout topTabsContainer;
    private final UniversalRecyclerView topTabs;
    private final @Nullable VerticalTabView botCreateTopicButtonVertical;
    private final @Nullable HorizontalTabView botCreateTopicButtonHorizontal;
    private final ImageView closeButtonTop;
    private final ImageView closeButtonSide;
    private final ImageView toggleButtonTop;
    private final ImageView toggleButtonSide;
    private final FrameLayout sideTabsContainer;
    private final UniversalRecyclerView sideTabs;

    private long lastSelectedTopicId;
    private long animateFromSelectedTopicId;

    public TopicsTabsView(Context context, BaseFragment fragment, int currentAccount, long dialogId, Theme.ResourcesProvider resourcesProvider) {
        super(context);

        this.fragment = fragment;
        this.currentAccount = currentAccount;
        this.dialogId = dialogId;
        this.resourcesProvider = resourcesProvider;

        mono = ChatObject.isMonoForum(MessagesController.getInstance(currentAccount).getChat(-dialogId));
        bot = UserObject.isBotForumWithEditableTopics(MessagesController.getInstance(currentAccount).getUser(dialogId));
        canShowProgress = !UserConfig.getInstance(currentAccount).getPreferences().getBoolean("topics_end_reached_" + -dialogId, false);

        setClipChildren(true);
        setClipToPadding(true);
        setWillNotDraw(false);

        topTabsContainer = new FrameLayout(context);
        addView(topTabsContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, TOP_TABS_HEIGHT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 7, 7, 7, 7));

        sideTabsContainer = new FrameLayout(context);
        addView(sideTabsContainer, LayoutHelper.createFrame(64, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.FILL_VERTICAL, 7, 7, 7, 7));

        topTabs = new UniversalRecyclerView(context, currentAccount, 0, this::fillHorizontalTabs, this::onTabClick, this::onTabLongClick, resourcesProvider) {
            private final GradientClip clip = new GradientClip();
            private final AnimatedFloat animatedClipL = new AnimatedFloat(this, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
            private final AnimatedFloat animatedClipR = new AnimatedFloat(this, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
            private final LensGlide glide = topGlide = new LensGlide(this);
            private final PressDrop drop = new PressDrop(this);
            private final Paint keepCoverPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final RectF lensRect = new RectF(), dropRect = new RectF();
            // Where the lens was drawn last, from its tab's left, and where its glide sets off from.
            private float lensTabX = Float.NaN, fromLeft = Float.NaN, fromRight = Float.NaN;
            private final TopicsRollWheel.Puck puck = new TopicsRollWheel.Puck();
            private final Paint knurlPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private float scrolled, visibleWidth;
            private float pinX;
            private Drawable pinIcon;
            private int pinIconColor;
            {
                keepCoverPaint.setColor(0xFF000000);
                keepCoverPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
                knurlPaint.setStrokeCap(Paint.Cap.ROUND);
                knurlPaint.setStrokeWidth(dp(1.25f));
            }

            @Override
            public void onScrolled(int dx, int dy) {
                super.onScrolled(dx, dy);
                // The scale moves with the tabs, and the drum's glows follow what goes out of sight.
                scrolled += dx;
                TopicsTabsView.this.invalidate();
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                final float clipAlphaL = animatedClipL.set(canScrollHorizontally(-1));
                final float clipAlphaR = animatedClipR.set(canScrollHorizontally(1));
                final float keepCover = getKeepCover();
                // On a rolled-out bar the tabs fade away just before "Keep open", so it reads on its own.
                final float coverLeft = getKeepCoverLeft();
                visibleWidth = getTopTabsVisibleWidth();
                final boolean needClip = clipAlphaL > 0 || clipAlphaR > 0 || keepCover > 0;
                if (needClip) {
                    canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), 0xFF, Canvas.ALL_SAVE_FLAG);
                }
                collectScaleMarks();
                drawLens(canvas);
                drawScale(canvas);
                super.dispatchDraw(canvas);
                if (drop.isMoving()) {
                    invalidate();
                }
                if (needClip) {
                    canvas.save();
                    if (clipAlphaL > 0) {
                        AndroidUtilities.rectTmp.set(0, 0, dp(18), getHeight());
                        clip.draw(canvas, AndroidUtilities.rectTmp, GradientClip.LEFT, clipAlphaL);
                    }
                    if (clipAlphaR > 0) {
                        AndroidUtilities.rectTmp.set(getWidth() - dp(18), 0, getWidth(), getHeight());
                        clip.draw(canvas, AndroidUtilities.rectTmp, GradientClip.RIGHT, clipAlphaR);
                    }
                    if (keepCover > 0) {
                        AndroidUtilities.rectTmp.set(coverLeft, 0, coverLeft + dp(18), getHeight());
                        clip.draw(canvas, AndroidUtilities.rectTmp, GradientClip.RIGHT, keepCover);
                        keepCoverPaint.setAlpha((int) (0xFF * keepCover));
                        canvas.drawRect(coverLeft + dp(18), 0, getWidth(), getHeight(), keepCoverPaint);
                    }
                    canvas.restore();
                    canvas.restore();
                }
            }

            // The drum curves away at the ends of the well: a topic nearing either end of the bar
            // shrinks a little about its middle, its lens and its light with it, like the icons on
            // the wheel as they turn away.
            private float drumScale(float x) {
                return lerp(0.84f, 1f, smoothstep(-dp(24), dp(36), Math.min(x, visibleWidth - x)));
            }

            /** Where a point of a tab is drawn once the drum has shrunk the tab about its middle. */
            private float onDrum(View tab, float x) {
                final float middle = tab.getX() + tab.getWidth() / 2f;
                return middle + (x - middle) * drumScale(middle);
            }

            @Override
            public boolean dispatchTouchEvent(MotionEvent e) {
                drop.onTouch(e);
                return super.dispatchTouchEvent(e);
            }

            @Override
            public boolean drawChild(Canvas canvas, View child, long drawingTime) {
                if (child instanceof HorizontalTabView) {
                    final float middle = child.getX() + child.getWidth() / 2f, s = drumScale(middle) * drop.getScale(child);
                    if (s < 0.999f) {
                        canvas.save();
                        canvas.scale(s, s, middle, child.getY() + child.getHeight() / 2f);
                        final boolean result = super.drawChild(canvas, child, drawingTime);
                        canvas.restore();
                        return result;
                    }
                }
                return super.drawChild(canvas, child, drawingTime);
            }

            // The lens: a puck of white glass (frosted grey in the dark) under the whole topic you are
            // in, its icon in the puck's round end. It is the wheel's bead opened out: rolling out, it
            // opens out of the bead; pick another topic and it glides there, taking its width, its
            // leading end first, so it stretches a little on the way (see LensGlide). Under it, a
            // drop of glass answers a finger (see PressDrop).
            private void drawLens(Canvas canvas) {
                if (lastSelectedTopicId != currentTopicId) {
                    animateFromSelectedTopicId = lastSelectedTopicId;
                    // Picked again before it landed: the lens sets off from where it is, not from where it was going.
                    final boolean moving = glide.isRunning() && !Float.isNaN(lensTabX);
                    fromLeft = moving ? lensRect.left - lensTabX : Float.NaN;
                    fromRight = moving ? lensRect.right - lensTabX : Float.NaN;
                    glide.start();
                }
                lastSelectedTopicId = currentTopicId;
                HorizontalTabView fromSelectedTab = null;
                HorizontalTabView selectedTab = null;
                for (int i = 0; i < getChildCount(); ++i) {
                    View child = getChildAt(i);
                    if (child instanceof HorizontalTabView) {
                        HorizontalTabView tab = (HorizontalTabView) child;
                        if (tab.isAdd)
                            continue;
                        if (tab.getTopicId() == currentTopicId) {
                            selectedTab = tab;
                        }
                        if (tab.getTopicId() == animateFromSelectedTopicId) {
                            fromSelectedTab = tab;
                        }
                    }
                }
                glide.step();
                final float lead = glide.lead(), trail = glide.trail();
                final boolean dark = rollWheel.isDark();
                drop.markLens(selectedTab);
                final View pressed = drop.getTab();
                if (pressed instanceof HorizontalTabView && rollT >= 1f) {
                    final float half = dp(LENS_HEIGHT / 2f), cy = pressed.getY() + pressed.getHeight() / 2f;
                    dropRect.set(pressed.getX() + dp(1), cy - half, pressed.getX() + ((HorizontalTabView) pressed).getLensWidth() - dp(1), cy + half);
                    final float s = drumScale(dropRect.centerX());
                    canvas.save();
                    canvas.scale(s, s, dropRect.centerX(), dropRect.centerY());
                    drop.draw(canvas, dropRect, half, selectedTab, glide.isRunning(), lead, dark);
                    canvas.restore();
                }
                if (selectedTab == null) {
                    lensTabX = Float.NaN;
                    glide.setProgress(0, 0);
                    return;
                }
                float l = selectedTab.getX() + dp(1), r = selectedTab.getX() + selectedTab.getLensWidth() - dp(1), appear = 1f, narrow = 1f;
                glide.setProgress(0, 0);
                if (glide.isRunning()) {
                    if (fromSelectedTab != null) {
                        final float fl = fromSelectedTab.getX() + (Float.isNaN(fromLeft) ? dp(1) : fromLeft);
                        final float fr = fromSelectedTab.getX() + (Float.isNaN(fromRight) ? fromSelectedTab.getLensWidth() - dp(1) : fromRight);
                        final boolean right = l > fl;
                        final float left = lerp(fl, l, right ? trail : lead), end = lerp(fr, r, right ? lead : trail);
                        final float rest = lerp(fr - fl, r - l, (lead + trail) / 2f);
                        narrow = 1f - 0.1f * Utilities.clamp((end - left - rest) / rest, 1f, 0f);
                        glide.setProgress(Math.max(Math.abs(left - l), Math.abs(end - r)), Math.max(Math.abs(fl - l), Math.abs(fr - r)));
                        l = left;
                        r = end;
                    } else {
                        // The topic you were in is out of sight: the lens comes in where it lands.
                        appear = lead;
                    }
                }
                float half = dp(LENS_HEIGHT / 2f) * narrow;
                if (rollT < 1f && sidemenuT <= 0.5f) {
                    final float open = smoothstep(0f, 0.32f, rollT), c = selectedTab.getX() + selectedTab.getIconCenterX();
                    l = lerp(c - dp(16), l, open);
                    r = lerp(c + dp(16), r, open);
                    half = lerp(dp(16), half, open);
                }
                final float cy = selectedTab.getY() + selectedTab.getHeight() / 2f;
                lensRect.set(l, cy - half, r, cy + half);
                lensTabX = selectedTab.getX();
                final float s = drumScale(lensRect.centerX()) * lerp(0.6f, 1f, appear) * drop.getLensScale(selectedTab);
                canvas.save();
                canvas.scale(s, s, lensRect.centerX(), lensRect.centerY());
                puck.draw(canvas, lensRect, half, appear, dark);
                canvas.restore();
            }

            // Where the pinned topics end on the scale, and who is typing in each topic, for its tab's
            // badge. The tabs keep Telegram's own counters for news: the scale's lights are the
            // wheel's alone.
            private void collectScaleMarks() {
                pinX = -1;
                for (int i = 0; i < getChildCount(); ++i) {
                    final View child = getChildAt(i);
                    if (!(child instanceof HorizontalTabView)) continue;
                    final HorizontalTabView tab = (HorizontalTabView) child;
                    if (tab.addW > 0) {
                        pinX = onDrum(tab, tab.getX() + tab.getWidth() - dp(9));
                    }
                    final int pos = getChildAdapterPosition(child);
                    final int index = pos == RecyclerView.NO_POSITION ? -1 : getWheelIndex(adapter.getItem(pos));
                    // The topic you are in says so in the header instead.
                    tab.setTyping(index >= 0 && rollWheel.isTypingAt(index) && tab.getTopicId() != currentTopicId);
                }
            }

            // The wheel's scale, unrolled under the tabs: a knurled tick every SCALE_TICK, moving with
            // the tabs, only while the bar winds up or rolls out (see getGearAlpha), and a taller tick
            // and the pin mark where the pinned topics end, where the wheel clicks a little firmer.
            private void drawScale(Canvas canvas) {
                final float gear = getGearAlpha();
                knurlPaint.setColor(TopicsRollWheel.getKnurlColor(resourcesProvider, rollWheel.isDark()));
                knurlPaint.setAlpha((int) (knurlPaint.getAlpha() * gear));
                final float tick = dp(SCALE_TICK), bottom = getHeight() - dp(TopicsRollWheel.KNURL_FROM_EDGE);
                final float phase = ((scrolled % tick) + tick) % tick;
                for (float x = tick / 2f - phase; gear > 0 && x < getWidth() + tick; x += tick) {
                    if (pinX >= 0 && Math.abs(x - pinX) < dp(6)) continue;
                    canvas.drawLine(x, bottom - dp(TopicsRollWheel.KNURL), x, bottom, knurlPaint);
                }
                if (pinX >= 0) {
                    if (gear > 0) {
                        canvas.drawLine(pinX, bottom - dp(TopicsRollWheel.KNURL + 2.5f), pinX, bottom, knurlPaint);
                    }
                    if (pinIcon == null) {
                        pinIcon = getContext().getResources().getDrawable(R.drawable.msg_limit_pin).mutate();
                    }
                    final int pinColor = Theme.getColor(Theme.key_chats_pinnedIcon, resourcesProvider);
                    if (pinIconColor != pinColor) {
                        pinIcon.setColorFilter(new PorterDuffColorFilter(pinIconColor = pinColor, PorterDuff.Mode.SRC_IN));
                    }
                    final int size = dp(10), top = (getHeight() - size) / 2 - dp(1);
                    pinIcon.setAlpha(0xCC);
                    pinIcon.setBounds((int) (pinX - size / 2f), top, (int) (pinX - size / 2f) + size, top + size);
                    pinIcon.draw(canvas);
                }
            }

            @Override
            public Integer getSelectorColor(int position) {
                return 0;
            }
        };
        topTabs.listenReorder(this::whenReordered);
        topTabs.setWillNotDraw(false);
        topTabs.adapter.setApplyBackground(false);
        topTabs.makeHorizontal();
        // A tab opens its topic as the finger lifts, not a moment after.
        topTabs.setInstantClick(true);
        topTabsContainer.addView(topTabs, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL, /*bot ? (72):*/ BAR_KEY, 0, ROLL_BUTTON_TOP_WIDTH, 0));
        topTabs.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (isLoadingVisible()) {
                    loadMore();
                }
            }
        });


        if (bot) {
            botCreateTopicButtonHorizontal = null; // new HorizontalTabView(context, currentAccount, resourcesProvider);
            //botCreateTopicButtonHorizontal.setAll(true, false, currentTopicId == 0);
            //botCreateTopicButtonHorizontal.setOnClickListener(v -> {
            //    onTopicSelected.run(0, false);
            //});
            //topTabsContainer.addView(botCreateTopicButtonHorizontal, LayoutHelper.createFrame(36, TOP_TABS_HEIGHT, Gravity.LEFT | Gravity.TOP, 36, 0, 0, 0));


            botCreateTopicButtonVertical = new VerticalTabView(context, currentAccount, resourcesProvider);
            ScaleStateListAnimator.apply(botCreateTopicButtonVertical.layout);
            botCreateTopicButtonVertical.setAll(true, false, currentTopicId == 0);
            botCreateTopicButtonVertical.setOnClickListener(v -> {
                onTopicSelected.run(0, false);
            });
            sideTabsContainer.addView(botCreateTopicButtonVertical, LayoutHelper.createFrame(64, 42, Gravity.LEFT | Gravity.TOP, 0, 48, 0, 0));
        } else {
            botCreateTopicButtonHorizontal = null;
            botCreateTopicButtonVertical = null;
        }

        sideTabs = new UniversalRecyclerView(context, currentAccount, 0, this::fillVerticalTabs, this::onTabClick, this::onTabLongClick, resourcesProvider) {
            private final GradientClip clip = new GradientClip();
            private final AnimatedFloat animatedClip = new AnimatedFloat(this, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
            private final AnimatedFloat animatedClipBottom = new AnimatedFloat(this, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
            private final LensGlide glide = sideGlide = new LensGlide(this);
            private final PressDrop drop = new PressDrop(this);
            private long lensId = Long.MIN_VALUE, lensFromId = Long.MIN_VALUE;
            private final RectF lensRect = new RectF(), dropRect = new RectF();
            // Where the lens was drawn last, from its tab's top, and where its glide sets off from.
            private float lensTabY = Float.NaN, fromTop = Float.NaN, fromBottom = Float.NaN;
            private final TopicsRollWheel.Puck puck = new TopicsRollWheel.Puck();

            @Override
            public boolean dispatchTouchEvent(MotionEvent e) {
                drop.onTouch(e);
                return super.dispatchTouchEvent(e);
            }

            @Override
            public Integer getSelectorColor(int position) {
                return 0;
            }

            @Override
            public boolean drawChild(Canvas canvas, View child, long drawingTime) {
                final float s = drop.getScale(child);
                if (s < 1f) {
                    canvas.save();
                    canvas.scale(s, s, child.getX() + child.getWidth() / 2f, child.getY() + child.getHeight() / 2f);
                    final boolean result = super.drawChild(canvas, child, drawingTime);
                    canvas.restore();
                    return result;
                }
                return super.drawChild(canvas, child, drawingTime);
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                final float clipAlpha = animatedClip.set(canScrollVertically(-1));
                // The roll button sits right below the list, so tabs fade out before reaching it.
                final float clipAlphaBottom = animatedClipBottom.set(canScrollVertically(1));
                final boolean needClip = clipAlpha > 0 || clipAlphaBottom > 0;
                if (needClip) {
                    canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), 0xFF, Canvas.ALL_SAVE_FLAG);
                }
                markTyping();
                drawLens(canvas);
                // The rail's gear line is the track's, under the tabs (see drawSideWell).
                super.dispatchDraw(canvas);
                if (drop.isMoving()) {
                    invalidate();
                }
                if (needClip) {
                    canvas.save();
                    if (clipAlpha > 0) {
                        AndroidUtilities.rectTmp.set(0, 0, getWidth(), dp(12));
                        clip.draw(canvas, AndroidUtilities.rectTmp, GradientClip.TOP, clipAlpha);
                    }
                    if (clipAlphaBottom > 0) {
                        AndroidUtilities.rectTmp.set(0, getHeight() - dp(12), getWidth(), getHeight());
                        clip.draw(canvas, AndroidUtilities.rectTmp, GradientClip.BOTTOM, clipAlphaBottom);
                    }
                    canvas.restore();
                    canvas.restore();
                }
            }

            // The lens is the bar's puck of glass, around the whole topic you are in, its icon and its
            // name. It glides down or up to a topic you pick, taking its height: its leading edge
            // goes first, so on its way it stretches along the rail, a little narrower, as if it kept
            // its volume (see LensGlide). Under it, a drop of glass answers a finger (see PressDrop).
            private void drawLens(Canvas canvas) {
                if (lensId != currentTopicId) {
                    lensFromId = lensId;
                    lensId = currentTopicId;
                    // Picked again before it landed: the lens sets off from where it is, not from where it was going.
                    final boolean moving = glide.isRunning() && !Float.isNaN(lensTabY);
                    fromTop = moving ? lensRect.top - lensTabY : Float.NaN;
                    fromBottom = moving ? lensRect.bottom - lensTabY : Float.NaN;
                    glide.start();
                }
                VerticalTabView selectedTab = null, fromTab = null;
                for (int i = 0; i < getChildCount(); ++i) {
                    final View child = getChildAt(i);
                    if (!(child instanceof VerticalTabView)) continue;
                    final VerticalTabView tab = (VerticalTabView) child;
                    if (tab.lensId == currentTopicId) selectedTab = tab;
                    if (tab.lensId == lensFromId) fromTab = tab;
                }
                glide.step();
                final float lead = glide.lead(), trail = glide.trail();
                final boolean dark = rollWheel.isDark();
                drop.markLens(selectedTab);
                final View pressed = drop.getTab();
                if (pressed instanceof VerticalTabView) {
                    final float cx = pressed.getX() + ((VerticalTabView) pressed).getIconCenterX();
                    dropRect.set(cx - dp(27), pressed.getY() + dp(2), cx + dp(27), pressed.getY() + pressed.getHeight() - dp(2));
                    drop.draw(canvas, dropRect, dp(14), selectedTab, glide.isRunning(), lead, dark);
                }
                if (selectedTab == null) {
                    lensTabY = Float.NaN;
                    glide.setProgress(0, 0);
                    return;
                }
                float t = selectedTab.getY() + dp(2), b = selectedTab.getY() + selectedTab.getHeight() - dp(2), appear = 1f, narrow = 1f;
                glide.setProgress(0, 0);
                if (glide.isRunning()) {
                    if (fromTab != null) {
                        final float ft = fromTab.getY() + (Float.isNaN(fromTop) ? dp(2) : fromTop);
                        final float fb = fromTab.getY() + (Float.isNaN(fromBottom) ? fromTab.getHeight() - dp(2) : fromBottom);
                        final boolean down = t > ft;
                        final float top = lerp(ft, t, down ? trail : lead), bottom = lerp(fb, b, down ? lead : trail);
                        final float rest = lerp(fb - ft, b - t, (lead + trail) / 2f);
                        narrow = 1f - 0.1f * Utilities.clamp((bottom - top - rest) / rest, 1f, 0f);
                        glide.setProgress(Math.max(Math.abs(top - t), Math.abs(bottom - b)), Math.max(Math.abs(ft - t), Math.abs(fb - b)));
                        t = top;
                        b = bottom;
                    } else {
                        appear = lead;
                    }
                }
                final float cx = selectedTab.getX() + selectedTab.getIconCenterX(), half = dp(27) * narrow;
                lensRect.set(cx - half, t, cx + half, b);
                lensTabY = selectedTab.getY();
                canvas.save();
                final float s = lerp(0.6f, 1f, appear) * drop.getLensScale(selectedTab);
                canvas.scale(s, s, lensRect.centerX(), lensRect.centerY());
                puck.draw(canvas, lensRect, dp(14), appear, dark);
                canvas.restore();
            }

            // Who is typing in each topic, for its tab's badge. The tabs keep Telegram's own counters
            // for news: the scale's lights are the wheel's alone.
            private void markTyping() {
                for (int i = 0; i < getChildCount(); ++i) {
                    final View child = getChildAt(i);
                    if (!(child instanceof VerticalTabView)) continue;
                    final int pos = getChildAdapterPosition(child);
                    final int index = pos == RecyclerView.NO_POSITION ? -1 : getWheelIndex(adapter.getItem(pos));
                    // The topic you are in says so in the header instead.
                    ((VerticalTabView) child).setTyping(index >= 0 && rollWheel.isTypingAt(index) && ((VerticalTabView) child).lensId != currentTopicId);
                }
            }
        };
        sideTabs.listenReorder(this::whenReordered);
        sideTabs.adapter.setApplyBackground(false);
        sideTabs.setClipToPadding(false);
        sideTabs.setClipChildren(false);
        sideTabs.setInstantClick(true);
        sideTabsContainer.addView(sideTabs, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL, 0, bot ? (48 + 42) : 48, 0, ROLL_BUTTON_SIDE_HEIGHT));
        sideTabs.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (isLoadingVisible()) {
                    loadMore();
                }
            }
        });

        toggleButtonTop = createButton(context, R.drawable.menu_sidebar_left, this::onSideMenuButtonClick);
        toggleButtonSide = createButton(context, R.drawable.menu_sidebar_left, this::onSideMenuButtonClick);
        topTabsContainer.addView(toggleButtonTop, LayoutHelper.createFrame(BAR_KEY, TOP_TABS_HEIGHT, Gravity.LEFT | Gravity.TOP));
        sideTabsContainer.addView(toggleButtonSide, LayoutHelper.createFrame(64, 48, Gravity.LEFT | Gravity.TOP));

        closeButtonTop = createButton(context, R.drawable.msg_select, this::onCloseButtonClick);
        closeButtonSide = createButton(context, R.drawable.msg_select, this::onCloseButtonClick);
        topTabsContainer.addView(closeButtonTop, LayoutHelper.createFrame(BAR_KEY, TOP_TABS_HEIGHT, Gravity.LEFT | Gravity.TOP));
        sideTabsContainer.addView(closeButtonSide, LayoutHelper.createFrame(64, 48, Gravity.LEFT | Gravity.TOP));

        rollButtonTop = new RollButton(context, false, resourcesProvider);
        rollButtonTop.setOnClickListener(this::onRollButtonClick);
        rollButtonTop.onKeepProgress = topTabs::invalidate;
        topTabsContainer.addView(rollButtonTop, LayoutHelper.createFrame(ROLL_BUTTON_TOP_KEEP_WIDTH, TOP_TABS_HEIGHT, Gravity.RIGHT | Gravity.TOP));
        rollButtonSide = new RollButton(context, true, resourcesProvider);
        rollButtonSide.setOnClickListener(this::onRollButtonClick);
        // As Keep open turns back into «, the chat moves from stepping aside into the rail's column.
        rollButtonSide.onKeepProgress = () -> {
            if (onRollChanged != null) {
                onRollChanged.run();
            }
        };
        sideTabsContainer.addView(rollButtonSide, LayoutHelper.createFrame(64, ROLL_BUTTON_SIDE_HEIGHT, Gravity.LEFT | Gravity.BOTTOM));

        rollWheel = new TopicsRollWheel(context, currentAccount, dialogId, mono, resourcesProvider);
        rollWheel.setDelegate(new TopicsRollWheel.Delegate() {
            @Override
            public void onWheelTap() {
                setRolledOut(true, true);
            }

            @Override
            public void onWheelTurned(long id, int direction) {
                pendingSwitchDirection = direction;
                selectTopic(id, false);
                if (currentTopicId != id) {
                    // The chat could not open it (its first message is not loaded yet): turn back.
                    pendingSwitchDirection = 0;
                    rollWheel.turnTo(currentTopicId);
                }
            }

            @Override
            public void onWheelLongPress(long id) {
                final TLRPC.TL_forumTopic topic = findTopic(id);
                if (topic != null) {
                    onTabLongClick(HorizontalTabView.Factory.asTab(dialogId, topic, mono), rollWheel, 0, 0, 0);
                }
            }

            @Override
            public void onWheelScrub(int index) {
                setRollDisplay(index);
                if (index >= rollWheel.getCount() - 3) {
                    loadMore();
                }
            }
        });
        ScaleStateListAnimator.apply(rollWheel, 0.05f, 1.6f);
        addView(rollWheel, LayoutHelper.createFrame(TopicsRollWheel.WIDTH + TopicsRollWheel.INSET * 2, TopicsRollWheel.HEIGHT + TopicsRollWheel.INSET * 2 + TopicsRollWheel.TOP_SPACE, Gravity.LEFT | Gravity.TOP, 7 - TopicsRollWheel.INSET, 7 - TopicsRollWheel.INSET, 0, 0));


        MessagesController.getInstance(currentAccount).getTopicsController().loadTopics(-dialogId, false, TopicsController.LOAD_TYPE_HASH_CHECK);

        SharedPreferences sp = MessagesController.getInstance(currentAccount).getMainSettings();
        if (sp.getBoolean("topicssidetabs" + dialogId, false)) {
            sidemenuT = 1.0f;
            sidemenuEnabled = true;
        }
        topicBottom = sp.getBoolean("topicssidetabsb" + dialogId, false);
        toggleButtonSide.setImageResource(topicBottom ? R.drawable.menu_sidebar_top : R.drawable.menu_sidebar_bottom);

        // A chat remembers its own choice; chats you never chose for follow your latest choice anywhere.
        rollable = sp.getBoolean("topicsroll" + dialogId, sp.getBoolean("topicsroll_default", false));
        if (rollable) {
            rolledOut = false;
            rollT = 0f;
            animatorRollReserve.setValue(false, false);
        }
        rollButtonTop.setKeep(rollable && rolledOut, false);
        rollButtonSide.setKeep(rollable && rolledOut, false);

        checkTopicsVisibility(false);
        checkUi_closeButtonVisibility();
        updateSidemenuPosition();
        updateTabs();
        updateWheel(false);
    }

    /* Roll */

    // Kept open: winds the bar up into the wheel. Rolled out over the chat: keeps it open again.
    private void onRollButtonClick(View v) {
        setRollable(!rollable);
    }

    private void setRollable(boolean rollable) {
        if (this.rollable == rollable) return;
        this.rollable = rollable;
        AndroidUtilities.cancelRunOnUIThread(rollUpAfterSelect);
        hideRollHint();
        try {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        } catch (Exception ignore) {}

        MessagesController.getInstance(currentAccount).getMainSettings().edit()
            .putBoolean("topicsroll" + dialogId, rollable)
            .putBoolean("topicsroll_default", rollable)
            .apply();

        // Winding a kept bar up, « just fades with it; Keep open, tapped, turns back into «. Only a
        // bar rolled out from the wheel reads Keep open (see setRolledOut).
        rollButtonTop.setKeep(false, true);
        rollButtonSide.setKeep(false, true);
        if (rollable) {
            rolledOut = false;
            animateRollTo(0f);
            maybeShowRollHint();
        } else {
            rolledOut = true;
            animateRollTo(1f);
        }
        animatorRollReserve.setValue(!rollable, true);
    }

    /** Rolls the bar out over the chat (or winds it back into the wheel). Only means something while rollable. */
    private void setRolledOut(boolean out, boolean animated) {
        AndroidUtilities.cancelRunOnUIThread(rollUpAfterSelect);
        rollUpOnOpen = false;
        if (!rollable) {
            out = true;
        }
        if (rolledOut == out && (rollAnimator != null || rollT == (out ? 1f : 0f))) {
            return;
        }
        rolledOut = out;
        if (out && rollable) {
            // Its far end reads Keep open. The wheel only rolls the bar out from all the way up, where
            // that end is out of sight, so it can change at once.
            rollButtonTop.setKeep(true, false);
            rollButtonSide.setKeep(true, false);
        }
        if (out) {
            hideRollHint();
            AndroidUtilities.cancelRunOnUIThread(hideRollDisplay);
            animatorRollDisplay.setValue(false, true);
            scrollTabsToCurrent();
            try {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
            } catch (Exception ignore) {}
        }
        if (animated) {
            animateRollTo(out ? 1f : 0f);
        } else {
            cancelRollAnimator();
            setRollT(out ? 1f : 0f);
        }
    }

    // The bar comes back scrolled to the topic you are in, its icon where the wheel's lens was, so
    // the wheel lays it down from there.
    private void scrollTabsToCurrent() {
        if (topTabs.layoutManager == null) return;
        for (int i = 0; i < topTabs.adapter.getItemCount(); ++i) {
            final UItem item = topTabs.adapter.getItem(i);
            if (item != null && !item.red && item.id != -2 && (mono ? item.longValue : item.id) == currentTopicId) {
                topTabs.layoutManager.scrollToPositionWithOffset(i, 0);
                return;
            }
        }
    }

    private void animateRollTo(float to) {
        cancelRollAnimator();
        if (rollT == to) {
            setRollT(to);
            return;
        }
        final float from = rollT;
        rollAnimator = ValueAnimator.ofFloat(from, to);
        rollAnimator.addUpdateListener(a -> setRollT((float) a.getAnimatedValue()));
        rollAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (rollAnimator == animation) {
                    rollAnimator = null;
                    setRollT(to);
                    if (to >= 1f && isLoadingVisible()) {
                        loadMore();
                    }
                }
            }
        });
        final float distance = Math.abs(to - from);
        if (to > from) {
            // Rolling out: the wheel is pushed across the row and slows down as it lays the last tabs.
            rollAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            rollAnimator.setDuration((long) Math.max(200, 540 * distance));
        } else {
            // Winding up: the wheel gathers speed, then settles into its place.
            rollAnimator.setInterpolator(EASE_IN_OUT_CUBIC);
            rollAnimator.setDuration((long) Math.max(200, 480 * distance));
        }
        rollAnimator.start();
    }

    private void cancelRollAnimator() {
        if (rollAnimator != null) {
            final ValueAnimator animator = rollAnimator;
            rollAnimator = null;
            animator.cancel();
        }
    }

    private void setRollT(float t) {
        rollT = Utilities.clamp(t, 1f, 0f);
        checkUi_roll();
        if (onRollChanged != null) {
            onRollChanged.run();
        }
    }

    private float getReserveT() {
        return animatorRollReserve.getFloatValue();
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        final float t = Utilities.clamp((x - edge0) / (edge1 - edge0), 1f, 0f);
        return t * t * (3f - 2f * t);
    }

    /**
     * Someone typing in a topic, on its tab: a small badge in the colour of the tab's counter (grey
     * when the topic is muted) with three dots that rise and fall one after another, as on the wheel.
     * It sits at the icon's lower corner, clear of the counter, and grows in from its middle as it
     * comes (alpha). cx, cy: its middle; h: its height. The caller keeps redrawing while it shows.
     */
    static void drawTypingBadge(Canvas canvas, Paint paint, RectF rect, float cx, float cy, float h, float alpha, boolean muted, Theme.ResourcesProvider resourcesProvider) {
        if (alpha <= 0f) return;
        final float w = h * 1.8f, s = lerp(0.4f, 1f, alpha);
        canvas.save();
        canvas.scale(s, s, cx, cy);
        rect.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f);
        paint.setColor(Theme.multAlpha(Theme.getColor(muted ? Theme.key_chats_unreadCounterMuted : Theme.key_chats_unreadCounter, resourcesProvider), alpha));
        canvas.drawRoundRect(rect, h / 2f, h / 2f, paint);
        final int dot = Theme.getColor(Theme.key_chats_unreadCounterText, resourcesProvider);
        final float t = SystemClock.uptimeMillis() / 1000f;
        for (int k = 0; k < 3; ++k) {
            final float phase = ((t * 1.6f - k * 0.2f) % 1f + 1f) % 1f;
            final float rise = (float) Math.sin(Math.PI * phase);
            paint.setColor(Theme.multAlpha(dot, alpha * (0.5f + 0.5f * rise * rise)));
            canvas.drawCircle(cx + (k - 1) * h * 0.36f, cy - h * 0.1f * rise, h * 0.12f, paint);
        }
        canvas.restore();
    }

    /** Clears the ring the icon gives way with around a typing badge, inside the tab's own layer. */
    static void clearTypingBadge(Canvas canvas, Paint clearPaint, RectF rect, float cx, float cy, float h, float ring, float alpha) {
        if (alpha <= 0f) return;
        final float w = h * 1.8f;
        rect.set(cx - w / 2f - ring, cy - h / 2f - ring, cx + w / 2f + ring, cy + h / 2f + ring);
        AndroidUtilities.scaleRect(rect, lerp(0.4f, 1f, alpha), cx, cy);
        canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, clearPaint);
    }

    /** How far a critically damped spring has come at x = its rate times the time: no jump at the start, no overshoot. */
    private static float spring(float x) {
        return x <= 0f ? 0f : 1f - (1f + x) * (float) Math.exp(-x);
    }

    /**
     * The lens's glide from the topic you were in to the one you picked. Its leading edge sets off
     * first and its trailing edge follows, each on a critically damped spring: the glass leaves
     * without a jump, stretches a little on its way and gathers itself as it lands. It keeps its own
     * clock, a frame at a time, so a frame held up anyway never makes it leap ahead to catch up. The
     * chat opens the topic only once the glass has all but landed (see isMoving), so nothing heavy
     * runs while it is on its way.
     */
    private static class LensGlide {
        // Each edge's spring, in radians a second: the leading edge is nine tenths of the way there
        // in 0.115 s, the trailing one in 0.145 s.
        private static final float LEAD = 34f, TRAIL = 27f;
        private static final float DURATION = 0.36f, LONGEST_FRAME = 0.024f;
        private final View view;
        private float time = DURATION;
        private long frameTime, expectTime;
        private float remaining, travel;

        LensGlide(View view) {
            this.view = view;
        }

        void start() {
            time = 0f;
            frameTime = 0;
            expectTime = 0;
        }

        /** The topic has just changed: the glass sets off on its next frame, and counts as moving until then. */
        void expect() {
            expectTime = SystemClock.uptimeMillis();
        }

        /** Moves the glide on by this frame, by no more than a frame and a half. */
        void step() {
            if (time >= DURATION) return;
            final long now = SystemClock.uptimeMillis();
            if (frameTime != 0) {
                time = Math.min(DURATION, time + Math.min(LONGEST_FRAME, (now - frameTime) / 1000f));
            }
            frameTime = now;
            view.invalidate();
        }

        boolean isRunning() {
            return time < DURATION;
        }

        float lead() {
            return isRunning() ? spring(LEAD * time) : 1f;
        }

        float trail() {
            return isRunning() ? spring(TRAIL * time) : 1f;
        }

        /** Where the glass is on its way, in pixels: how far its slower edge still has to go, out of how far it goes. */
        void setProgress(float remaining, float travel) {
            this.remaining = remaining;
            this.travel = travel;
        }

        /**
         * Whether the glass is still on its way, on a list that is being drawn: more than 3 dp (or 1.5%
         * of the way, on a long glide) from where it lands. Past that it covers 3 or 4 px a frame, and
         * a frame held up there does not show.
         */
        boolean isMoving() {
            final long now = SystemClock.uptimeMillis();
            if (expectTime != 0 && now - expectTime < 64) return true;
            return isRunning() && now - frameTime < 100 && remaining > Math.max(dp(3), travel * 0.015f);
        }
    }

    /**
     * A drop of glass under your finger. Press a tab and it blooms from where you touch into the
     * lens's shape there, fainter than the lens, while the tab sinks into it a little; press the
     * topic you are in and the lens itself gives. Pick the topic and the lens glides over and takes
     * the drop's place; let go anywhere else, or scroll, and the drop melts away. It stands in for the
     * list's square highlight.
     */
    private static class PressDrop {
        // The drop's bloom and the tab's way back up, in radians a second; how far the tab sinks;
        // how long the drop takes to melt, in seconds.
        private static final float BLOOM = 30f, SETTLE = 20f, SINK = 0.07f, MELT = 0.2f;
        private final RecyclerView list;
        private final TopicsRollWheel.Puck glass = new TopicsRollWheel.Puck();
        private final RectF rect = new RectF();
        private final int touchSlop;
        private View tab;
        private boolean fresh, onLens;
        private float downX, downY, sinkAtUp;
        private long downTime, upTime;

        PressDrop(RecyclerView list) {
            this.list = list;
            touchSlop = ViewConfiguration.get(list.getContext()).getScaledTouchSlop();
        }

        /** Follows the list's touches: a finger on a tab brings the drop, and lifting it, or scrolling, lets it go. */
        void onTouch(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    tab = list.getScrollState() == RecyclerView.SCROLL_STATE_IDLE ? list.findChildViewUnder(e.getX(), e.getY()) : null;
                    fresh = true;
                    downX = e.getX();
                    downY = e.getY();
                    downTime = SystemClock.uptimeMillis();
                    upTime = 0;
                    list.invalidate();
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (Math.abs(e.getX() - downX) > touchSlop || Math.abs(e.getY() - downY) > touchSlop) {
                        release();
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    release();
                    break;
            }
        }

        private void release() {
            if (tab != null && upTime == 0) {
                sinkAtUp = getBloom();
                upTime = SystemClock.uptimeMillis();
                list.invalidate();
            }
        }

        /** The tab under the finger, or the one the drop is leaving; null once it has gone. */
        View getTab() {
            if (tab != null && (tab.getParent() != list || upTime != 0 && SystemClock.uptimeMillis() - upTime > 700)) {
                tab = null;
            }
            return tab;
        }

        /**
         * Called on every frame with the tab the lens is on (or gliding to): the first frame of a
         * press tells whether it is the topic you are in, where the lens gives instead of a drop.
         */
        void markLens(View lensTab) {
            if (fresh) {
                fresh = false;
                onLens = tab != null && tab == lensTab;
            }
        }

        private float getBloom() {
            return spring(BLOOM * (SystemClock.uptimeMillis() - downTime) / 1000f);
        }

        /** How far the tab has sunk: all the way as the drop forms, and back up once you let go. */
        float getSink() {
            if (getTab() == null) return 0f;
            if (upTime == 0) return getBloom();
            final float x = SETTLE * (SystemClock.uptimeMillis() - upTime) / 1000f;
            return sinkAtUp * (1f + x) * (float) Math.exp(-x);
        }

        /** How a tab is scaled as it sinks, about its middle. */
        float getScale(View child) {
            return child != null && child == getTab() ? 1f - SINK * getSink() : 1f;
        }

        /** Whether the tab is still sinking or coming back up (the drop itself asks for its own frames). */
        boolean isMoving() {
            if (getTab() == null) return false;
            return upTime == 0 ? getBloom() < 0.995f : getSink() > 0.002f;
        }

        /** How much the lens gives under the finger, on the topic you are in. */
        float getLensScale(View lensTab) {
            return onLens && lensTab != null && lensTab == getTab() ? 1f - SINK * 0.6f * getSink() : 1f;
        }

        /**
         * Draws the drop into target, the lens's shape on the pressed tab, behind the tabs. While the
         * lens glides to this tab the drop gives way to it (arrival: how far the lens has come).
         */
        void draw(Canvas canvas, RectF target, float radius, View lensTab, boolean gliding, float arrival, boolean dark) {
            final View tab = getTab();
            if (tab == null || onLens || fresh) return;
            final long now = SystemClock.uptimeMillis();
            final float bloom = getBloom();
            float alpha = (dark ? 0.8f : 0.7f) * smoothstep(0f, 0.5f, bloom), melt = 0f;
            if (tab == lensTab) {
                alpha *= gliding ? 1f - smoothstep(0.2f, 0.85f, arrival) : 0f;
            } else if (upTime != 0) {
                melt = smoothstep(0f, MELT, (now - upTime) / 1000f);
                alpha *= 1f - melt;
            }
            if (alpha <= 0f) {
                if (upTime != 0) {
                    this.tab = null;
                }
                return;
            }
            // From a small round drop under the finger to the lens's shape, and a little smaller as it melts.
            final float r0 = Math.min(dp(10), Math.min(target.width(), target.height()) / 2f);
            final float cx = Utilities.clamp(downX, target.right - r0, target.left + r0);
            final float cy = Utilities.clamp(downY, target.bottom - r0, target.top + r0);
            rect.set(
                lerp(cx - r0, target.left, bloom),
                lerp(cy - r0, target.top, bloom),
                lerp(cx + r0, target.right, bloom),
                lerp(cy + r0, target.bottom, bloom)
            );
            AndroidUtilities.scaleRect(rect, 1f - 0.06f * melt);
            glass.draw(canvas, rect, lerp(r0, radius, bloom) * (1f - 0.06f * melt), alpha, dark);
            list.invalidate();
        }
    }

    private void checkUi_roll() {
        final float visibility = animatorTopicsVisibility.getFloatValue();
        final boolean blind = sidemenuT > 0.5f;
        // The side tabs fade in as the rail drops. The top tabs are laid down by the wheel: they are
        // only cut off where the wheel is.
        final float sideContent = Utilities.clamp((rollT - 0.18f) / 0.55f, 1f, 0f);
        final float topContent = rollT > 0.005f ? 1f : 0f;

        topTabsContainer.setAlpha(lerp(1.0f, 0f, sidemenuT) * topContent);
        topTabsContainer.setVisibility(((1f - sidemenuT) * visibility) > 0 && topContent > 0 ? View.VISIBLE : View.GONE);
        sideTabsContainer.setAlpha(sideContent);
        sideTabsContainer.setVisibility(getTabsVisibility(Position.LEFT) > 0 && sideContent > 0 ? VISIBLE : GONE);
        sideTabs.setTranslationY(-dp(28) * (1f - CubicBezierInterpolator.EASE_OUT.getInterpolation(rollT)));
        topTabs.setTranslationX(0);

        // The wheel: in its well at the start of the island, or at the moving end of the bar while it
        // rolls out or winds up, turned to the tab it is passing, until it fades at the far end. At the
        // top it rises out of its well onto the bar's line as it goes; at the bottom it is on it
        // already. For side tabs it stands up at the top of the rail, which drops down out of it.
        final float wheelAlpha = (blind ? 1f - Utilities.clamp(rollT / 0.3f, 1f, 0f) : 1f - smoothstep(0.8f, 0.97f, rollT))
            * visibility * (rollable || rollT < 1f ? 1f : 0f);
        rollWheel.setVertical(blind);
        rollWheel.setAlpha(wheelAlpha);
        rollWheel.setVisibility(wheelAlpha > 0 ? VISIBLE : GONE);
        rollWheel.setEnabled(rollable && !rolledOut && rollT < 0.05f);
        // The wheel rolls on and leaves its bead on your topic: the bar's puck takes over from it.
        rollWheel.setLensAlpha(blind ? 1f : 1f - smoothstep(0.02f, 0.3f, rollT));
        // « or Keep open shows at the bar's far end as the wheel fades there.
        rollButtonTop.setAlpha(blind ? 1f : smoothstep(0.92f, 1f, rollT));
        final float islandTop = getIslandTop();
        float wheelLeft, wheelY;
        if (blind) {
            wheelLeft = dp(7) + lerp(dp(V_ISLAND_W), dp(SIDE_TABS_WIDTH), rollT) / 2f - dp(TopicsRollWheel.HEIGHT) / 2f;
            wheelY = islandTop + dp(ISLAND_RIM);
        } else if (topicBottom) {
            wheelLeft = lerp(dp(7), getMeasuredWidth() - dp(7 + TopicsRollWheel.WIDTH), rollT);
            wheelY = topTabsContainer.getTranslationY() + dp(7);
        } else {
            wheelLeft = lerp(dp(7 + ISLAND_RIM), getMeasuredWidth() - dp(7 + TopicsRollWheel.WIDTH), rollT);
            wheelY = lerp(islandTop + dp(ISLAND_RIM), islandTop, rollT);
        }
        wheelLeftPx = wheelLeft;
        rollWheel.setTranslationX(wheelLeft - dp(7));
        rollWheel.setTranslationY(wheelY - dp(7 + TopicsRollWheel.TOP_SPACE));
        if (!blind && rollT > 0f && rollT < 1f) {
            final float lens = wheelLeft + dp(TopicsRollWheel.WIDTH) / 2f;
            rollWheel.setBeltOverride(lerp(rollWheel.indexOf(currentTopicId), tabIndexAt(lens), smoothstep(0f, 0.3f, rollT)));
        } else {
            rollWheel.setBeltOverride(-1);
        }
        // The bar's lens opens out of the wheel's as it rolls, and the gear line comes and goes.
        topTabs.invalidate();
        sideTabs.invalidate();
        invalidate();
    }

    private float wheelLeftPx;
    private float[] tabCenters = new float[16], tabIndices = new float[16];

    /**
     * How much the gear line shows, the wheel's knurled ticks under the bar's tabs or down the rail:
     * not at all while the topics are out or kept open. Winding up it shows, subtly at first, until
     * it is the wheel's own pale ticks; rolling out it goes the other way.
     */
    private float getGearAlpha() {
        return (float) Math.pow(1f - rollT, 1.5f);
    }

    /**
     * Which tab (as a wheel index, fractional between two tabs) is at x, so the wheel can turn to what
     * it passes. It goes by the tabs' icons, so the icon under the wheel's lens is the one on the bar.
     */
    private float tabIndexAt(float x) {
        final int count = topTabs.getChildCount();
        if (tabCenters.length < count) {
            tabCenters = new float[count * 2];
            tabIndices = new float[count * 2];
        }
        final float base = topTabsContainer.getX() + topTabs.getX();
        int n = 0;
        for (int i = 0; i < count; ++i) {
            final View child = topTabs.getChildAt(i);
            final UItem item = topTabs.adapter.getItem(topTabs.getChildAdapterPosition(child));
            if (item == null || item.red || item.id == -2) continue;
            final float center = base + child.getX() + (child instanceof HorizontalTabView ? ((HorizontalTabView) child).getIconCenterX() : child.getWidth() / 2f);
            final float index = rollWheel.indexOf(mono ? item.longValue : item.id);
            int j = n++;
            while (j > 0 && tabCenters[j - 1] > center) {
                tabCenters[j] = tabCenters[j - 1];
                tabIndices[j] = tabIndices[j - 1];
                j--;
            }
            tabCenters[j] = center;
            tabIndices[j] = index;
        }
        if (n == 0) return rollWheel.indexOf(currentTopicId);
        final float cell = dp(34);
        if (x <= tabCenters[0]) return Math.max(-0.5f, tabIndices[0] - (tabCenters[0] - x) / cell);
        for (int i = 0; i < n - 1; ++i) {
            if (x < tabCenters[i + 1]) {
                return lerp(tabIndices[i], tabIndices[i + 1], (x - tabCenters[i]) / Math.max(1f, tabCenters[i + 1] - tabCenters[i]));
            }
        }
        return Math.min(rollWheel.getCount() - 0.5f, tabIndices[n - 1] + (x - tabCenters[n - 1]) / cell);
    }

    /** The top of the row the tabs use; while rolled up, the wheel's island shares it with the chat's top panels. */
    private float getBarBaseTop() {
        return sideMenuBackgroundMarginTop;
    }

    /** The top of the island, level with the chat's first top panel. */
    private float getIslandTop() {
        return getBarBaseTop() + dp(7);
    }

    /**
     * The chat tells how tall its top panels are and how many there are. A single 48 dp panel (the
     * pinned message, usually) joins the wheel's island; with more, the island keeps to itself.
     */
    public void setTopPanels(float height, int count, boolean single) {
        final boolean hadPanels = topPanelsCount > 0;
        final boolean changed = topPanelsHeight != height || topPanelsCount != count;
        topPanelsHeight = height;
        topPanelsCount = count;
        if (single) {
            // A panel that comes into an empty row is in the island from the start; the island
            // widens as the panel grows.
            if (!animatorIslandJoin.getValue()) {
                animatorIslandJoin.setValue(true, hadPanels && isAttachedToWindow());
            }
        } else if (count > 0) {
            if (animatorIslandJoin.getValue()) {
                animatorIslandJoin.setValue(false, isAttachedToWindow());
            }
        }
        // A panel that leaves an empty row stays in the island, which narrows as the panel goes.
        if (changed) {
            checkUi_roll();
        }
    }

    /** How much the island spans the row with the chat's top panel as its display: only in the top row. */
    private float getIslandJoin() {
        return animatorIslandJoin.getFloatValue() * Utilities.clamp(topPanelsHeight / dp(ISLAND_HEIGHT), 1f, 0f) * getTopRow();
    }

    /** 1 while the bar's row is at the top, where the island shares the chat's top panel row. */
    private float getTopRow() {
        return topicBottom ? 0f : 1f - sidemenuT;
    }

    /** How much the display shows, while the wheel turns and a little after. */
    private float getDisplayShown() {
        return animatorRollDisplay.getFloatValue() * (1f - smoothstep(0f, 0.3f, rollT)) * animatorTopicsVisibility.getFloatValue();
    }

    /** The island's width: just the wheel, stretched to show the display, or the whole row with a panel. */
    private float getIslandWidth() {
        final float full = getMeasuredWidth() - dp(14);
        final float alone = Math.min(full, dp(ISLAND_ALONE));
        final float stretched = Utilities.clamp(dp(DISPLAY_LEFT - 7 + 23 + 16) + animatedDisplayWidth.getValue(), full, alone);
        final float display = animatorRollDisplay.getFloatValue() * (1f - smoothstep(0f, 0.3f, rollT));
        return lerp(lerp(alone, stretched, display), full, getIslandJoin());
    }

    /** At the bottom the island is the wheel's track, stretched while the wheel turns to show the display. */
    private float getBottomIslandWidth() {
        final float full = getMeasuredWidth() - dp(14);
        final float alone = Math.min(full, dp(TopicsRollWheel.WIDTH));
        final float stretched = Utilities.clamp(dp(BOTTOM_DISPLAY_LEFT - 7 + 24 + 14) + animatedDisplayWidth.getValue(), full, alone);
        final float display = animatorRollDisplay.getFloatValue() * (1f - smoothstep(0f, 0.3f, rollT));
        return lerp(alone, stretched, display);
    }

    /** How much the chat's top panels sit in the island, giving their glass to it: only in the top row. */
    private float getDocked() {
        final float docked = (1f - getReserveT()) * (1f - smoothstep(0f, 0.6f, rollT));
        return docked * animatorTopicsVisibility.getFloatValue() * (rollable || rollT < 1f ? 1f : 0f) * getTopRow();
    }

    /**
     * How far the chat's top panels (the pinned message and others) step aside to the right for the
     * wheel: for its island in the top row, or for the upright island on the side (and for the rail
     * while it is out over the chat). A wheel at the bottom leaves them be.
     */
    public float getRollInsetLeft() {
        final float visibility = animatorTopicsVisibility.getFloatValue();
        final float rowOpen = Math.max(getReserveT(), smoothstep(0f, 0.6f, rollT));
        final float row = topicBottom ? 0 : lerp(dp(ISLAND_ALONE + 7), dp(DISPLAY_LEFT - 7), animatorIslandJoin.getFloatValue()) * (1f - rowOpen);
        final float side = lerp(dp(V_ISLAND_W + 7), dp(SIDE_TABS_WIDTH + 7), smoothstep(0f, 0.6f, rollT)) * (1f - getReserveT());
        return lerp(row, side, sidemenuT) * visibility;
    }

    /** The alpha of the chat's own glass behind its top panels: none while the panel is in the island. */
    public float getTopPanelsGlassAlpha() {
        return 1f - animatorIslandJoin.getFloatValue() * getDocked();
    }

    /**
     * How much the island's display covers the chat's top panels while the wheel turns: in the top
     * row and on the side, where the display takes the pinned message's place. At the bottom the
     * display is in the wheel's own row. The panels are gone by the time the display's words come
     * in, so the two never show over each other.
     */
    public float getTopPanelsCover() {
        return !topicBottom || sidemenuT > 0.5f ? (topPanelsCount > 0 || topPanelsHeight > 0 ? smoothstep(0f, 0.5f, getDisplayShown()) : 0f) : 0f;
    }

    /** The chat's header, whose Back and menu keys sit in wells like the topics'. */
    public void setHeaderKeys(ActionBar header) {
        this.header = header;
        if (header != null) {
            header.invalidate();
        }
    }

    /** Draws the well into one of the header's keys (its visible glass), under its icon. */
    public void drawKeyWell(Canvas canvas, RectF key, float alpha) {
        final float amount = animatorTopicsVisibility.getFloatValue() * alpha;
        if (amount <= 0f) return;
        keyWellRect.set(key);
        keyWellRect.inset(dp(ISLAND_RIM), dp(ISLAND_RIM));
        keyWell.draw(canvas, keyWellRect, amount, rollWheel.isDark());
    }

    /** How far the top panels move down for a bar in the top row: one kept open there, or one rolled out over the chat. */
    public float getTopPanelsShift(float padding) {
        final float temporary = smoothstep(0f, 0.6f, rollT) * (1f - getReserveT());
        final float open = topicBottom ? temporary : Math.max(getReserveT(), smoothstep(0f, 0.6f, rollT));
        return topicBottom ? 0 : (dp(TOP_TABS_HEIGHT) + padding) * open * (1f - sidemenuT) * animatorTopicsVisibility.getFloatValue();
    }

    /**
     * How far the chat steps aside for side tabs rolled out over it, 0 to 1: back and to the right,
     * so no message hides under the rail, in one motion with the rail. Only for a rail rolled out from
     * the wheel (the one that reads Keep open at its foot); kept open, it has its own column.
     */
    public float getChatAside() {
        if (rollButtonSide == null) return 0f;
        return sidemenuT * rollT * rollButtonSide.getKeepProgress() * animatorTopicsVisibility.getFloatValue();
    }

    /** How far a rail rolled out over the chat is out, for the chat's avatars to tuck in under it. */
    public float getRailOverChat() {
        if (rollButtonSide == null) return 0f;
        return sidemenuT * rollButtonSide.getKeepProgress() * animatorTopicsVisibility.getFloatValue() * smoothstep(0f, 0.15f, rollT);
    }

    /**
     * How far the chat's avatar at height y (in this view's coordinates) tucks into its bubble under a
     * rail rolled out over the chat, 0 to 1. The rail's lower edge brushes past the avatars as it drops
     * out of the wheel: each one tucks in as the edge reaches it, the way Telegram tucks avatars in
     * when side tabs open, and comes back out as the rail winds up past it. So no avatar ever peeks
     * out from under the rail, and the bubbles stepping aside have the room to themselves.
     */
    public float getAvatarTuck(float y) {
        final float out = getRailOverChat();
        if (out <= 0f || rollT >= 1f) return out;
        final float islandBottom = getBarBaseTop() + dp(V_ISLAND_H + 14);
        final float edge = lerp(islandBottom, getMeasuredHeight() - sideMenuBackgroundMarginBottom, rollT) - dp(7);
        return out * smoothstep(-dp(24), dp(24), edge - y);
    }

    /** Called on every step of rolling, so the chat can move its top panels along. */
    public void doOnRollChanged(Runnable runnable) {
        onRollChanged = runnable;
    }

    /** +1 or -1 for the side the topic picked last is on, from the wheel or the tabs, for the chat to slide that way; 0 otherwise. */
    public int consumeSwitchDirection() {
        final int direction = pendingSwitchDirection;
        pendingSwitchDirection = 0;
        return direction;
    }

    public boolean isRolledOutOverChat() {
        return rollable && rolledOut;
    }

    /** Back rolls a bar that is out over the chat back up. */
    public boolean onBackPressed(boolean invoked) {
        if (isRolledOutOverChat() && animatorTopicsVisibility.getValue()) {
            if (invoked) {
                setRolledOut(false, true);
            }
            return false;
        }
        return true;
    }

    /** Called when the messages start moving under a bar that is out over them. */
    public void onChatScrolledByUser() {
        if (isRolledOutOverChat()) {
            setRolledOut(false, true);
        }
    }

    /**
     * A bar rolled out over the chat rolls back up as soon as anything else is touched. The chat
     * passes every touch down here, since the input and the top panels sit above this view.
     * (offsetX, offsetY) is where this view is in the event's coordinates.
     */
    public void onChatTouchDown(MotionEvent ev, float offsetX, float offsetY) {
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN && isRolledOutOverChat() && rollT > 0.5f && !isOverRolledBar(ev, offsetX, offsetY)) {
            setRolledOut(false, true);
        }
    }

    private boolean isOverRolledBar(MotionEvent ev, float offsetX, float offsetY) {
        final float x = ev.getX() - offsetX, y = ev.getY() - offsetY;
        if (sideTabsContainer.getVisibility() == VISIBLE && sideGlassRect.contains(x, y)) return true;
        if (topTabsContainer.getVisibility() == VISIBLE && topGlassRect.contains(x, y)) return true;
        return rollHint != null && rollHint.shown() && rollHint.containsTouch(ev, offsetX + rollHint.getX(), offsetY + rollHint.getY());
    }

    private TLRPC.TL_forumTopic findTopic(long id) {
        if (id == 0) return null;
        final ArrayList<TLRPC.TL_forumTopic> topics = MessagesController.getInstance(currentAccount).getTopicsController().getTopics(-dialogId);
        if (topics != null) {
            for (TLRPC.TL_forumTopic topic : topics) {
                if (getTopicId(topic) == id) return topic;
            }
        }
        return null;
    }

    private void updateWheel(boolean animated) {
        if (rollWheel == null) return;
        final ArrayList<TLRPC.TL_forumTopic> topics = MessagesController.getInstance(currentAccount).getTopicsController().getTopics(-dialogId);
        rollWheel.set(currentTopicId, topics, excludeTopics, bot, animated);
    }

    // While the wheel turns, the island's display tunes to the topic under the lens. It lets go
    // a moment after the wheel settles, and the pinned message comes back.
    private void setRollDisplay(int index) {
        AndroidUtilities.cancelRunOnUIThread(hideRollDisplay);
        if (index < 0) {
            AndroidUtilities.runOnUIThread(hideRollDisplay, 520);
            return;
        }
        final boolean shown = animatorRollDisplay.getFloatValue() > 0;
        if (index != displayIndex || !shown) {
            displayDirection = shown && displayIndex >= 0 ? Integer.signum(index - displayIndex) : 0;
            displayIndex = index;
            displayChangedAt = SystemClock.elapsedRealtime();
            bindDisplay(index);
            if (!shown) {
                animatedDisplayWidth.set(displayContentWidth, true);
            }
            invalidate();
        }
        animatorRollDisplay.setValue(true, true);
    }

    private static Text displayText(CharSequence text, boolean bold, float size) {
        final Text t = new Text("", size, bold ? AndroidUtilities.bold() : null);
        t.setText(Emoji.replaceEmoji(text, t.paint.getFontMetricsInt(), false));
        return t;
    }

    /** Where the display starts: in the top island, beside the upright one, or in the bottom wheel's row. */
    private float getDisplayLeft() {
        if (sidemenuT > 0.5f) return dp(7 + V_ISLAND_W + 7);
        return dp(topicBottom ? BOTTOM_DISPLAY_LEFT : DISPLAY_LEFT);
    }

    /** At the bottom the display shares the wheel's 36 dp row, on two smaller lines. */
    private boolean isDisplayCompact() {
        return topicBottom && sidemenuT <= 0.5f;
    }

    /** The display's maximum text width: from its text to 16 dp (14 at the bottom) before the row's end. */
    private float getDisplayMaxWidth() {
        return isDisplayCompact()
            ? getMeasuredWidth() - dp(7 + 14) - getDisplayLeft() - dp(24)
            : getMeasuredWidth() - dp(7 + 16) - getDisplayLeft() - dp(23);
    }

    // What the display says about a topic: its name, its counter, and its latest message, like the
    // topic's row in the list. For All, where the news is.
    private void bindDisplay(int index) {
        displayPinned = rollWheel.isPinnedAt(index);
        final boolean compact = isDisplayCompact();
        final float titleSize = compact ? 13 : 14, previewSize = compact ? 12 : 14;
        displayTitle = displayText(rollWheel.getTitleAt(index), true, titleSize);
        String chip = null;
        boolean chipMuted = false;
        if (rollWheel.isAllAt(index)) {
            chip = rollWheel.getBadgeString();
            chipMuted = rollWheel.isBadgeMuted();
        } else if (rollWheel.isMentionAt(index)) {
            chip = "@";
        } else if (rollWheel.getUnreadAt(index) > 0) {
            chip = LocaleController.formatNumber(rollWheel.getUnreadAt(index), ',');
            chipMuted = rollWheel.isMutedAt(index);
        }
        displayChip = chip == null ? null : new Text(chip, 10.5f, AndroidUtilities.bold());
        displayChipMuted = chipMuted;

        displaySender = null;
        displayMessage = null;
        final CharSequence typing = rollWheel.getTypingAt(index);
        displayTyping = typing != null;
        if (typing != null) {
            // Who is typing there, like the topic's row in the list.
            displayMessage = displayText(typing, false, previewSize);
        } else if (rollWheel.isAllAt(index)) {
            final CharSequence news = rollWheel.getNewsTitles(3);
            displayMessage = displayText(news != null ? formatString(R.string.TopicsRollNewIn, news) : getString(R.string.TopicsRollAllTogether), false, previewSize);
        } else {
            final CharSequence[] preview = getPreview(rollWheel.getTopicAt(index));
            if (preview != null) {
                displaySender = preview[0] == null ? null : displayText(preview[0], true, previewSize);
                displayMessage = displayText(preview[1], false, previewSize);
            }
        }

        final float titleRow = (displayPinned ? dp(compact ? 15 : 17) : 0) + displayTitle.getCurrentWidth() + (displayChip != null ? dp(5) + getChipWidth(displayChip) : 0);
        final float previewRow = (displaySender != null ? displaySender.getCurrentWidth() : 0) + (displayMessage != null ? displayMessage.getCurrentWidth() : 0);
        displayContentWidth = Math.min(Math.max(titleRow, previewRow), getDisplayMaxWidth());
    }

    /** "Sender: " and the text of a topic's latest message (or what it did, for a service message). */
    private CharSequence[] getPreview(TLRPC.TL_forumTopic topic) {
        if (topic == null || topic.topMessage == null) return null;
        final long key = ((long) topic.id << 32) ^ topic.topMessage.id;
        CharSequence[] preview = displayPreviews.get(key);
        if (preview != null) return preview;
        final MessageObject message = new MessageObject(currentAccount, topic.topMessage, false, false);
        CharSequence sender = null;
        CharSequence text = topic.topMessage.message;
        if (topic.topMessage instanceof TLRPC.TL_messageService) {
            text = message.messageText;
        } else {
            if (TextUtils.isEmpty(text)) {
                text = message.messageText;
            }
            if (message.isOut()) {
                sender = getString(R.string.FromYou);
            } else {
                final long senderId = message.getSenderId();
                if (senderId > 0) {
                    final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(senderId);
                    sender = user != null ? UserObject.getFirstName(user) : null;
                } else if (senderId < 0) {
                    final TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-senderId);
                    sender = chat != null ? chat.title : null;
                }
            }
        }
        if (text == null) text = "";
        text = text.toString().replace('\n', ' ').trim();
        preview = new CharSequence[] { TextUtils.isEmpty(sender) ? null : sender + ": ", text };
        if (displayPreviews.size() > 64) displayPreviews.clear();
        displayPreviews.put(key, preview);
        return preview;
    }

    private static float getChipWidth(Text chip) {
        return Math.max(dp(16), chip.getCurrentWidth() + dp(9));
    }

    private final RectF displayGlassRect = new RectF();

    // The display, in the pinned message's place (or in the stretched island), behind a seam where
    // the pinned message's line is, on the same two lines. For side tabs it takes the pinned
    // message's place beside the upright island, in glass of its own; at the bottom it shares the
    // wheel's row, on two smaller lines. Its glass and line come first and go last, and its words
    // come in once the pinned message's are gone, so words never show over other words.
    private void drawRollDisplay(Canvas canvas) {
        final float shown = getDisplayShown();
        if (shown <= 0 || displayTitle == null) return;
        final float glassShown = smoothstep(0f, 0.25f, shown), wordsShown = smoothstep(0.5f, 1f, shown);
        final boolean blind = sidemenuT > 0.5f, compact = isDisplayCompact();
        final RectF glass;
        final BlurredBackgroundDrawable drawable = topMenuBackgroundDrawable;
        final float left = getDisplayLeft();
        final float top = compact ? topTabsContainer.getTranslationY() + dp(7) : getIslandTop();
        if (blind) {
            // The top row's glass is free on the side: it becomes the display's, as wide as the pinned
            // message, or as the display needs when nothing is pinned.
            if (drawable == null) return;
            final float right = topPanelsCount > 0 ? getMeasuredWidth() - dp(7) : Math.min(getMeasuredWidth() - dp(7), left + dp(23 + 16) + animatedDisplayWidth.getValue());
            displayGlassRect.set(left - dp(7), top - dp(7), right + dp(7), top + dp(ISLAND_HEIGHT + 7));
            displayGlassRect.round(glassBounds);
            if (topGlassRadius != dp(ISLAND_HEIGHT / 2f) - dp(6)) {
                drawable.setRadius(topGlassRadius = dp(ISLAND_HEIGHT / 2f) - dp(6));
            }
            drawable.setBounds(glassBounds);
            drawable.setAlpha((int) (0xFF * glassShown));
            drawable.draw(canvas);
            glass = displayGlassRect;
        } else {
            glass = topGlassRect;
            if (drawable == null || glass.isEmpty()) return;
        }
        canvas.save();
        canvas.clipPath(drawable.getPath());

        displayPaint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_chat_topPanelLine, resourcesProvider), glassShown));
        if (compact) {
            displayRect.set(left + dp(12), top + dp(6), left + dp(14.5f), top + dp(30));
        } else {
            displayRect.set(left + dp(13), top + dp(8.7f), left + dp(16), top + dp(39.3f));
        }
        canvas.drawRoundRect(displayRect, dp(1.5f), dp(1.5f), displayPaint);

        // A new topic slides in from the side the wheel turns to, like the numbers on a dial.
        final float p = Utilities.clamp((SystemClock.elapsedRealtime() - displayChangedAt) / 170f, 1f, 0f);
        if (p < 1f) invalidate();
        final float e = CubicBezierInterpolator.EASE_OUT.getInterpolation(p);
        final float alpha = wordsShown * (0.35f + 0.65f * e);
        canvas.translate(0, displayDirection * dp(9) * (1f - e));

        final float x = left + dp(compact ? 24 : 23);
        final float maxWidth = Math.max(0, Math.min(glass.right - dp(7 + (compact ? 14 : 16)), getMeasuredWidth() - dp(7 + (compact ? 14 : 16))) - x);
        final float titleY = top + dp(compact ? 11.5f : 16.3f), previewY = top + dp(compact ? 26.5f : 34.3f);
        final int textColor = Theme.getColor(Theme.key_chat_topPanelMessage, resourcesProvider);
        final int grayColor = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider);

        float tx = x;
        if (displayPinned) {
            if (displayPinDrawable == null) {
                displayPinDrawable = getContext().getResources().getDrawable(R.drawable.msg_pin_mini).mutate();
            }
            displayPinDrawable.setColorFilter(new PorterDuffColorFilter(grayColor, PorterDuff.Mode.SRC_IN));
            displayPinDrawable.setAlpha((int) (0xFF * alpha));
            final int size = dp(compact ? 12 : 14);
            displayPinDrawable.setBounds((int) tx, (int) (titleY - size / 2f), (int) tx + size, (int) (titleY + size / 2f));
            displayPinDrawable.draw(canvas);
            tx += size + dp(3);
        }
        final float chipSpace = displayChip != null ? dp(5) + getChipWidth(displayChip) : 0;
        displayTitle.ellipsize(Math.max(0, maxWidth - (tx - x) - chipSpace)).draw(canvas, tx, titleY, textColor, alpha);
        tx += displayTitle.getWidth();
        if (displayChip != null) {
            final float w = getChipWidth(displayChip);
            final float chipHalf = dp(compact ? 7 : 8);
            displayRect.set(tx + dp(5), titleY - chipHalf, tx + dp(5) + w, titleY + chipHalf);
            displayPaint.setColor(Theme.multAlpha(Theme.getColor(displayChipMuted ? Theme.key_chats_unreadCounterMuted : Theme.key_chats_unreadCounter, resourcesProvider), alpha));
            canvas.drawRoundRect(displayRect, chipHalf, chipHalf, displayPaint);
            displayChip.draw(canvas, displayRect.centerX() - displayChip.getCurrentWidth() / 2f, titleY, Theme.getColor(Theme.key_chats_unreadCounterText, resourcesProvider), alpha);
        }

        float px = x;
        if (displaySender != null) {
            displaySender.ellipsize(maxWidth).draw(canvas, px, previewY, textColor, alpha);
            px += displaySender.getWidth();
        }
        if (displayMessage != null && maxWidth - (px - x) > dp(8)) {
            final int messageColor = displayTyping ? Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider) : grayColor;
            displayMessage.ellipsize(maxWidth - (px - x)).draw(canvas, px, previewY, messageColor, alpha);
        }
        canvas.restore();
    }

    private void maybeShowRollHint() {
        final SharedPreferences prefs = MessagesController.getGlobalMainSettings();
        final String key = "topicsroll_hint_wheel";
        // The hint points at the wheel in the top row.
        if (prefs.getInt(key, 0) >= 2 || getMeasuredWidth() <= 0 || topicBottom || sidemenuT > 0.5f) {
            return;
        }
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply();
        AndroidUtilities.runOnUIThread(() -> {
            if (!isAttachedToWindow() || !rollable || rolledOut) return;
            showRollHint();
        }, 560);
    }

    private void showRollHint() {
        if (rollHint != null) {
            removeView(rollHint);
        }
        rollHint = new HintView2(getContext(), HintView2.DIRECTION_TOP);
        rollHint.setMultilineText(true);
        rollHint.setText(AndroidUtilities.replaceTags(getString(R.string.TopicsRollHintWheel)));
        rollHint.setMaxWidthPx(HintView2.cutInFancyHalf(rollHint.getText(), rollHint.getTextPaint()));
        rollHint.setDuration(5000);
        rollHint.setRounding(12);
        rollHint.setPadding(dp(8), 0, dp(8), 0);
        rollHint.setOnHiddenListener(() -> {
            if (rollHint != null) {
                removeView(rollHint);
                rollHint = null;
            }
        });
        addView(rollHint, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 120, Gravity.TOP | Gravity.LEFT));
        rollHint.setJointPx(0, dp(7 + ISLAND_RIM) + dp(TopicsRollWheel.WIDTH) / 2f - dp(8));
        rollHint.setTranslationY(getIslandTop() + dp(ISLAND_HEIGHT + 2));
        rollHint.show();
    }

    private void hideRollHint() {
        if (rollHint != null) {
            rollHint.hide();
        }
    }

    private void checkTopicsVisibility(boolean animated) {
        final ArrayList<TLRPC.TL_forumTopic> topicsList = MessagesController.getInstance(currentAccount).getTopicsController().getTopics(-dialogId);
        animatorTopicsVisibility.setValue(topicsList != null && !topicsList.isEmpty() && !allTopicsHidden, animated);
    }

    private void onSideMenuButtonClick(View v) {
        animateSidemenuTo(pendingSidemenu != null ? !pendingSidemenu : !sidemenuEnabled);
    }

    private void onCloseButtonClick(View v) {
        sideTabs.allowReorder(false);
        topTabs.allowReorder(false);
        animatorCloseButtonVisibility.setValue(false, true);
        AndroidUtilities.updateVisibleRows(sideTabs);
        AndroidUtilities.updateVisibleRows(topTabs);
    }

    private ImageView createButton(Context context, @DrawableRes int iconRes, View.OnClickListener onClickListener) {
        ImageView button = new ImageView(context);
        button.setImageResource(iconRes);
        button.setScaleType(ImageView.ScaleType.CENTER);
        button.setOnClickListener(onClickListener);
        ScaleStateListAnimator.apply(button);
        return button;
    }

    private final BoolAnimator animatorCloseButtonVisibility = new BoolAnimator(0,
        (a, b, c, d) -> checkUi_closeButtonVisibility(),
        CubicBezierInterpolator.EASE_OUT_QUINT, 320);

    private void checkUi_closeButtonVisibility() {
        {
            final float visibility = animatorCloseButtonVisibility.getFloatValue();

            closeButtonTop.setAlpha(visibility);
            closeButtonTop.setScaleX(lerp(0.4f, 1f, visibility));
            closeButtonTop.setScaleY(lerp(0.4f, 1f, visibility));
            closeButtonTop.setVisibility(visibility > 0 ? VISIBLE : GONE);

            closeButtonSide.setAlpha(visibility);
            closeButtonSide.setScaleX(lerp(0.4f, 1f, visibility));
            closeButtonSide.setScaleY(lerp(0.4f, 1f, visibility));
            closeButtonSide.setVisibility(visibility > 0 ? VISIBLE : GONE);
        }
        {
            final float visibility = 1f - animatorCloseButtonVisibility.getFloatValue();

            toggleButtonTop.setAlpha(visibility);
            toggleButtonTop.setScaleX(lerp(0.4f, 1f, visibility));
            toggleButtonTop.setScaleY(lerp(0.4f, 1f, visibility));
            toggleButtonTop.setVisibility(visibility > 0 ? VISIBLE : GONE);

            toggleButtonSide.setAlpha(visibility);
            toggleButtonSide.setScaleX(lerp(0.4f, 1f, visibility));
            toggleButtonSide.setScaleY(lerp(0.4f, 1f, visibility));
            toggleButtonSide.setVisibility(visibility > 0 ? VISIBLE : GONE);
        }
    }

    private final RectF topGlassRect = new RectF();
    private final RectF sideGlassRect = new RectF();
    private final RectF wheelGlassRect = new RectF();
    private final Rect glassBounds = new Rect();
    private float sideGlassRadius, topGlassRadius;

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        final float visibility = animatorTopicsVisibility.getFloatValue();
        final boolean rolling = rollT < 1f;
        if (rolling) {
            computeWheelGlassRect(wheelGlassRect);
        }
        if (getTabsVisibility(Position.LEFT) > 0) {
            sideGlassRect.set(
                sideTabsContainer.getTranslationX(),
                sideTabsContainer.getTranslationY(),
                sideTabsContainer.getTranslationX() + dp(7 + 7 + 64),
                getMeasuredHeight() - sideMenuBackgroundMarginBottom);
            if (rolling) {
                // The side tabs drop down out of the wheel like a blind.
                lerp(wheelGlassRect, sideGlassRect, rollT, sideGlassRect);
            }
            // The rail rolls up into the upright island and drops down out of it.
            final float radius = rolling ? lerp(dp(ISLAND_HEIGHT / 2f), dp(16), rollT) : dp(16);
            if (sideGlassRadius != radius) {
                sideMenuBackgroundDrawable.setRadius(sideGlassRadius = radius);
            }
            sideMenuBackgroundDrawable.setAlpha((int) (0xFF * lerp(visibility, 1f, rollT)));
            sideGlassRect.round(glassBounds);
            sideMenuBackgroundDrawable.setBounds(glassBounds);
            sideMenuBackgroundDrawable.draw(canvas);
            drawSideWell(canvas, lerp(visibility, 1f, rollT));
        } else {
            sideGlassRect.setEmpty();
        }
        if ((1f - sidemenuT) * visibility > 0) {
            topGlassRect.set(
                0, topTabsContainer.getTranslationY(),
                getMeasuredWidth(), topTabsContainer.getTranslationY() + dp(TOP_TABS_HEIGHT + 7 + 7));
            if (rolling) {
                // The bar rolls out across the row behind the wheel, like a carpet.
                lerp(wheelGlassRect, topGlassRect, rollT, topGlassRect);
            }
            final float radius = rolling ? lerp(wheelGlassRect.height() / 2f - dp(7), dp(TOP_TABS_HEIGHT / 2f), rollT) : dp(TOP_TABS_HEIGHT / 2f);
            if (topGlassRadius != radius) {
                topMenuBackgroundDrawable.setRadius(topGlassRadius = radius);
            }
            final float topAlpha = lerp(1.0f, 0f, sidemenuT) * lerp(visibility, 1f, rollT);
            topMenuBackgroundDrawable.setAlpha((int) (255 * topAlpha));
            topGlassRect.round(glassBounds);
            topMenuBackgroundDrawable.setBounds(glassBounds);
            topMenuBackgroundDrawable.draw(canvas);
            drawTopWell(canvas, topAlpha);
        } else {
            topGlassRect.setEmpty();
        }

        canvas.save();
        canvas.clipRect(0, 0, getWidth(), getHeight());
        super.dispatchDraw(canvas);
        drawRollEdge(canvas);
        drawRollDisplay(canvas);
        canvas.restore();
    }

    private final TopicsRollWheel.Well islandWell = new TopicsRollWheel.Well();
    private final TopicsRollWheel.Ends trackEnds = new TopicsRollWheel.Ends();
    private final RectF islandWellRect = new RectF();

    // The bar's surface is the wheel's track. Rolled up in the top row the track is the wheel's well,
    // 6 dp inside the island; rolling out it stretches with the wheel, which is its far end until it
    // fades there, and the island's rim gives way as the wheel rises onto the bar's line. At the
    // bottom the track is the island's whole surface from the start. The toggle sits in its left end
    // and « or Keep open in its right end, both shaded where the drum curves away.
    private void drawTopWell(Canvas canvas, float alpha) {
        if (alpha <= 0 || topGlassRect.isEmpty()) return;
        final boolean dark = rollWheel.isDark();
        final float inset = topicBottom ? 0 : lerp(dp(ISLAND_RIM), 0, rollT);
        final float left = topGlassRect.left + dp(7) + inset, top = topGlassRect.top + dp(7) + inset, full = topGlassRect.right - dp(7);
        final float right = rollT < 1f ? Math.min(full, wheelLeftPx + dp(TopicsRollWheel.WIDTH)) : full;
        islandWellRect.set(left, top, right, top + dp(TOP_TABS_HEIGHT));
        islandWell.draw(canvas, islandWellRect, alpha, dark);
        // Rolled up the wheel shades its own ends; rolling out, the track's left end takes over as the
        // wheel leaves it, its right end as the wheel fades there.
        trackEnds.draw(canvas, islandWellRect, islandWellRect.height() / 2f, dp(24), smoothstep(0f, 0.2f, rollT) * alpha, smoothstep(0.8f, 1f, rollT) * alpha, false, dark);
    }

    private final Paint railGearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // For side tabs the rail's surface is the track standing up: rolled up it is the upright wheel's
    // well, 6 dp inside its island, and it grows down out of it as the rail drops, its ends shaded
    // where the drum curves away.
    private void drawSideWell(Canvas canvas, float alpha) {
        if (alpha <= 0 || sideGlassRect.isEmpty()) return;
        final boolean dark = rollWheel.isDark();
        final float inset = dp(7) + lerp(dp(ISLAND_RIM), 0, rollT);
        islandWellRect.set(sideGlassRect.left + inset, sideGlassRect.top + inset, sideGlassRect.right - inset, sideGlassRect.bottom - inset);
        final float radius = lerp(dp(TopicsRollWheel.HEIGHT / 2f), dp(16), rollT);
        islandWell.draw(canvas, islandWellRect, radius, alpha, dark);
        final float ends = smoothstep(0.3f, 0.8f, rollT) * alpha;
        trackEnds.draw(canvas, islandWellRect, radius, dp(24), ends, ends, true, dark);

        // The gear line, down the track's right edge, while the rail winds up into the upright wheel
        // or drops down out of it (see getGearAlpha). The rail's tabs fade early, so it is the
        // track's; it hands over to the wheel's own pale ticks as the wheel appears.
        final float gear = getGearAlpha() * Utilities.clamp(rollT / 0.3f, 1f, 0f) * alpha;
        if (gear > 0) {
            railGearPaint.setStrokeCap(Paint.Cap.ROUND);
            railGearPaint.setStrokeWidth(dp(1.25f));
            railGearPaint.setColor(TopicsRollWheel.getKnurlColor(resourcesProvider, dark));
            railGearPaint.setAlpha((int) (railGearPaint.getAlpha() * gear));
            final float x1 = islandWellRect.right - dp(TopicsRollWheel.KNURL_FROM_EDGE), x0 = x1 - dp(TopicsRollWheel.KNURL);
            final float tick = dp(SCALE_TICK);
            for (float y = islandWellRect.top + radius; y <= islandWellRect.bottom - radius; y += tick) {
                canvas.drawLine(x0, y, x1, y, railGearPaint);
            }
        }
    }

    /** How far Keep open covers the end of the bar, 0 to 1. */
    private float getKeepCover() {
        return rollButtonTop != null ? rollButtonTop.getKeepProgress() : 0f;
    }

    /** Where the bar's tabs start to fade out before Keep open, in the tabs' list. */
    private float getKeepCoverLeft() {
        return rollButtonTop != null ? rollButtonTop.getLeft() + rollButtonTop.getKeepLeft() - topTabs.getLeft() - dp(18) : topTabs.getWidth();
    }

    /** How much of the row the bar's tabs show: up to Keep open while it is there. */
    private float getTopTabsVisibleWidth() {
        return lerp(topTabs.getWidth(), getKeepCoverLeft() + dp(18), getKeepCover());
    }

    /** The topic's place on the wheel for a tab, or -1 (All, New topic, loading). */
    private int getWheelIndex(UItem item) {
        if (item == null || item.red || item.id == -2 || item.object == null) return -1;
        return rollWheel.findIndex(mono ? item.longValue : item.id);
    }

    // The island's glass (with the drawable's 7 dp padding): it stays put while the wheel is
    // pressed into its well. In the top row it shares the chat's first panel row; at the bottom it is
    // the wheel's track in the bar's row; for side tabs it stands up at the top of the rail.
    private void computeWheelGlassRect(RectF rect) {
        animatedDisplayWidth.set(displayContentWidth);
        final float top = getBarBaseTop();
        if (sidemenuT > 0.5f) {
            rect.set(0, top, dp(V_ISLAND_W + 14), top + dp(V_ISLAND_H + 14));
        } else if (topicBottom) {
            final float barTop = topTabsContainer.getTranslationY();
            rect.set(0, barTop, getBottomIslandWidth() + dp(14), barTop + dp(TOP_TABS_HEIGHT + 14));
        } else {
            rect.set(0, top, getIslandWidth() + dp(14), top + dp(ISLAND_HEIGHT + 14));
        }
    }

    private final Paint rollEdgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix rollEdgeMatrix = new Matrix();
    private LinearGradient rollEdgeGradientH, rollEdgeGradientV;

    // While the side rail drops down or rolls up, its moving end looks like the rolled-up part of a
    // blind: a soft shade, a lit rim and the shadow of the roll itself. (The top bar has the wheel.)
    private void drawRollEdge(Canvas canvas) {
        if (rollT <= 0f || rollT >= 1f || sidemenuT <= 0.5f) return;
        final float strength = (float) Math.sin(Math.PI * rollT) * animatorTopicsVisibility.getFloatValue();
        if (strength <= 0.01f) return;
        final boolean vertical = true;
        final BlurredBackgroundDrawable drawable = vertical ? sideMenuBackgroundDrawable : topMenuBackgroundDrawable;
        final RectF glass = vertical ? sideGlassRect : topGlassRect;
        if (drawable == null || glass.isEmpty()) return;

        if (rollEdgeGradientH == null) {
            final int[] colors = { 0x00000000, 0x14000000, 0x38FFFFFF, 0x24000000 };
            final float[] stops = { 0f, 0.6f, 0.84f, 1f };
            rollEdgeGradientH = new LinearGradient(0, 0, 1, 0, colors, stops, Shader.TileMode.CLAMP);
            rollEdgeGradientV = new LinearGradient(0, 0, 0, 1, colors, stops, Shader.TileMode.CLAMP);
        }
        final float size = dp(18);
        rollEdgeMatrix.reset();
        if (vertical) {
            final float edge = glass.bottom - dp(7);
            rollEdgeMatrix.setScale(1, size);
            rollEdgeMatrix.postTranslate(0, edge - size);
            rollEdgeGradientV.setLocalMatrix(rollEdgeMatrix);
            rollEdgePaint.setShader(rollEdgeGradientV);
            AndroidUtilities.rectTmp.set(glass.left, edge - size, glass.right, edge);
        } else {
            final float edge = glass.right - dp(7);
            rollEdgeMatrix.setScale(size, 1);
            rollEdgeMatrix.postTranslate(edge - size, 0);
            rollEdgeGradientH.setLocalMatrix(rollEdgeMatrix);
            rollEdgePaint.setShader(rollEdgeGradientH);
            AndroidUtilities.rectTmp.set(edge - size, glass.top, edge, glass.bottom);
        }
        rollEdgePaint.setAlpha((int) (0xFF * strength));
        canvas.save();
        canvas.clipPath(drawable.getPath());
        canvas.drawRect(AndroidUtilities.rectTmp, rollEdgePaint);
        canvas.restore();
    }


    @Override
    protected boolean drawChild(@NonNull Canvas canvas, View child, long drawingTime) {
        canvas.save();
        if (child == sideTabsContainer) {
            canvas.clipPath(sideMenuBackgroundDrawable.getPath());
        }
        if (child == topTabsContainer) {
            canvas.clipPath(topMenuBackgroundDrawable.getPath());
        }
        if (child == topTabsContainer && rollT < 1f && sidemenuT <= 0.5f) {
            // The tabs are only laid down behind the wheel as it rolls, until it fades at the end.
            final float reveal = smoothstep(0.8f, 1f, rollT) * dp(TopicsRollWheel.WIDTH + 20);
            canvas.clipRect(0, 0, rollWheel.getX() + dp(TopicsRollWheel.INSET + 6) + reveal, getHeight());
        }
        final boolean result = super.drawChild(canvas, child, drawingTime);
        canvas.restore();
        return result;
    }

    private BlurredBackgroundDrawable sideMenuBackgroundDrawable;
    private BlurredBackgroundDrawable topMenuBackgroundDrawable;
    private float sideMenuBackgroundMarginBottom;
    private float sideMenuBackgroundMarginTop;

    public void setSideMenuBackgroundDrawable(BlurredBackgroundDrawable sideMenuBackgroundDrawable) {
        this.sideMenuBackgroundDrawable = sideMenuBackgroundDrawable;
        this.sideMenuBackgroundDrawable.setRadius(sideGlassRadius = dp(16));
        this.sideMenuBackgroundDrawable.setPadding(dp(7));
    }

    public void setTopMenuBackgroundDrawable(BlurredBackgroundDrawable sideMenuBackgroundDrawable) {
        this.topMenuBackgroundDrawable = sideMenuBackgroundDrawable;
        this.topMenuBackgroundDrawable.setRadius(topGlassRadius = dp(TOP_TABS_HEIGHT / 2f));
        this.topMenuBackgroundDrawable.setPadding(dp(7));
    }



    public void setSideMenuBackgroundMarginBottom(float margin) {
        sideMenuBackgroundMarginBottom = margin;
        checkUi_topicsVerticalPosition();
        checkSideTabsPadding(true);
        invalidate();
    }

    public void setSideMenuBackgroundMarginTop(float margin) {
        sideMenuBackgroundMarginTop = margin;
        checkUi_topicsVerticalPosition();
        checkSideTabsPadding(true);
        invalidate();
    }

    private void checkSideTabsPadding(final boolean force) {
        final int oldPadding = sideTabsContainer.getPaddingBottom();
        final int padding = Math.round(sideMenuBackgroundMarginBottom + getBarBaseTop());

        if (oldPadding == padding) {
            return;
        }

        if (force) {
            sideTabsContainer.setPadding(0, 0, 0, padding);
            return;
        }

        if (padding < oldPadding) {
            sideTabsContainer.setPadding(0, 0, 0, 0);
        }
    }

    private Runnable onUpdateSideMenuPosition;

    public void doOnUpdateSideMenuPosition(Runnable runnable) {
        onUpdateSideMenuPosition = runnable;
    }

    private boolean topicBottom;
    private boolean sidemenuEnabled;
    private float sidemenuT = 0.0f;
    private boolean sidemenuAnimating;
    public void updateSidemenuPosition() {
        if (onUpdateSideMenuPosition != null) {
            onUpdateSideMenuPosition.run();
        }

        checkUi_topicsVerticalPosition();
        if (header != null) {
            header.invalidate();
        }

        final float leftTabsVisibility = getTabsVisibility(Position.LEFT);
        sideTabsContainer.setTranslationX(lerp(-dp(64 + 7 + 7), 0, leftTabsVisibility));
        checkUi_roll();

        toggleButtonTop.setColorFilter(new PorterDuffColorFilter(
            ColorUtils.blendARGB(
                Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider),
                Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider),
                1f - sidemenuT
            ),
            PorterDuff.Mode.SRC_IN
        ));
        toggleButtonSide.setColorFilter(new PorterDuffColorFilter(
                ColorUtils.blendARGB(
                        Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider),
                        Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider),
                        sidemenuT
                ),
                PorterDuff.Mode.SRC_IN
        ));
        closeButtonTop.setColorFilter(new PorterDuffColorFilter(
            Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider),
            PorterDuff.Mode.SRC_IN
        ));
        closeButtonSide.setColorFilter(new PorterDuffColorFilter(
                Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider),
                PorterDuff.Mode.SRC_IN
        ));

        invalidate();
    }

    private void checkUi_topicsVerticalPosition() {
        if (topicBottom) {
            // A bottom bar stays at the bottom, rolled up or out: its wheel is a thumb wheel there.
            topTabsContainer.setTranslationY(getMeasuredHeight() - dp(TOP_TABS_HEIGHT + 7 + 7) - sideMenuBackgroundMarginBottom + lerp(dp(TOP_TABS_HEIGHT + 7), 0, getTabsVisibility(Position.BOTTOM)));
        } else {
            topTabsContainer.setTranslationY(getBarBaseTop() + lerp(-dp(TOP_TABS_HEIGHT + 7), 0, getTabsVisibility(Position.TOP)));
        }
        sideTabsContainer.setTranslationY(getBarBaseTop());
        checkSideTabsPadding(true);
        checkUi_roll();
    }

    private Boolean pendingSidemenu;
    private ValueAnimator animator;
    private void animateSidemenuTo(boolean side) {
        if (sidemenuEnabled == side) return;
        if (animator != null) {
            animator.cancel();
            if (sidemenuAnimating) {
                pendingSidemenu = side;
                return;
            }
        }

        if (!side) {
            topicBottom = !topicBottom;
        }

        sidemenuEnabled = side;
        sidemenuAnimating = true;
        animator = ValueAnimator.ofFloat(sidemenuT, side ? 1.0f : 0.0f);
        animator.addUpdateListener(anm -> {
            sidemenuT = (float) anm.getAnimatedValue();
            updateSidemenuPosition();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (animator == animation) {
                    sidemenuT = side ? 1.0f : 0.0f;
                    updateSidemenuPosition();
                    sidemenuAnimating = false;
                    toggleButtonSide.setImageResource(topicBottom ? R.drawable.menu_sidebar_top : R.drawable.menu_sidebar_bottom);
                    animator = null;
                    MessagesController.getInstance(currentAccount).getMainSettings().edit()
                        .putBoolean("topicssidetabs" + dialogId, sidemenuEnabled)
                        .putBoolean("topicssidetabsb" + dialogId, topicBottom)
                        .apply();
                    if (pendingSidemenu != null && side != pendingSidemenu) {
                        final boolean newValue = pendingSidemenu;
                        pendingSidemenu = null;
                        animateSidemenuTo(newValue);
                    }
                    AndroidUtilities.runOnUIThread(() -> {
                        if (isLoadingVisible()) {
                            loadMore();
                        }
                    });
                }
            }
        });
//        animator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
//        animator.setDuration(320);
        animator.setInterpolator(ChatListItemAnimator.DEFAULT_INTERPOLATOR);
        animator.setDuration(ChatListItemAnimator.DEFAULT_DURATION);
        animator.start();
    }

    private long currentTopicId;
    private void updateTabs() {
        checkTopicsVisibility(true);
        updateWheel(true);

        boolean wasOnLeft = !topTabs.canScrollHorizontally(-1);
        topTabs.adapter.update(true);
        invalidate();
        if (wasOnLeft) {
            topTabs.scrollToPosition(0);
        }

        boolean wasOnTop = !sideTabs.canScrollVertically(-1);
        sideTabs.adapter.update(true);
        if (wasOnTop) {
            sideTabs.scrollToPosition(0);
        }
        AndroidUtilities.runOnUIThread(() -> {
            if (isLoadingVisible()) {
                loadMore();
            }
        });
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.topicsDidLoaded) {
            if ((Long) args[0] != -dialogId)
                return;
            updateTabs();
        } else if (id == NotificationCenter.updateInterfaces) {
            int mask = (Integer) args[0];
            if (/*!mono &&*/ (mask & MessagesController.UPDATE_MASK_SELECT_DIALOG) > 0) {
                MessagesController.getInstance(currentAccount).getTopicsController().sortTopics(-dialogId, false);
                updateTabs();
            }
            if ((mask & MessagesController.UPDATE_MASK_USER_PRINT) != 0) {
                updateTyping();
            }
        }
    }

    // Someone started or stopped typing: the lights on the wheel's scale, on the bar's and on the
    // rail's follow, and so does the display.
    private void updateTyping() {
        if (mono) return;
        rollWheel.updateTyping();
        topTabs.invalidate();
        sideTabs.invalidate();
        invalidate();
        if (displayIndex >= 0 && animatorRollDisplay.getFloatValue() > 0) {
            bindDisplay(displayIndex);
            invalidate();
        }
    }

    private void whenReordered(int id, ArrayList<UItem> items) {
        // if (mono) return;
        final TopicsController controller = MessagesController.getInstance(currentAccount).getTopicsController();
        final ArrayList<Integer> topics = new ArrayList<>();
        for (int i = 0; i < items.size(); ++i) {
            topics.add(items.get(i).id);
        }
        controller.reorderPinnedTopics(-dialogId, topics);
        controller.sortTopics(-dialogId, false);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        setAttached(true);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        setAttached(false);
    }

    private boolean notificationsAttached;
    private void setAttached(boolean attach) {
        if (notificationsAttached == attach) return;
        if (notificationsAttached = attach) {
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.topicsDidLoaded);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.updateInterfaces);
            MessagesController.getInstance(currentAccount).getTopicsController().onTopicFragmentResume(-dialogId);
        } else {
            AndroidUtilities.cancelRunOnUIThread(rollUpAfterSelect);
            AndroidUtilities.cancelRunOnUIThread(hideRollDisplay);
            MessagesController.getInstance(currentAccount).getTopicsController().onTopicFragmentPause(-dialogId);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.topicsDidLoaded);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.updateInterfaces);
        }
    }

    private void fillVerticalTabs(ArrayList<UItem> items, UniversalAdapter adapter) {
        final TLRPC.Chat currentChat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
        final TLRPC.User currentUser = MessagesController.getInstance(currentAccount).getUser(dialogId);
        final TopicsController controller = MessagesController.getInstance(currentAccount).getTopicsController();
        final ArrayList<TLRPC.TL_forumTopic> topics = controller.getTopics(-dialogId);
        if (!bot) {
            items.add(VerticalTabView.Factory.asAll(bot, mono).setChecked(currentTopicId == 0));
        }

        boolean reorder = false;
        if (topics != null) {
            for (TLRPC.TL_forumTopic topic : topics) {
                if (bot && topic.id == 1) continue;
                if (excludeTopics.contains(topic.id)) continue;
                if (!topic.pinned && reorder) {
                    adapter.reorderSectionEnd();
                    reorder = false;
                } else if (topic.pinned && !reorder) {
                    adapter.reorderSectionStart();
                    reorder = true;
                }
                items.add(VerticalTabView.Factory.asTab(dialogId, topic, mono).setChecked(currentTopicId == getTopicId(topic)));
            }
        }
        if (reorder) {
            adapter.reorderSectionEnd();
        }
        if (topics != null && !topics.isEmpty() && !controller.endIsReached(-dialogId) && canShowProgress) {
            items.add(VerticalTabView.Factory.asLoading(-2));
            items.add(VerticalTabView.Factory.asLoading(-3));
            items.add(VerticalTabView.Factory.asLoading(-4));
        }
        if (!bot && !mono && (currentChat != null && ChatObject.canCreateTopic(currentChat) || UserObject.isBotForumWithEditableTopics(currentUser))) {
            items.add(VerticalTabView.Factory.asAdd(false));
        }
    }

    private void fillHorizontalTabs(ArrayList<UItem> items, UniversalAdapter adapter) {
        final TLRPC.Chat currentChat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
        final TLRPC.User currentUser = MessagesController.getInstance(currentAccount).getUser(dialogId);
        final TopicsController controller = MessagesController.getInstance(currentAccount).getTopicsController();
        final ArrayList<TLRPC.TL_forumTopic> topics = controller.getTopics(-dialogId);
        items.add(HorizontalTabView.Factory.asAll(bot, mono).setChecked(currentTopicId == 0));

        boolean reorder = false;
        if (topics != null) {
            for (TLRPC.TL_forumTopic topic : topics) {
                if (bot && topic.id == 1) continue;
                if (excludeTopics.contains(topic.id)) continue;
                if (!topic.pinned && reorder) {
                    if (!items.isEmpty()) {
                        items.get(items.size() - 1).flags |= 8;
                    }
                    adapter.reorderSectionEnd();
                    reorder = false;
                } else if (topic.pinned && !reorder) {
                    adapter.reorderSectionStart();
                    reorder = true;
                }
                items.add(HorizontalTabView.Factory.asTab(dialogId, topic, mono).setChecked(currentTopicId == getTopicId(topic)));
            }
        }
        if (reorder) {
            adapter.reorderSectionEnd();
        }
        if (topics != null && !topics.isEmpty() && !controller.endIsReached(-dialogId) && canShowProgress) {
            items.add(HorizontalTabView.Factory.asLoading(-2));
            items.add(HorizontalTabView.Factory.asLoading(-3));
            items.add(HorizontalTabView.Factory.asLoading(-4));
        }
        if (!bot && !mono && (currentChat != null && ChatObject.canCreateTopic(currentChat) || UserObject.isBotForumWithEditableTopics(currentUser))) {
            items.add(HorizontalTabView.Factory.asAdd());
        }
    }

    private boolean isLoadingVisible() {
        if (sidemenuT > 0.5f) {
            for (int i = 0; i < sideTabs.getChildCount(); ++i) {
                final View child = sideTabs.getChildAt(i);
                final int position = sideTabs.getChildAdapterPosition(child);
                final UItem item = sideTabs.adapter.getItem(position);
                if (item != null && item.red) return true;
            }
        } else {
            for (int i = 0; i < topTabs.getChildCount(); ++i) {
                final View child = topTabs.getChildAt(i);
                final int position = topTabs.getChildAdapterPosition(child);
                final UItem item = topTabs.adapter.getItem(position);
                if (item != null && item.red) return true;
            }
        }
        return false;
    }

    private void loadMore() {
        final TopicsController controller = MessagesController.getInstance(currentAccount).getTopicsController();
        if (!controller.endIsReached(-dialogId)) {
            controller.loadTopics(-dialogId);
        }
    }

    private void onTabClick(UItem item, View view, int position, float x, float y) {
        if (item.longValue != -2) {
            // The chat slides in from the side the topic is on, along the bar or down the rail.
            pendingSwitchDirection = Integer.compare(rollWheel.indexOf(mono ? item.longValue : item.id), rollWheel.indexOf(currentTopicId));
        }
        if (isRolledOutOverChat()) {
            // Picked a topic from a bar rolled out over the chat: let the selection land, then wind back up
            // (once the topic has opened, see rollUpAfterPick).
            AndroidUtilities.cancelRunOnUIThread(rollUpAfterSelect);
            AndroidUtilities.runOnUIThread(rollUpAfterSelect, item.longValue == -2 ? 0 : 340);
            pickTime = SystemClock.uptimeMillis();
            rollUpOnOpen = false;
            topicOpening = item.longValue != -2 && (mono ? item.longValue : item.id) != currentTopicId;
        }
        if (mono) {
            if (onDialogSelected != null) {
                onDialogSelected.run(item.longValue, false);
            }
        } else {
            if (item.longValue == -2) {
                if (onTopicCreated != null) {
                    onTopicCreated.run();
                }
            } else if (onTopicSelected != null) {
                onTopicSelected.run(item.id, false);
            }
        }
    }

    private boolean onTabLongClick(UItem item, View view, int position, float x, float y) {
        if (sideTabs.isReorderAllowed() || topTabs.isReorderAllowed()) return false;
        if (item.object instanceof TLRPC.TL_forumTopic) {
            final TLRPC.TL_forumTopic topic = (TLRPC.TL_forumTopic) item.object;
            final MessagesController messagesController = MessagesController.getInstance(currentAccount);
            final TLRPC.Chat currentChat = dialogId < 0 ? messagesController.getChat(-dialogId) : null;
            final TLRPC.User currentUser = dialogId > 0 ? messagesController.getUser(dialogId) : null;
            final ItemOptions options = ItemOptions.makeOptions(fragment, view, true);

            if (ChatObject.isMonoForum(currentChat)) {
                final long topicId = DialogObject.getPeerDialogId(topic.from_id);
                if (topicId == 0 || !ChatObject.canManageMonoForum(currentAccount, currentChat)) {
                    return false;
                }
                options.add(
                    R.drawable.msg_clear,
                    getString(R.string.ClearHistory),
                    () -> {
                        options.dismiss();
                        final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(topicId);
                        if (user != null) {
                            AlertsCreator.createClearDaysDialogAlert(fragment, -1, user, currentChat, true, revoke -> {
                                if (fragment instanceof ChatActivity) {
                                    ((ChatActivity) fragment).performHistoryClear(topicId, false, true);
                                }
                            }, fragment.getResourceProvider());
                        }
                    }
                );
                long _chatId = currentChat.id;
                if (ChatObject.isMonoForum(currentChat) && ChatObject.canManageMonoForum(currentAccount, currentChat) && currentChat.linked_monoforum_id != 0) {
                    _chatId = currentChat.linked_monoforum_id;
                }
                final long chatId = _chatId;
                final TLRPC.Chat channel = MessagesController.getInstance(currentAccount).getChat(chatId);
                final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(topicId);
                if (user != null && ChatObject.canBlockUsers(channel)) {
                    options.add(R.drawable.msg_remove, getString(R.string.BanUserMonoforum), null);
                    ActionBarMenuSubItem subitem = options.getLast();
                    subitem.setVisibility(View.GONE);
                    MessagesController.getInstance(currentAccount).checkIsInChat(true, channel, user, (isInChat, currentAdminRights, rank) -> {
                        AndroidUtilities.runOnUIThread(() -> {
                            final boolean banned = !isInChat;
                            subitem.setVisibility(View.VISIBLE);
                            subitem.setText(getString(banned ? R.string.UnbanUserMonoforum : R.string.BanUserMonoforum));
                            subitem.setOnClickListener(v -> {
                                options.dismiss();
                                if (!banned) {
                                    MessagesController.getInstance(currentAccount).deleteParticipantFromChat(chatId, user, null, false, false);
                                } else {
                                    TLRPC.TL_channels_editBanned req = new TLRPC.TL_channels_editBanned();
                                    req.participant = MessagesController.getInputPeer(user);
                                    req.channel = MessagesController.getInputChannel(channel);
                                    req.banned_rights = new TLRPC.TL_chatBannedRights();
                                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                                        if (response != null) {
                                            final TLRPC.Updates updates = (TLRPC.Updates) response;
                                            MessagesController.getInstance(currentAccount).processUpdates(updates, false);
                                            if (!updates.chats.isEmpty()) {
                                                AndroidUtilities.runOnUIThread(() -> {
                                                    TLRPC.Chat chat = updates.chats.get(0);
                                                    MessagesController.getInstance(currentAccount).loadFullChat(chat.id, 0, true);
                                                }, 1000);
                                            }
                                        }
                                    });
                                }
                            });
                        });
                    });
                }
            } else {
                if (ChatObject.canManageTopics(currentChat) || UserObject.isBotForumWithEditableTopics(currentUser)) {
                    options.add(
                            topic.pinned ? R.drawable.msg_unpin : R.drawable.msg_pin,
                            getString(topic.pinned ? R.string.DialogUnpin : R.string.DialogPin),
                            () -> {
                                options.dismiss();
                                messagesController.getTopicsController()
                                        .pinTopic(-dialogId, topic.id, !topic.pinned, fragment);
                            }
                    );

                    if (topic.pinned) {
                        options.add(
                                R.drawable.tabs_reorder,
                                getString(R.string.FilterReorder),
                                () -> {
                                    sideTabs.allowReorder(true);
                                    topTabs.allowReorder(true);
                                    animatorCloseButtonVisibility.setValue(true, true);
                                    AndroidUtilities.updateVisibleRows(topTabs);
                                    AndroidUtilities.updateVisibleRows(sideTabs);
                                }
                        );
                    }
                }

                if (ChatObject.canManageTopics(currentChat) || UserObject.isBotForumWithEditableTopics(currentUser)) {
                    options.add(R.drawable.outline_profile_edit_24, getString(R.string.EditTopic), () -> {
                        options.dismiss();
                        fragment.presentFragment(TopicCreateFragment.create(-dialogId, topic.id));
                    });
                }

                final ItemOptions muteOptions = ChatNotificationsPopupWrapper.addAsItemOptions(fragment, options, dialogId, topic.id);
                final boolean muted = messagesController.isDialogMuted(dialogId, topic.id);
                options.add(
                        muted ? R.drawable.msg_unmute : R.drawable.msg_mute,
                        muted ? getString(R.string.Unmute) : getString(R.string.Mute),
                        () -> {
                            if (messagesController.isDialogMuted(dialogId, topic.id)) {
                                options.dismiss();
                                NotificationsController.getInstance(currentAccount).muteDialog(dialogId, topic.id, false);
                                if (BulletinFactory.canShowBulletin(fragment)) {
                                    BulletinFactory.createMuteBulletin(fragment, NotificationsController.SETTING_MUTE_UNMUTE, 0, resourcesProvider).show();
                                }
                            } else {
                                options.openSwipeback(muteOptions);
                            }
                        }
                );
                if (ChatObject.canManageTopic(currentAccount, currentChat, topic) && !UserObject.isBotForum(currentUser)) {
                    options.add(
                            topic.closed ? R.drawable.msg_topic_restart : R.drawable.msg_topic_close,
                            topic.closed ? getString(R.string.RestartTopic) : getString(R.string.CloseTopic),
                            () -> {
                                options.dismiss();
                                MessagesController.getInstance(currentAccount).getTopicsController().toggleCloseTopic(-dialogId, topic.id, !topic.closed);
                            }
                    );
                }
                if (ChatObject.canDeleteTopic(currentAccount, currentChat, topic)) {
                    options.add(R.drawable.msg_delete, getPluralString("DeleteTopics", 1), () -> {
                        options.dismiss();
                        HashSet<Integer> hashSet = new HashSet();
                        hashSet.add(topic.id);
                        deleteTopics(hashSet, () -> {
                        });
                    });
                }
            }
            if (view instanceof HorizontalTabView) {
                options.setScrimViewBackground(new Drawable() {
                    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    private RectF bound = new RectF();

                    {
                        paint.setColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground, resourcesProvider));
                    }

                    @Override
                    public void draw(@NonNull Canvas canvas) {
                        bound.set(getBounds());
                        float insetY = (bound.height() - dp(28)) / 2f;
                        bound.inset(dp(1), insetY);
                        canvas.drawRoundRect(bound, dp(14), dp(14), paint);
                    }

                    @Override
                    public void setAlpha(int alpha) {
                        paint.setAlpha(alpha);
                    }

                    @Override
                    public void setColorFilter(@Nullable ColorFilter colorFilter) {

                    }

                    @Override
                    public int getOpacity() {
                        return PixelFormat.TRANSPARENT;
                    }
                });
                options.translate(dp(16), 0);
            } else {
                options.setScrimViewBackground(Theme.createRoundRectDrawable(0, dp(5), dp(5), 0, Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider)));
            }
            options.show();
            return true;
        }
        return false;
    }

    public TLRPC.TL_forumTopic getTopic(long id) {
        final ArrayList<TLRPC.TL_forumTopic> topics = MessagesController.getInstance(currentAccount).getTopicsController().getTopics(-dialogId);
        if (topics != null) {
            for (TLRPC.TL_forumTopic topic : topics) {
                if (topic.id == id)
                    return topic;
            }
        }
        return null;
    }

    public void setCurrentTopic(long topicId) {
        if (this.currentTopicId != topicId) {
            // The list on show starts its glass gliding on its next frame; until then it counts as moving.
            if (topGlide != null) topGlide.expect();
            if (sideGlide != null) sideGlide.expect();
        }
        this.currentTopicId = topicId;
        topTabs.adapter.update(true);
        topTabs.invalidate();
        sideTabs.adapter.update(true);
        updateWheel(isAttachedToWindow());
        if (botCreateTopicButtonVertical != null) {
            botCreateTopicButtonVertical.setAll(true, false, topicId == 0);
        }
        if (botCreateTopicButtonHorizontal != null) {
            botCreateTopicButtonHorizontal.setAll(true, false, topicId == 0);
        }
    }

    private Utilities.Callback2<Integer, Boolean> onTopicSelected;
    public void setOnTopicSelected(Utilities.Callback2<Integer, Boolean> listener) {
        onTopicSelected = listener;
    }
    private Runnable onTopicCreated;
    public void setOnNewTopicSelected(Runnable listener) {
        onTopicCreated = listener;
    }
    public void selectTopic(long topicId, boolean fromMessage) {
        if (mono) {
            if (onDialogSelected != null) {
                onDialogSelected.run(topicId, fromMessage);
            }
        } else {
            if (onTopicSelected != null) {
                onTopicSelected.run((int) topicId, fromMessage);
            }
        }
    }

    private Utilities.Callback2<Long, Boolean> onDialogSelected;
    public void setOnDialogSelected(Utilities.Callback2<Long, Boolean> listener) {
        onDialogSelected = listener;
    }

    public static class VerticalTabView extends FrameLayout {

        private final int currentAccount;
        private final Theme.ResourcesProvider resourcesProvider;
        private Shaker shaker;

        private final LinearLayout layout;
        private final FrameLayout.LayoutParams imageViewParams;
        private final AnimatedTextView.AnimatedTextDrawable counterText;
        private final FrameLayout imageLayoutView;
        private final BackupImageView imageView;
        private final AvatarDrawable avatarDrawable;
        private final TextView textView;
        /** The topic this tab stands for, as the lens knows it: 0 for All. */
        private long lensId = Long.MIN_VALUE;
        /** Someone is typing in the topic: its badge shows at the icon's lower corner (see drawTypingBadge). */
        private boolean typing, muted;
        private AnimatedFloat typingAlpha;
        private final Paint typingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF typingRect = new RectF();

        public void setTyping(boolean typing) {
            if (this.typing == typing) return;
            this.typing = typing;
            imageLayoutView.invalidate();
        }

        private boolean reorder;
        public void setReorder(boolean value) {
            this.reorder = value;
            layout.invalidate();
        }

        /** Where the middle of the icon is, in this tab: where the lens sits. */
        public float getIconCenterX() {
            return layout.getLeft() + imageLayoutView.getLeft() + imageView.getLeft() + imageView.getWidth() / 2f;
        }

        public float getIconCenterY() {
            return layout.getTop() + imageLayoutView.getTop() + imageView.getTop() + imageView.getHeight() / 2f;
        }

        public VerticalTabView(Context context, int currentAccount, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.currentAccount = currentAccount;
            this.resourcesProvider = resourcesProvider;

            layout = new LinearLayout(context) {
                private final AnimatedFloat shakeAlpha = new AnimatedFloat(this, 360, CubicBezierInterpolator.EASE_OUT_QUINT);
                @Override
                protected void dispatchDraw(@NonNull Canvas canvas) {
                    canvas.save();
                    final float shakeAlpha = this.shakeAlpha.set(reorder);
                    if (shakeAlpha > 0) {
                        if (shaker == null) shaker = new Shaker(this);
                        canvas.translate(getWidth() / 2f, getHeight() / 2f);
                        shaker.concat(canvas, shakeAlpha);
                        canvas.translate(-getWidth() / 2f, -getHeight() / 2f);
                    }
                    super.dispatchDraw(canvas);
                    canvas.restore();
                }
            };
            layout.setWillNotDraw(false);
            layout.setOrientation(LinearLayout.VERTICAL);
            // In the rail a tab sinks into the drop of glass under your finger (see PressDrop).
            addView(layout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL, 1, 0, 0, 0));

            counterText = new AnimatedTextView.AnimatedTextDrawable();
            counterText.setTextSize(dp(11));
            counterText.setTypeface(AndroidUtilities.bold());
            counterText.setTextColor(Theme.getColor(Theme.key_chats_unreadCounterText, resourcesProvider));
            counterText.setOverrideFullWidth(AndroidUtilities.displaySize.x);
            counterText.setGravity(Gravity.CENTER);
            imageLayoutView = new FrameLayout(context) {
                private final Paint clipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                private final AnimatedPaint backgroundPaint = new AnimatedPaint(this, resourcesProvider);
                {
                    clipPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
                    counterText.setCallback(this);
                }
                @Override
                protected boolean verifyDrawable(@NonNull Drawable who) {
                    return counterText == who || super.verifyDrawable(who);
                }

                @Override
                protected void dispatchDraw(@NonNull Canvas canvas) {
                    final float counterAlpha = counterText.isNotEmpty();
                    final boolean counterVisible = counterAlpha > 0.0f;
                    final float counterScale = lerp(0.5f, 1.0f, counterAlpha) * countScale;
                    final float R = dp(10f), r = dp(8.33f);
                    final float cx = getWidth() / 2f + dp(12);
                    final float cy = dp(22 - 10);
                    final float w = Math.max(r + r, counterText.getCurrentWidth() + dp(10));
                    final float typingT = typingAlpha.set(typing);
                    final View icon = getChildCount() > 0 ? getChildAt(0) : null;
                    final boolean typingVisible = typingT > 0f && icon != null;
                    final float th = dp(11), tx = icon == null ? 0 : icon.getRight() - dp(1), ty = icon == null ? 0 : icon.getBottom() - th / 2f + dp(1);

                    if (counterVisible || typingVisible) {
                        canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), 0xFF, Canvas.ALL_SAVE_FLAG);
                    }
                    super.dispatchDraw(canvas);
                    if (counterVisible) {
                        AndroidUtilities.rectTmp.set(cx - w / 2f - dp(1.33f), cy - R, cx + w / 2f + dp(1.33f), cy + R);
                        AndroidUtilities.scaleRect(AndroidUtilities.rectTmp, counterAlpha);
                        canvas.drawRoundRect(AndroidUtilities.rectTmp, R * counterAlpha, R * counterAlpha, clipPaint);
                    }
                    if (typingVisible) {
                        clearTypingBadge(canvas, clipPaint, typingRect, tx, ty, th, dp(1.33f), typingT);
                    }
                    if (counterVisible || typingVisible) {
                        canvas.restore();
                    }
                    if (typingVisible) {
                        drawTypingBadge(canvas, typingPaint, typingRect, tx, ty, th, typingT, muted, resourcesProvider);
                        invalidate();
                    }

                    if (counterAlpha > 0.0f) {
                        canvas.save();
                        canvas.scale(counterScale, counterScale, cx, cy);
                        AndroidUtilities.rectTmp.set(cx - w / 2f, cy - r, cx + w / 2f, cy + r);
                        canvas.drawRoundRect(AndroidUtilities.rectTmp, r, r, backgroundPaint.setByKey(counterBackgroundColorKey, counterAlpha));
                        counterText.setBounds(AndroidUtilities.rectTmp);
                        counterText.setAlpha((int) (0xFF * counterAlpha));
                        counterText.draw(canvas);
                        canvas.restore();
                    }
                }
            };
            imageLayoutView.setWillNotDraw(false);
            imageLayoutView.setPadding(0, dp(4), 0, 0);
            typingAlpha = new AnimatedFloat(imageLayoutView, 0, 260, CubicBezierInterpolator.EASE_OUT_QUINT);
            layout.addView(imageLayoutView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

            imageView = new BackupImageView(context);
            imageLayoutView.addView(imageView, imageViewParams = LayoutHelper.createFrame(34, 34, Gravity.CENTER));
            avatarDrawable = new AvatarDrawable();

            textView = new TextView(context);
            textView.setTextColor(ColorUtils.blendARGB(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider), Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider), selectT));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10);
            textView.setGravity(Gravity.CENTER);
            textView.setTypeface(AndroidUtilities.bold());
            textView.setMaxLines(3);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            layout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 4, 0, 4, 0));
            layout.setPadding(0, 0, 0, dp(4));
            setWillNotDraw(false);
        }

        private boolean mono = false;
        private void setLayout(boolean mono) {
            if (this.mono == mono) return;
            this.mono = mono;
            imageView.setRoundRadius(dp(mono ? 36 : 3));
            imageLayoutView.setPadding(0, dp(mono ? 7 : 4), 0, 0);
            imageViewParams.width = mono ? dp(28) : dp(30);
            imageViewParams.height = mono ? dp(28) : dp(30);
        }

        private boolean pinned = false;
        private void setPinned(boolean pinned, boolean animated) {
            if (this.pinned != pinned) {
                this.pinned = pinned;
//                final int overlay = Theme.getColor(Theme.key_chats_pinnedOverlay, resourcesProvider);
//                if (Theme.isCurrentThemeDark()) {
//                    setBackgroundColor(pinned ? overlay : 0);
//                } else {
//                    setBackgroundColor(pinned ? Theme.blendOver(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider), overlay) : 0);
//                }
            }
        }

        private int counterBackgroundColorKey = Theme.key_chats_unreadCounter;
        private CharSequence mentionString;
        private CharSequence reactionString;
        private int lastUnread;
        private boolean lastMuted, lastMention, lastReactions;
        private void setCounter(boolean muted, int unread, boolean mention, boolean reactions, boolean animated) {
            this.muted = muted;
            if (reactions) {
                counterBackgroundColorKey = Theme.key_dialogReactionMentionBackground;
                if (reactionString == null) {
                    final SpannableStringBuilder sb = new SpannableStringBuilder("❤️");
                    final ColoredImageSpan span = new ColoredImageSpan(R.drawable.mini_like_filled);
                    span.setScale(0.8f, 0.8f);
                    span.spaceScaleX = 0.5f;
                    span.translate(-dp(3), 0);
                    sb.setSpan(span, 0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    reactionString = sb;
                }
                counterText.setText(reactionString, animated);
            } else if (mention) {
                counterBackgroundColorKey = muted ? Theme.key_chats_unreadCounterMuted : Theme.key_chats_unreadCounter;
                if (mentionString == null) {
                    final SpannableStringBuilder sb = new SpannableStringBuilder("@");
                    final ColoredImageSpan span = new ColoredImageSpan(R.drawable.mini_mention_filled_16);
                    span.setScale(0.8f, 0.8f);
                    span.spaceScaleX = 0.5f;
                    span.translate(-dp(3), 0);
                    sb.setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    mentionString = sb;
                }
                counterText.setText(mentionString, animated);
            } else if (unread > 0) {
                counterBackgroundColorKey = muted ? Theme.key_chats_unreadCounterMuted : Theme.key_chats_unreadCounter;
                counterText.setText(LocaleController.formatNumber(unread, ','), animated);
            } else {
                counterBackgroundColorKey = Theme.key_chats_unreadCounterMuted;
                counterText.setText("", animated);
            }
            if (animated && (lastUnread < unread || !lastMention && mention || !lastReactions && reactions)) {
                animateCounterBounce();
            }
            lastUnread = unread;
            lastMention = mention;
            lastReactions = reactions;
            imageLayoutView.invalidate();
        }

        private float countScale = 1;
        private ValueAnimator counterAnimator;
        private void animateCounterBounce() {
            if (counterAnimator != null) {
                counterAnimator.cancel();
                counterAnimator = null;
            }

            counterAnimator = ValueAnimator.ofFloat(0, 1);
            counterAnimator.addUpdateListener(anm -> {
                countScale = Math.max(1, (float) anm.getAnimatedValue());
                imageLayoutView.invalidate();
            });
            counterAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    countScale = 1;
                    imageLayoutView.invalidate();
                }
            });
            counterAnimator.setInterpolator(new OvershootInterpolator(2.0f));
            counterAnimator.setDuration(200);
            counterAnimator.start();
        }

        public void setAll(boolean bot, boolean mono, boolean selected) {
            setLayout(mono);
            this.topicId = -1;
            this.lensId = bot ? Long.MIN_VALUE : 0;
            this.staticImage = true;
            this.isAdd = false;
            textView.setText(getString(bot ? R.string.BotForumNewTopic : R.string.AllTopicsSide));
            textView.setVisibility(bot ? GONE : VISIBLE);
            imageView.clearImage();
            imageView.setAnimatedEmojiDrawable(null);
            if (bot) {
                BotNewTopicDrawable drawable = new BotNewTopicDrawable(getContext());
                drawable.setColor(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider));
                imageView.setImageDrawable(drawable);
            } else {
                imageView.setImageResource(R.drawable.other_chats);
            }
            imageView.setScaleX(1f);
            imageView.setScaleY(1f);
            setSelected(selected);
            updateImageColor();
            updateState();
            setCounter(true, 0, false, false, false);
            setPinned(false, false);
        }

        private long topicId = 0;
        private boolean isAdd = false;
        private boolean staticImage = false;
        public void setAdd(boolean mono, boolean selected) {
            setLayout(mono);
            this.lensId = Long.MIN_VALUE;
            this.staticImage = true;
            this.isAdd = true;
            textView.setText(getString(R.string.NewTopic));
            textView.setVisibility(VISIBLE);
            imageView.clearImage();
            imageView.setAnimatedEmojiDrawable(null);
            imageView.setImageResource(R.drawable.emoji_tabs_new3);
            imageView.setScaleX(1f);
            imageView.setScaleY(1f);
            setSelected(selected);
            updateImageColor();
            updateState();
            setCounter(true, 0, false, false, false);
            setPinned(false, false);
        }

        private LoadingDrawable loadingDrawable;
        public void setLoading() {
            setLayout(false);
            this.topicId = -1;
            this.lensId = Long.MIN_VALUE;
            this.staticImage = true;
            this.isAdd = false;
            final SpannableStringBuilder sb = new SpannableStringBuilder("x");
            final LoadingSpan span = new LoadingSpan(textView, dp(38));
            span.setScaleY(0.75f);
            sb.setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            textView.setText(sb);
            textView.setVisibility(VISIBLE);
            imageView.clearImage();
            imageView.setAnimatedEmojiDrawable(null);
            if (loadingDrawable == null) {
                loadingDrawable = new LoadingDrawable(resourcesProvider);
                loadingDrawable.setRadiiDp(38);
                loadingDrawable.setCallback(imageView);
                final int textColor = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider);
                loadingDrawable.setColors(
                    Theme.multAlpha(textColor, .15f),
                    Theme.multAlpha(textColor, .5f),
                    Theme.multAlpha(textColor, .6f),
                    Theme.multAlpha(textColor, .15f)
                );
                loadingDrawable.stroke = false;
            }
            imageView.setImageDrawable(loadingDrawable);
            imageView.setScaleX(1f);
            imageView.setScaleY(1f);
            setSelected(false);
            updateImageColor();
            setCounter(true, 0, false, false, false);
            setPinned(false, false);
            updateState();
        }

        public void set(long dialogId, TLRPC.TL_forumTopic topic, boolean selected) {
            setLayout(false);
            final boolean animated = topicId == topic.id;
            this.staticImage = false;
            this.topicId = topic.id;
            this.lensId = topic.id;
            this.isAdd = false;
            textView.setText(topic.title);
            textView.setVisibility(VISIBLE);
            if (topic.id == 1) {
                this.staticImage = true;
                imageView.clearImage();
                imageView.setAnimatedEmojiDrawable(null);
                imageView.setImageResource(R.drawable.msg_filled_general);
                imageView.setScaleX(0.66f);
                imageView.setScaleY(0.66f);
            } else if (topic.icon_emoji_id != 0) {
                // The emoji the wheel and the topic's own header show, drawn the same way.
                ForumUtilities.setTopicIcon(imageView, topic, false, false, resourcesProvider);
                imageView.setScaleX(1f);
                imageView.setScaleY(1f);
            } else {
                imageView.setAnimatedEmojiDrawable(null);
                imageView.setImageDrawable(ForumUtilities.createTopicDrawable(topic, false));
                imageView.setScaleX(1f);
                imageView.setScaleY(1f);
            }
            setSelected(selected);
            updateImageColor();
            setCounter(
                MessagesController.getInstance(currentAccount).isDialogMuted(dialogId, topic.id),
                topic.unread_count,
                topic.unread_mentions_count > 0,
                topic.unread_reactions_count > 0,
                animated
            );
            setPinned(topic.pinned, animated);
            updateState();
        }

        // The topic in the puck turns from grey to ink, not to the accent: the glass marks it. New
        // topic, an action, stays in the accent.
        private int getInkColor() {
            if (isAdd) return Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider);
            return ColorUtils.blendARGB(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider), Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), selectT);
        }

        private void updateImageColor() {
            final int color = getInkColor();
            if (!staticImage) {
                imageView.setColorFilter(null);
            } else {
                imageView.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
            }
            imageView.setEmojiColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
            imageView.invalidate();
        }

        public void setMf(TLRPC.TL_forumTopic dialog, boolean selected) {
            setLayout(true);
            this.isAdd = false;
            this.staticImage = false;
            final long dialogId = DialogObject.getPeerDialogId(dialog.from_id);
            final boolean animated = dialogId == topicId;
            topicId = dialogId;
            lensId = dialogId;
            textView.setText(DialogObject.getName(dialogId));
            textView.setVisibility(VISIBLE);
            if (dialogId >= 0) {
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
                avatarDrawable.setInfo(user);
                imageView.setForUserOrChat(user, avatarDrawable);
            } else {
                TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
                avatarDrawable.setInfo(chat);
                imageView.setForUserOrChat(chat, avatarDrawable);
            }
            imageView.setScaleX(1f);
            imageView.setScaleY(1f);
            updateState();
            setSelected(selected);
            setCounter(
                false, // MessagesController.getInstance(currentAccount).isDialogMuted(dialogId, dialog.dialogId),
                dialog.unread_count,
                false,
                dialog.unread_reactions_count > 0,
                animated
            );
            setPinned(false, animated);
        }

        private float selectT;
        private boolean selected;
        private ValueAnimator selectAnimator;
        public void setSelected(boolean selected) {
            if (this.selected == selected) return;
            this.selected = selected;
            if (selectAnimator != null) {
                selectAnimator.cancel();
            }
            selectAnimator = ValueAnimator.ofFloat(selectT, selected ? 1f : 0f);
            selectAnimator.addUpdateListener(anm -> {
                selectT = (float) anm.getAnimatedValue();
                updateState();
                updateImageColor();
            });
            selectAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    selectT = selected ? 1f : 0f;
                    updateState();
                    updateImageColor();
                }
            });
            selectAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            selectAnimator.setDuration(320);
            selectAnimator.start();
        }

        private void updateState() {
            textView.setTextColor(getInkColor());
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(dp(64), MeasureSpec.EXACTLY),
                heightMeasureSpec
            );
        }

        public static class Factory extends UItem.UItemFactory<VerticalTabView> {
            static { setup(new Factory()); }

            @Override
            public VerticalTabView createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new VerticalTabView(context, currentAccount, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                final VerticalTabView cell = (VerticalTabView) view;
                if (item.red) {
                    cell.setLoading();
                } else if (item.object == null) {
                    if (item.longValue == -2) {
                        cell.setAdd(item.accent, item.checked);
                    } else {
                        cell.setAll((item.flags & 1) != 0, item.accent, item.checked);
                    }
                } else if (item.object instanceof TLRPC.TL_forumTopic) {
                    if (!item.withUsername) {
                        cell.setMf((TLRPC.TL_forumTopic) item.object, item.checked);
                    } else {
                        cell.set(item.dialogId, (TLRPC.TL_forumTopic) item.object, item.checked);
                    }
                }
                cell.setReorder(listView != null && listView.isReorderAllowed() && cell.pinned);
            }

            public static UItem asAll(boolean botforum, boolean monoforum) {
                UItem item = UItem.ofFactory(Factory.class);
                item.id = 0;
                item.longValue = 0;
                item.object = null;
                item.accent = monoforum;
                item.flags = botforum ? 1 : 0;
                return item;
            }

            public static UItem asAdd(boolean monoforum) {
                UItem item = UItem.ofFactory(Factory.class);
                item.id = -2;
                item.longValue = -2;
                item.object = null;
                item.accent = monoforum;
                return item;
            }

            public static UItem asTab(long dialogId, TLRPC.TL_forumTopic topic, boolean mono) {
                UItem item = UItem.ofFactory(Factory.class);
                item.dialogId = dialogId;
                item.id = topic.id;
                item.object = topic;
                if (mono) {
                    item.longValue = DialogObject.getPeerDialogId(topic.from_id);
                    item.withUsername = false;
                }
                return item;
            }

            public static UItem asLoading(int id) {
                UItem item = UItem.ofFactory(Factory.class);
                item.id = id;
                item.red = true;
                item.checked = false;
                return item;
            }
        }
    }

    /**
     * A topic on the bar: its icon, its name and its counter. Every topic wears its icon, the same
     * one the wheel turns: its emoji, General's #, a plain topic's coloured bubble with its initial,
     * All's chat bubbles. The icon sits in a 28 dp slot at the tab's start, in the round end of the
     * glass puck when it is the topic you are in.
     */
    public static class HorizontalTabView extends FrameLayout {

        private static final int ICON_PAD = 4, ICON_SLOT = 28, ICON_SIZE = 20, TEXT_GAP = 3;

        private final int currentAccount;
        private final Theme.ResourcesProvider resourcesProvider;

        private Shaker shaker;
        private final LinkSpanDrawable.LinksTextView textView;
        private final AnimatedTextView.AnimatedTextDrawable counterText;
        private final View counterView;
        private final ImageView imageView;
        private final BackupImageView iconView;
        private final AvatarDrawable avatarDrawable = new AvatarDrawable();
        private boolean hasIcon, staticIcon;

        private boolean reorder;
        public void setReorder(boolean value) {
            this.reorder = value;
            invalidate();
        }

        /** Someone is typing in the topic: its badge shows at the icon's lower corner (see drawTypingBadge). */
        private boolean typing, muted;
        private final AnimatedFloat typingAlpha = new AnimatedFloat(this, 0, 260, CubicBezierInterpolator.EASE_OUT_QUINT);
        private final Paint typingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint typingClearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF typingRect = new RectF();
        {
            typingClearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        }

        public void setTyping(boolean typing) {
            if (this.typing == typing) return;
            this.typing = typing;
            invalidate();
        }

        @Override
        protected void dispatchDraw(@NonNull Canvas canvas) {
            final float typingT = typingAlpha.set(typing && hasIcon);
            if (typingT <= 0f) {
                super.dispatchDraw(canvas);
                return;
            }
            final float h = dp(9.5f), cx = iconView.getRight() - dp(0.5f), cy = iconView.getBottom() - dp(1.5f);
            canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), 0xFF, Canvas.ALL_SAVE_FLAG);
            super.dispatchDraw(canvas);
            clearTypingBadge(canvas, typingClearPaint, typingRect, cx, cy, h, dp(1.2f), typingT);
            canvas.restore();
            drawTypingBadge(canvas, typingPaint, typingRect, cx, cy, h, typingT, muted, resourcesProvider);
            invalidate();
        }

        private final AnimatedFloat shakeAlpha = new AnimatedFloat(this, 360, CubicBezierInterpolator.EASE_OUT_QUINT);
        @Override
        protected boolean drawChild(@NonNull Canvas canvas, View child, long drawingTime) {
            if (child == textView || child == iconView) {
                canvas.save();
                final float shakeAlpha = this.shakeAlpha.set(reorder);
                if (shakeAlpha > 0) {
                    if (shaker == null) shaker = new Shaker(this);
                    canvas.translate(getWidth() / 2f, getHeight() / 2f);
                    shaker.concat(canvas, shakeAlpha);
                    canvas.translate(-getWidth() / 2f, -getHeight() / 2f);
                }
                if (child == iconView) {
                    // The icon under the lens is a little magnified, like the one at the wheel's lens.
                    final float scale = iconBaseScale * (1f + 0.06f * selectT);
                    canvas.scale(scale, scale, child.getX() + child.getWidth() / 2f, child.getY() + child.getHeight() / 2f);
                }
                boolean r = super.drawChild(canvas, child, drawingTime);
                canvas.restore();
                return r;
            }
            return super.drawChild(canvas, child, drawingTime);
        }

        public HorizontalTabView(Context context, int currentAccount, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.currentAccount = currentAccount;
            this.resourcesProvider = resourcesProvider;
            setClipChildren(false);
            setClipToPadding(false);
            setWillNotDraw(false);

            iconView = new BackupImageView(context);
            // The tab sinks as one into the drop of glass under your finger (see PressDrop).
            addView(iconView, LayoutHelper.createFrame(ICON_SIZE, ICON_SIZE, Gravity.LEFT | Gravity.CENTER_VERTICAL));

            textView = new LinkSpanDrawable.LinksTextView(context, resourcesProvider);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f);
            textView.setTypeface(AndroidUtilities.bold());
            addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 11, 0, 11, 0));

            imageView = new ImageView(context);
            addView(imageView, LayoutHelper.createFrame(34, 34, Gravity.CENTER));
            counterText = new AnimatedTextView.AnimatedTextDrawable();
            counterText.setTextSize(dp(11));
            counterText.setTypeface(AndroidUtilities.bold());
            counterText.setOverrideFullWidth(AndroidUtilities.displaySize.x);
            counterText.setGravity(Gravity.CENTER);
            counterView = new View(context) {
                private final AnimatedPaint backgroundPaint = new AnimatedPaint(this, resourcesProvider);
                {
                    counterText.setCallback(this);
                }
                @Override
                protected boolean verifyDrawable(@NonNull Drawable who) {
                    return counterText == who || super.verifyDrawable(who);
                }

                @Override
                protected void dispatchDraw(@NonNull Canvas canvas) {
                    final float counterAlpha = counterText.isNotEmpty();
                    if (counterAlpha > 0) {
                        final float counterScale = lerp(.6f, 1.0f, counterAlpha);
                        final float width = Math.max(dp(16.66f), counterText.getCurrentWidth() + dp(10));
                        AndroidUtilities.rectTmp.set(0, 0, width, getHeight());
                        canvas.save();
                        canvas.scale(counterScale, counterScale, AndroidUtilities.rectTmp.centerX(), AndroidUtilities.rectTmp.centerY());
                        canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(8.33f), dp(8.33f), backgroundPaint.setByKey(counterBackgroundColorKey, counterAlpha));
                        // canvas.translate(0, -dp(1));
                        counterText.setBounds(AndroidUtilities.rectTmp);
                        counterText.setAlpha((int) (0xFF * counterAlpha));
                        counterText.setTextColor(Theme.getColor(Theme.key_chats_unreadCounterText, resourcesProvider));
                        counterText.draw(canvas);
                        canvas.restore();
                    }
                    super.dispatchDraw(canvas);
                }

                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    final float width = Math.max(dp(16.66f), counterText.getAnimateToWidth() + dp(10));
                    super.onMeasure(
                        MeasureSpec.makeMeasureSpec((int) width, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(dp(16.66f), MeasureSpec.EXACTLY)
                    );
                }
            };
            addView(counterView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 4.66f, 0, 11, 0));

            updateTextColor();
        }

        private boolean pinned = false;
        private void setPinned(boolean pinned, boolean animated) {
            if (this.pinned != pinned) {
                this.pinned = pinned;
//                final int overlay = Theme.getColor(Theme.key_chats_pinnedOverlay, resourcesProvider);
//                if (Theme.isCurrentThemeDark()) {
//                    setBackgroundColor(pinned ? overlay : 0);
//                } else {
//                    setBackgroundColor(pinned ? Theme.blendOver(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider), overlay) : 0);
//                }
            }
        }

        /** How wide the lens is around this tab: all of it but the room left for the pin. */
        public float getLensWidth() {
            return getWidth() - addW;
        }

        /** Where the middle of the icon is, from the tab's left: in the lens's round end. */
        public float getIconCenterX() {
            return hasIcon ? dp(ICON_PAD + ICON_SLOT / 2f) : getWidth() / 2f;
        }

        public boolean hasIcon() {
            return hasIcon;
        }

        private int getTextLeft() {
            return hasIcon ? dp(ICON_PAD + ICON_SLOT + TEXT_GAP) : dp(11);
        }

        private int getTextRight() {
            return hasIcon ? dp(10) : dp(11);
        }

        int counterViewX;
        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            final int w = right - left;
            final int h = bottom - top;

            final int ix = (w - imageView.getMeasuredWidth()) / 2;
            final int iy = (h - imageView.getMeasuredHeight()) / 2;
            imageView.layout(ix, iy, ix + imageView.getMeasuredWidth(), iy + imageView.getMeasuredHeight());
            final int iconLeft = dp(ICON_PAD) + (dp(ICON_SLOT) - iconView.getMeasuredWidth()) / 2;
            final int iconTop = (h - iconView.getMeasuredHeight()) / 2;
            iconView.layout(iconLeft, iconTop, iconLeft + iconView.getMeasuredWidth(), iconTop + iconView.getMeasuredHeight());
            final int textLeft = getTextLeft();
            textView.layout(textLeft, h / 2 - textView.getMeasuredHeight() / 2, textLeft + textView.getMeasuredWidth(), h / 2 + textView.getMeasuredHeight() / 2);
            if (counterText.getAnimateToWidth() > 0) {
                counterView.layout(w - getTextRight() - counterView.getMeasuredWidth(), h / 2 - counterView.getMeasuredHeight() / 2, w - getTextRight(), h / 2 + counterView.getMeasuredHeight() / 2);
            } else {
                final int cl = textLeft + textView.getMeasuredWidth() + dp(5);
                counterView.layout(cl, h / 2 - counterView.getMeasuredHeight() / 2, cl + counterView.getMeasuredWidth(), h / 2 + counterView.getMeasuredHeight() / 2);
            }
            if (counterViewX != 0 && counterView.getLeft() != counterViewX) {
                counterView.setTranslationX(-counterView.getLeft() + counterViewX);
                counterView.animate().translationX(0f).setDuration(320).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
            }
            counterViewX = counterView.getLeft();
        }

        private long topicId;
        private boolean isAdd = false;
        private boolean mono = false;
        private void setLayout(boolean mono) {
            if (this.mono == mono) return;
            this.mono = mono;
//            imageView.setRoundRadius(dp(mono ? 36 : 0));
//            imageViewParams.topMargin = mono ? dp(7) : dp(4);
//            imageViewParams.width = mono ? dp(28) : dp(36);
//            imageViewParams.height = mono ? dp(28) : dp(36);
        }

        public long getTopicId() {
            return topicId;
        }

        private void setIcon(boolean show) {
            hasIcon = show;
            iconView.setVisibility(show ? VISIBLE : GONE);
            if (!show) {
                iconView.setAnimatedEmojiDrawable(null);
                iconView.clearImage();
            }
            requestLayout();
        }

        // A glyph that takes the tab's colour: All's chat bubbles, General's #.
        private void setStaticIcon(@DrawableRes int res, float scale) {
            setIcon(true);
            staticIcon = true;
            iconView.setAnimatedEmojiDrawable(null);
            iconView.setRoundRadius(0);
            iconView.setImageResource(res);
            iconBaseScale = scale;
            updateTextColor();
        }

        private float iconBaseScale = 1f;

        private boolean staticImage = false;
        public void setAll(boolean bot, boolean mono, boolean selected) {
            setLayout(mono);
            this.topicId = 0;
            this.isAdd = false;
            this.staticImage = true;
            imageView.setVisibility(bot ? VISIBLE : GONE);
            if (bot) {
                setIcon(false);
                BotNewTopicDrawable drawable = new BotNewTopicDrawable(getContext());
                drawable.setColor(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider));
                imageView.setImageDrawable(drawable);
            } else {
                setStaticIcon(R.drawable.other_chats, 1f);
            }

            textView.setText(getString(bot ? R.string.BotForumNewTopic : R.string.AllTopicsShort));
            textView.setVisibility(bot ? GONE : VISIBLE);
            setContentDescription(bot ? getString(R.string.BotForumNewTopic) : null);
            setSelected(selected);
            updateTextColor();
            setCounter(true, 0, false, false, false);
            setPinned(false, false);
        }

        public void setAdd() {
            setLayout(false);
            this.topicId = 0;
            this.isAdd = true;
            this.staticImage = false;
            this.imageView.setVisibility(GONE);
            setIcon(false);
            this.textView.setVisibility(VISIBLE);
            SpannableStringBuilder sb = new SpannableStringBuilder("e\u200B");
            sb.setSpan(new ColoredImageSpan(R.drawable.menu_topic_add), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            textView.setText(sb);
            setContentDescription(getString(R.string.NewTopic));
            setSelected(false);
            updateTextColor();
            setCounter(true, 0, false, false, false);
            setPinned(false, false);
        }

        public void setLoading() {
            setLayout(false);
            this.topicId = -1;
            this.staticImage = true;
            this.imageView.setVisibility(GONE);
            setIcon(false);
            this.textView.setVisibility(VISIBLE);
            final SpannableStringBuilder sb = new SpannableStringBuilder("x");
            final LoadingSpan span = new LoadingSpan(textView, dp(42));
            span.setScaleY(.95f);
            sb.setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            textView.setText(sb);
            setContentDescription(null);
            setSelected(false);
            updateTextColor();
            setCounter(true, 0, false, false, false);
            setPinned(false, false);
        }

        public void set(long dialogId, TLRPC.TL_forumTopic topic, boolean selected) {
            setLayout(false);
            final boolean animated = this.topicId == topic.id;
            this.topicId = topic.id;
            this.staticImage = false;
            this.imageView.setVisibility(GONE);
            this.textView.setVisibility(VISIBLE);
            if (topic.id == 1) {
                setStaticIcon(R.drawable.msg_filled_general, 0.8f);
            } else if (topic.icon_emoji_id != 0) {
                setIcon(true);
                staticIcon = false;
                iconView.setRoundRadius(0);
                // The emoji the wheel and the topic's own header show, drawn the same way.
                ForumUtilities.setTopicIcon(iconView, topic, false, false, resourcesProvider);
                iconBaseScale = 1f;
            } else {
                setIcon(true);
                staticIcon = false;
                iconView.setRoundRadius(0);
                iconView.setAnimatedEmojiDrawable(null);
                iconView.setImageDrawable(ForumUtilities.createTopicDrawable(topic, false));
                iconBaseScale = 1f;
            }
            textView.setText(topic.hidden ? "" : Emoji.replaceEmoji(topic.title, textView.getPaint().getFontMetricsInt(), false));
            // A hidden General shows only its icon; TalkBack still reads its name.
            setContentDescription(topic.hidden ? topic.title : null);
            setSelected(selected);
            updateTextColor();
            setCounter(
                MessagesController.getInstance(currentAccount).isDialogMuted(dialogId, topicId),
                topic.unread_count,
                topic.unread_mentions_count > 0,
                topic.unread_reactions_count > 0,
                animated
            );
            setPinned(topic.pinned, animated);
        }

        private void updateTextColor() {
            final int color = getTextColor();
            textView.setTextColor(color);
            textView.setEmojiColor(color);
            if (staticIcon) {
                iconView.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
            } else {
                iconView.setColorFilter(null);
            }
            iconView.setEmojiColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
            iconView.invalidate();
            counterView.invalidate();
            invalidate();
        }

        // The topic in the puck turns from grey to ink, not to the accent: the glass marks it. New
        // topic, an action, stays in the accent.
        private int getTextColor() {
            if (isAdd) return Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider);
            return ColorUtils.blendARGB(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider), Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), selectT);
        }

        public void setMf(long chatDialogId, TLRPC.TL_forumTopic dialog, boolean selected) {
            setLayout(true);
            final long dialogId = DialogObject.getPeerDialogId(dialog.from_id);
            final boolean animated = this.topicId == dialogId;
            this.topicId = dialogId;
            this.staticImage = false;
            this.imageView.setVisibility(GONE);
            this.textView.setVisibility(VISIBLE);
            final TLObject object = MessagesController.getInstance(currentAccount).getUserOrChat(dialogId);
            setIcon(true);
            staticIcon = false;
            iconView.setAnimatedEmojiDrawable(null);
            iconView.setRoundRadius(dp(ICON_SIZE) / 2);
            avatarDrawable.setInfo(currentAccount, object);
            iconView.setForUserOrChat(object, avatarDrawable);
            iconBaseScale = 1f;
            textView.setText(TextUtils.ellipsize(DialogObject.getName(dialogId), textView.getPaint(), dp(150), TextUtils.TruncateAt.END));
            setContentDescription(null);
            setSelected(selected);
            updateTextColor();
            setCounter(
                MessagesController.getInstance(currentAccount).isDialogMuted(chatDialogId, dialogId),
                dialog.unread_count,
                false,
                false, // dialog.unread_reactions_count > 0,
                animated
            );
            setPinned(false, animated);
        }

        private float selectT;
        private boolean selected;
        private ValueAnimator selectAnimator;
        public void setSelected(boolean selected) {
            if (this.selected == selected) return;
            this.selected = selected;
            if (selectAnimator != null) {
                selectAnimator.cancel();
            }
            selectAnimator = ValueAnimator.ofFloat(selectT, selected ? 1f : 0f);
            selectAnimator.addUpdateListener(anm -> {
                selectT = (float) anm.getAnimatedValue();
                updateTextColor();
            });
            selectAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    selectT = selected ? 1f : 0f;
                    updateTextColor();
                }
            });
            selectAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            selectAnimator.setDuration(320);
            selectAnimator.start();
            invalidate();
        }

        private int counterBackgroundColorKey = Theme.key_chats_unreadCounter;
        private CharSequence mentionString;
        private CharSequence reactionString;
        private int lastUnread;
        private boolean lastMuted, lastMention, lastReactions;
        private void setCounter(boolean muted, int unread, boolean mention, boolean reactions, boolean animated) {
            this.muted = muted;
            if (reactions) {
                counterBackgroundColorKey = Theme.key_dialogReactionMentionBackground;
                if (reactionString == null) {
                    final SpannableStringBuilder sb = new SpannableStringBuilder("❤️");
                    final ColoredImageSpan span = new ColoredImageSpan(R.drawable.mini_like_filled);
                    span.setScale(0.8f, 0.8f);
                    span.spaceScaleX = 0.5f;
                    span.translate(-dp(3), 0);
                    sb.setSpan(span, 0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    reactionString = sb;
                }
                counterText.setText(reactionString, animated);
            } else if (mention) {
                counterBackgroundColorKey = muted ? Theme.key_chats_unreadCounterMuted : Theme.key_chats_unreadCounter;
                if (mentionString == null) {
                    final SpannableStringBuilder sb = new SpannableStringBuilder("@");
                    final ColoredImageSpan span = new ColoredImageSpan(R.drawable.mini_mention_filled_16);
                    span.setScale(0.8f, 0.8f);
                    span.spaceScaleX = 0.5f;
                    span.translate(-dp(3), 0);
                    sb.setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    mentionString = sb;
                }
                counterText.setText(mentionString, animated);
            } else if (unread > 0) {
                counterBackgroundColorKey = muted ? Theme.key_chats_unreadCounterMuted : Theme.key_chats_unreadCounter;
                counterText.setText(LocaleController.formatNumber(unread, ','), animated);
            } else {
                counterBackgroundColorKey = Theme.key_chats_unreadCounterMuted;
                counterText.setText("", animated);
            }
            if (animated && (lastUnread < unread || !lastMention && mention || !lastReactions && reactions)) {
                animateCounterBounce();
            }
            lastUnread = unread;
            lastMention = mention;
            lastReactions = reactions;
            counterView.invalidate();
            if (getMeasuringWidth() != getMeasuredWidth()) {
                requestLayout();
            }
        }

        private ValueAnimator counterAnimator;
        private void animateCounterBounce() {
            if (counterAnimator != null) {
                counterAnimator.cancel();
                counterAnimator = null;
            }

            counterAnimator = ValueAnimator.ofFloat(0, 1);
            counterAnimator.addUpdateListener(anm -> {
                counterView.setScaleX(Math.max(1, (float) anm.getAnimatedValue()));
                counterView.setScaleY(Math.max(1, (float) anm.getAnimatedValue()));
                counterView.invalidate();
            });
            counterAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    counterView.setScaleX(1f);
                    counterView.setScaleY(1f);
                    counterView.invalidate();
                }
            });
            counterAnimator.setInterpolator(new OvershootInterpolator(2.0f));
            counterAnimator.setDuration(200);
            counterAnimator.start();
        }

        private int addW = 0;

        private int getMeasuringWidth() {
            final int counterWidth = (int) Math.max(dp(16.66f), counterText.getAnimateToWidth() + dp(10));
            final int text = textView.getMeasuredWidth();
            return getTextLeft() + text + (counterText.getAnimateToWidth() > 0 ? (text > 0 ? dp(5) : 0) + counterWidth : 0) + (text > 0 || counterText.getAnimateToWidth() > 0 ? getTextRight() : dp(ICON_PAD) - dp(TEXT_GAP)) + addW;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            textView.measure(widthMeasureSpec, heightMeasureSpec);
            iconView.measure(MeasureSpec.makeMeasureSpec(dp(ICON_SIZE), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(ICON_SIZE), MeasureSpec.EXACTLY));
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(getMeasuringWidth(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(dp(TOP_TABS_HEIGHT), MeasureSpec.EXACTLY)
            );
        }

        public static class Factory extends UItem.UItemFactory<HorizontalTabView> {
            static { setup(new Factory()); }

            @Override
            public HorizontalTabView createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new HorizontalTabView(context, currentAccount, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                HorizontalTabView cell = (HorizontalTabView) view;
                if (item.red) {
                    cell.setLoading();
                } else if (item.object == null) {
                    if (item.id == -2) {
                        cell.setAdd();
                    } else {
                        cell.setAll((item.flags & 1) != 0, item.accent, item.checked);
                    }
                } else if (item.object instanceof TLRPC.TL_forumTopic) {
                    if (!item.withUsername) {
                        cell.setMf(item.dialogId, (TLRPC.TL_forumTopic) item.object, item.checked);
                    } else {
                        cell.set(item.dialogId, (TLRPC.TL_forumTopic) item.object, item.checked);
                    }
                }
                cell.addW = BitwiseUtils.hasFlag(item.flags, 8) ? dp(10) : 0;
                cell.setReorder(listView != null && listView.isReorderAllowed() && cell.pinned);
            }

            public static UItem asAll(boolean botforum, boolean monoforum) {
                UItem item = UItem.ofFactory(Factory.class);
                item.id = 0;
                item.longValue = 0;
                item.object = null;
                item.accent = monoforum;
                // item.flags = botforum ? 1 : 0;
                return item;
            }

            public static UItem asTab(long dialogId, TLRPC.TL_forumTopic topic, boolean mono) {
                UItem item = UItem.ofFactory(Factory.class);
                item.dialogId = dialogId;
                item.id = topic.id;
                item.object = topic;
                if (mono) {
                    item.longValue = DialogObject.getPeerDialogId(topic.from_id);
                    item.withUsername = false;
                }
                return item;
            }

            public static UItem asLoading(int id) {
                UItem item = UItem.ofFactory(Factory.class);
                item.id = id;
                item.red = true;
                return item;
            }

            public static UItem asAdd() {
                UItem item = UItem.ofFactory(Factory.class);
                item.id = -2;
                item.longValue = -2;
                item.object = null;
                return item;
            }
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        checkUi_topicsVerticalPosition();
    }

    private boolean allTopicsHidden;

    public void setAllTopicsHidden(boolean allTopicsHidden) {
        if (this.allTopicsHidden != allTopicsHidden) {
            this.allTopicsHidden = allTopicsHidden;
            checkTopicsVisibility(true);
        }
    }


    private final HashSet<Integer> excludeTopics = new HashSet<>();
    private void deleteTopics(HashSet<Integer> selectedTopics, Runnable runnable) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(LocaleController.getPluralString("DeleteTopics", selectedTopics.size()));
        ArrayList<Integer> topicsToRemove = new ArrayList<>(selectedTopics);
        final long currentTopic = currentTopicId;
        if (selectedTopics.size() == 1) {
            TLRPC.TL_forumTopic topic = MessagesController.getInstance(currentAccount).getTopicsController().findTopic(-dialogId, topicsToRemove.get(0));
            builder.setMessage(formatString(R.string.DeleteSelectedTopic, topic.title));
        } else {
            builder.setMessage(getString(R.string.DeleteSelectedTopics));
        }
        builder.setPositiveButton(LocaleController.getString(R.string.Delete), (dialog, which) -> {
            for (int topicId : topicsToRemove) {
                if (currentTopic == topicId) {
                    selectTopic(0, false);
                }
            }

            excludeTopics.addAll(selectedTopics);
            updateTabs();
            BulletinFactory.of(fragment).createUndoBulletin(LocaleController.getPluralString("TopicsDeleted", selectedTopics.size()), () -> {
                excludeTopics.removeAll(selectedTopics);
                updateTabs();
                for (int topicId : topicsToRemove) {
                    if (currentTopic == topicId) {
                        selectTopic(topicId, false);
                        break;
                    }
                }
            }, () -> {
                MessagesController.getInstance(currentAccount).getTopicsController().deleteTopics(-dialogId, topicsToRemove);
                runnable.run();
            }).show();
            dialog.dismiss();
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), (dialog, which) -> {
            dialog.dismiss();
        });
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
        TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (button != null) {
            button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
        }
    }

    private long getTopicId(TLRPC.TL_forumTopic topic) {
        return mono ? DialogObject.getPeerDialogId(topic.from_id) : topic.id;
    }

    private static class BotNewTopicDrawable extends Drawable {
        private final Drawable drawable;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rectF = new RectF();

        public BotNewTopicDrawable(Context context) {
            drawable = context.getResources().getDrawable(R.drawable.menu_topic_add).mutate();
        }

        public void setColor(int color) {
            paint.setColor(color);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            canvas.drawRoundRect(rectF, dp(10), dp(10), paint);
            drawable.draw(canvas);
        }

        @Override
        protected void onBoundsChange(@NonNull Rect bounds) {
            super.onBoundsChange(bounds);
            rectF.set(bounds);

            final int x = bounds.centerX() - dp(12);
            final int y = bounds.centerY() - dp(12);

            drawable.setBounds(x, y, x + dp(24), y + dp(24));
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
            drawable.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {

        }

        @Override
        public int getOpacity() {
            return PixelFormat.UNKNOWN;
        }
    }




    /**
     * The button at the far end of the tabs. While the bar keeps its place it winds the bar up into
     * the wheel (two chevrons pointing back to where the wheel will sit); while the bar is rolled
     * out over the chat it reads "Keep open". At the end of the top bar it is wider than the
     * chevrons so the words fit; touches beside what it shows go to the tabs under it.
     */
    private static class RollButton extends View {
        private final boolean vertical;
        private final Theme.ResourcesProvider resourcesProvider;
        private final AnimatedFloat animatedKeep = new AnimatedFloat(this, 0, 420, CubicBezierInterpolator.EASE_OUT_QUINT);
        private final Paint chevronPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint buttonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path chevronPath = new Path();
        private final RectF chip = new RectF();
        private final Text keepText;
        private boolean keep;
        private float lastKeepProgress = -1;
        Runnable onKeepProgress;

        RollButton(Context context, boolean vertical, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.vertical = vertical;
            this.resourcesProvider = resourcesProvider;
            chevronPaint.setStyle(Paint.Style.STROKE);
            chevronPaint.setStrokeCap(Paint.Cap.ROUND);
            chevronPaint.setStrokeJoin(Paint.Join.ROUND);
            chevronPaint.setStrokeWidth(dp(1.66f));
            keepText = new Text(getString(R.string.TopicsRollKeepOpen), vertical ? 10 : 12.5f, AndroidUtilities.bold());
            setContentDescription(getString(R.string.TopicsRollUp));
        }

        void setKeep(boolean keep, boolean animated) {
            this.keep = keep;
            if (!animated) {
                animatedKeep.force(keep);
            }
            setContentDescription(getString(keep ? R.string.TopicsRollKeepOpen : R.string.TopicsRollUp));
            invalidate();
        }

        /** 0 while it shows the chevrons, 1 while it reads "Keep open". */
        float getKeepProgress() {
            return animatedKeep.get();
        }

        /** Where "Keep open" starts, in this view. */
        float getKeepLeft() {
            return getWidth() - dp(5) - getChipWidth() + dp(10) * (1f - animatedKeep.get());
        }

        private float getChipWidth() {
            return dp(11) + keepText.getWidth() + dp(11);
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent ev) {
            if (!vertical && ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
                final float active = keep ? getChipWidth() + dp(8) : dp(ROLL_BUTTON_TOP_WIDTH);
                if (ev.getX() < getWidth() - active) {
                    return false;
                }
            }
            return super.dispatchTouchEvent(ev);
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            final float t = animatedKeep.set(keep);
            if (t != lastKeepProgress) {
                lastKeepProgress = t;
                if (onKeepProgress != null) {
                    onKeepProgress.run();
                }
            }
            final int accent = Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider);
            final float w = getWidth(), h = getHeight();

            if (t < 1f) {
                // Two chevrons pointing back to where the wheel will sit.
                final float cx = vertical ? w / 2f : w - dp(ROLL_BUTTON_TOP_WIDTH) / 2f, cy = h / 2f;
                chevronPaint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider), 1f - t));
                final float s = dp(3.66f), scale = lerp(1f, 0.5f, t);
                chevronPath.rewind();
                for (int i = 0; i < 2; ++i) {
                    final float ox = i == 0 ? -dp(2.33f) : dp(2.33f);
                    chevronPath.moveTo(ox + s * 0.55f, -s);
                    chevronPath.lineTo(ox - s * 0.55f, 0);
                    chevronPath.lineTo(ox + s * 0.55f, s);
                }
                canvas.save();
                canvas.translate(cx, cy);
                canvas.rotate(vertical ? 90 : 0);
                canvas.scale(scale, scale);
                canvas.drawPath(chevronPath, chevronPaint);
                canvas.restore();
            }

            if (t > 0f) {
                // A button in the accent colour, so it reads as something to press, never as the topic
                // you are in (that one sits on glass).
                final int onAccent = Theme.getColor(Theme.key_featuredStickers_buttonText, resourcesProvider);
                chevronPaint.setColor(Theme.multAlpha(onAccent, t));
                if (vertical) {
                    // A check on a button with the words under it, like the rail's own tabs.
                    final float cx = w / 2f, cy = h / 2f - dp(7);
                    chip.set(cx - dp(18), cy - dp(11), cx + dp(18), cy + dp(11));
                    drawButton(canvas, chip, dp(11), accent, t);
                    chevronPath.rewind();
                    chevronPath.moveTo(cx - dp(5), cy + dp(0.5f));
                    chevronPath.lineTo(cx - dp(1.5f), cy + dp(4));
                    chevronPath.lineTo(cx + dp(5.5f), cy - dp(3.5f));
                    canvas.drawPath(chevronPath, chevronPaint);
                    keepText.draw(canvas, cx - keepText.getWidth() / 2f, cy + dp(11 + 8), accent, t);
                } else {
                    final float left = getKeepLeft();
                    chip.set(left, h / 2f - dp(13), left + getChipWidth(), h / 2f + dp(13));
                    drawButton(canvas, chip, dp(13), accent, t);
                    keepText.draw(canvas, chip.left + dp(11), chip.centerY(), onAccent, t);
                }
            }
        }

        // A filled button with a soft shadow of its own colour under it.
        private void drawButton(Canvas canvas, RectF rect, float radius, int color, float alpha) {
            buttonPaint.setColor(Theme.multAlpha(color, alpha * 0.3f));
            rect.offset(0, dp(1));
            canvas.drawRoundRect(rect, radius, radius, buttonPaint);
            rect.offset(0, -dp(1));
            buttonPaint.setColor(Theme.multAlpha(color, alpha));
            canvas.drawRoundRect(rect, radius, radius, buttonPaint);
        }
    }

    public enum Position {
        TOP, LEFT, BOTTOM
    }

    private static int getTabsSize(Position position) {
        return dp(position == Position.LEFT ? SIDE_TABS_WIDTH : TOP_TABS_HEIGHT);
    }

    public float getSideMenuT() {
        return sidemenuT * animatorTopicsVisibility.getFloatValue() * getReserveT();
    }

    public boolean isSideMenuEnabled() {
        return sidemenuEnabled && animatorTopicsVisibility.getValue() && !rollable;
    }


    private float getTabsVisibility(Position position) {
        final float visibility = animatorTopicsVisibility.getFloatValue();

        if (position == Position.LEFT) {
            return sidemenuT * visibility;
        }
        if (position == Position.TOP && !topicBottom || position == Position.BOTTOM && topicBottom) {
            return (1.0f - sidemenuT) * visibility;
        }
        return 0;
    }

    public Position getCurrentTabsPosition() {
        return sidemenuEnabled ? Position.LEFT : (topicBottom ? Position.BOTTOM : Position.TOP);
    }

    /**
     * The space the chat leaves for the tabs. Rolled up at the top or on the side, the bar gives its
     * space back to the chat; at the bottom it keeps its row, where its wheel turns.
     */
    public float getTabsVisibleSpaceWithPadding(Position position, float padding) {
        final float visibility = getTabsVisibility(position) * (position == Position.BOTTOM ? 1f : getReserveT());
        final int size = getTabsSize(position);
        return (size + padding) * visibility;
    }


    @Override
    public void onFactorChanged(int id, float factor, float fraction, FactorAnimator callee) {
        if (id == ANIMATOR_ID_ROLL_DISPLAY || id == ANIMATOR_ID_ISLAND_JOIN) {
            // The island changes shape; the chat's top panels follow it.
            checkUi_roll();
            if (onRollChanged != null) {
                onRollChanged.run();
            }
            return;
        }
        updateSidemenuPosition();
    }
}