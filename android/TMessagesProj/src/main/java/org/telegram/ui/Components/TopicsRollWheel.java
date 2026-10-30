package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.Forum.ForumUtilities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * The topic bar rolled up into a wheel. Topic icons sit on a drum in the bar's own order (All,
 * the pinned topics, then the rest), and the one in the middle, under the lens, is the topic you
 * are in. Swipe it and it turns, clicking at every topic and a little firmer where the pinned
 * topics end; let go and it settles on a topic and opens it. Tap it and {@link TopicsTabsView}
 * rolls the bar back out. A topic with news lights up on the wheel's scale, like a station on a
 * radio dial, and where someone is typing the light is three dots that rise and fall; news further
 * along than the wheel shows makes that end of the well glow; a badge on the well's rim counts
 * unread messages in the other topics; and the wheel nudges toward a topic that just got a message.
 * Rolled out, the bar keeps its own size and carries the same track, scale, lights and glass. At
 * the bottom the wheel stays at the bottom; for side tabs it stands up (see {@link #setVertical}).
 *
 * The view is {@link #INSET} dp larger than the wheel on every side: rolled up, that is the rim of
 * the 48 dp glass island TopicsTabsView draws behind it, and the wheel turns in a well the island
 * draws into its glass, the one that stretches into the bar's. {@link #TOP_SPACE} more dp on top
 * leave room for the badge, which rides the well's rim.
 */
@SuppressLint("ViewConstructor")
public class TopicsRollWheel extends View {

    public static final int WIDTH = 108;
    public static final int HEIGHT = 36;
    public static final int INSET = 6;
    public static final int TOP_SPACE = 5;
    /** The scale along the lower edge of a well: where its ticks and its lights are, from that edge. */
    public static final float KNURL_FROM_EDGE = 3, KNURL = 3.5f, LIGHT_FROM_EDGE = 4.75f;
    private static final float RADIUS = 46;
    private static final float CELL = 34;
    private static final float ICON = 22;
    private static final float STEP = CELL / RADIUS;
    private static final long SETTLE_MS = 420;
    private static final long NUDGE_MS = 900;
    private static final long POP_MS = 420;

    public interface Delegate {
        /** A tap: roll the bar back out. */
        void onWheelTap();
        /** The wheel settled on another topic after a swipe. Direction is +1 for later topics, -1 for earlier ones. */
        void onWheelTurned(long id, int direction);
        /** A long press on the topic in the middle. */
        void onWheelLongPress(long id);
        /** While it turns, the topic under the lens (its index); -1 once it has settled. */
        void onWheelScrub(int index);
    }

    private static class Item {
        long id;
        boolean all, general, pinned, mention, muted, typing;
        int unread;
        TLRPC.TL_forumTopic topic;
        String key;
    }

    private static class Slot {
        final ImageReceiver receiver;
        final AvatarDrawable avatar = new AvatarDrawable();
        String key;

        Slot(View parent, int currentAccount) {
            receiver = new ImageReceiver(parent);
            receiver.setCurrentAccount(currentAccount);
        }
    }

    private final int currentAccount;
    private final long dialogId;
    private final boolean mono;
    private final Theme.ResourcesProvider resourcesProvider;
    private Delegate delegate;

    private final ArrayList<Item> items = new ArrayList<>();
    private final HashMap<Long, Integer> indexById = new HashMap<>();
    private final HashMap<Long, Slot> slots = new HashMap<>();
    private final Drawable allDrawable, generalDrawable;
    private int staticIconColor;

    private long currentId = -1;
    private float position;
    private float beltOverride = -1;
    private ValueAnimator settleAnimator;
    private int lastTick = Integer.MIN_VALUE;

    private long nudgeStart;
    private int nudgeDirection;

    private final AnimatedTextView.AnimatedTextDrawable badgeText;
    private final AnimatedFloat animatedBadge = new AnimatedFloat(this, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
    private final AnimatedFloat animatedBadgeMuted = new AnimatedFloat(this, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
    private boolean hasBadge, badgeMuted;
    private int badgeCount;
    private boolean badgeMention;
    private long popStart;

    private final Paint knurlPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix glowMatrix = new Matrix();
    private RadialGradient glowShader;
    private int glowColor;
    private final Path clipPath = new Path();
    private float lensAlpha = 1f;
    private final RectF rect = new RectF(), lensRect = new RectF();
    private final Puck bead = new Puck();
    private final Ends ends = new Ends();
    private boolean vertical;

    private boolean attached;

    public TopicsRollWheel(Context context, int currentAccount, long dialogId, boolean mono, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.currentAccount = currentAccount;
        this.dialogId = dialogId;
        this.mono = mono;
        this.resourcesProvider = resourcesProvider;

        allDrawable = context.getResources().getDrawable(R.drawable.other_chats).mutate();
        generalDrawable = context.getResources().getDrawable(R.drawable.msg_filled_general).mutate();

        badgeText = new AnimatedTextView.AnimatedTextDrawable(false, true, true);
        badgeText.setCallback(this);
        badgeText.setTextSize(dp(10.5f));
        badgeText.setTypeface(AndroidUtilities.bold());
        badgeText.setGravity(Gravity.CENTER);
        badgeText.setOverrideFullWidth(AndroidUtilities.displaySize.x);

        knurlPaint.setStrokeCap(Paint.Cap.ROUND);
        knurlPaint.setStrokeWidth(dp(1.25f));

        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        updateColors();
    }

    public void setDelegate(Delegate delegate) {
        this.delegate = delegate;
    }

    public void updateColors() {
        badgeText.setTextColor(Theme.getColor(Theme.key_chats_unreadCounterText, resourcesProvider));
        invalidate();
    }

    private int accent() {
        return Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider);
    }

    boolean isDark() {
        return resourcesProvider != null ? resourcesProvider.isDark() : Theme.isCurrentThemeDark();
    }

    /* Content */

    public void set(long currentId, ArrayList<TLRPC.TL_forumTopic> topics, Set<Integer> excludeTopics, boolean bot, boolean animated) {
        final MessagesController controller = MessagesController.getInstance(currentAccount);
        final HashMap<Long, Integer> previousUnread = new HashMap<>();
        for (int i = 0; i < items.size(); ++i) {
            previousUnread.put(items.get(i).id, items.get(i).unread);
        }

        items.clear();
        indexById.clear();
        final Item all = new Item();
        all.id = 0;
        all.all = true;
        all.key = "all";
        items.add(all);
        if (topics != null) {
            for (int i = 0; i < topics.size(); ++i) {
                final TLRPC.TL_forumTopic topic = topics.get(i);
                if (topic == null || bot && topic.id == 1 || excludeTopics != null && excludeTopics.contains(topic.id)) continue;
                final Item item = new Item();
                item.topic = topic;
                item.id = mono ? DialogObject.getPeerDialogId(topic.from_id) : topic.id;
                item.general = !mono && topic.id == 1;
                item.pinned = topic.pinned;
                item.unread = topic.unread_count;
                item.mention = !mono && topic.unread_mentions_count > 0;
                item.muted = !mono && controller.isDialogMuted(dialogId, topic.id);
                item.typing = !mono && controller.getPrintingString(dialogId, topic.id, true) != null;
                item.key = mono ? "peer" + item.id : "topic" + topic.id + "_" + topic.icon_emoji_id + "_" + topic.icon_color + "_" + topic.title;
                items.add(item);
            }
        }
        for (int i = 0; i < items.size(); ++i) {
            indexById.put(items.get(i).id, i);
        }

        final boolean idle = !dragging && settleAnimator == null && beltOverride < 0;
        final boolean changed = this.currentId != currentId;
        this.currentId = currentId;
        if (idle) {
            final int index = indexOf(currentId);
            if (changed && animated && isShown() && getAlpha() > 0 && Math.abs(index - position) > 0.01f) {
                // Picked somewhere else (a message, a link): the wheel turns there on its own.
                animatePosition(index, false);
            } else {
                position = index;
                lastTick = index;
            }
        }

        // A topic that just got a message, other than this one and not muted, gives the wheel a nudge.
        if (animated && idle && !changed) {
            for (int i = 0; i < items.size(); ++i) {
                final Item item = items.get(i);
                final Integer was = previousUnread.get(item.id);
                if (was != null && item.unread > was && !item.muted && item.id != currentId) {
                    final int direction = Integer.compare(i, Math.round(position));
                    if (direction != 0) {
                        nudgeDirection = direction;
                        nudgeStart = SystemClock.elapsedRealtime();
                    }
                    break;
                }
            }
        }

        bindBadge(animated);
        evictSlots();
        updateContentDescription();
        invalidate();
    }

    private void bindBadge(boolean animated) {
        int unread = 0, mutedUnread = 0;
        boolean mention = false;
        for (int i = 0; i < items.size(); ++i) {
            final Item item = items.get(i);
            if (item.all || item.id == currentId) continue;
            if (item.muted && !item.mention) {
                mutedUnread += item.unread;
            } else {
                unread += item.unread;
            }
            mention |= item.mention;
        }
        final boolean muted = unread <= 0 && !mention;
        final int count = muted ? mutedUnread : unread;
        final boolean show = count > 0 || mention;
        if (animated && show && (count > badgeCount || mention && !badgeMention)) {
            popStart = SystemClock.elapsedRealtime();
        }
        hasBadge = show;
        badgeMuted = muted;
        badgeCount = count;
        badgeMention = mention;
        if (show) {
            badgeText.setText((mention ? "@" : "") + (count > 0 ? (count > 999 ? "999+" : LocaleController.formatNumber(count, ',')) : ""), animated);
        }
        if (!animated) {
            animatedBadge.force(show);
            animatedBadgeMuted.force(muted);
        }
    }

    private void evictSlots() {
        final Iterator<Map.Entry<Long, Slot>> it = slots.entrySet().iterator();
        while (it.hasNext()) {
            final Map.Entry<Long, Slot> entry = it.next();
            final Integer index = indexById.get(entry.getKey());
            // Topics that left the list, and icons far from the lens, let their images go.
            if (index == null || slots.size() > 16 && Math.abs(index - position) > 5) {
                if (attached) entry.getValue().receiver.onDetachedFromWindow();
                it.remove();
            }
        }
    }

    public int getCount() {
        return items.size();
    }

    public int indexOf(long id) {
        final Integer index = indexById.get(id);
        return index == null ? 0 : index;
    }

    /** The topic's place on the wheel, or -1 when it is not on it. */
    public int findIndex(long id) {
        final Integer index = indexById.get(id);
        return index == null ? -1 : index;
    }

    /** Someone started or stopped typing somewhere: the wheel's lights follow. */
    public void updateTyping() {
        if (mono) return;
        final MessagesController controller = MessagesController.getInstance(currentAccount);
        boolean changed = false;
        for (int i = 0; i < items.size(); ++i) {
            final Item item = items.get(i);
            if (item.topic == null) continue;
            final boolean typing = controller.getPrintingString(dialogId, item.topic.id, true) != null;
            if (item.typing != typing) {
                item.typing = typing;
                changed = true;
            }
        }
        if (changed) invalidate();
    }

    /** 2 when the topic has news (or someone is typing there), 1 when its news is muted, 0 otherwise. */
    public int getLitAt(int index) {
        if (index < 0 || index >= items.size()) return 0;
        final Item item = items.get(index);
        if (!lit(item)) return 0;
        return item.muted && !item.mention && !item.typing ? 1 : 2;
    }

    public boolean isTypingAt(int index) {
        return index >= 0 && index < items.size() && items.get(index).typing;
    }

    /** What the person typing in the topic is doing ("Ines is typing"), or null. */
    public CharSequence getTypingAt(int index) {
        if (!isTypingAt(index) || items.get(index).topic == null) return null;
        return MessagesController.getInstance(currentAccount).getPrintingString(dialogId, items.get(index).topic.id, true);
    }

    public long getIdAt(int index) {
        return index >= 0 && index < items.size() ? items.get(index).id : 0;
    }

    public boolean isPinnedAt(int index) {
        return index >= 0 && index < items.size() && items.get(index).pinned;
    }

    public CharSequence getTitleAt(int index) {
        if (index < 0 || index >= items.size()) return "";
        final Item item = items.get(index);
        if (item.all) return getString(mono ? R.string.AllChats : R.string.OpenAllTopics);
        if (mono) return DialogObject.getName(item.id);
        return item.topic != null && item.topic.title != null ? item.topic.title : "";
    }

    public float getPosition() {
        return position;
    }

    public boolean isAllAt(int index) {
        return index >= 0 && index < items.size() && items.get(index).all;
    }

    public TLRPC.TL_forumTopic getTopicAt(int index) {
        return index >= 0 && index < items.size() ? items.get(index).topic : null;
    }

    public int getUnreadAt(int index) {
        return index >= 0 && index < items.size() ? items.get(index).unread : 0;
    }

    public boolean isMentionAt(int index) {
        return index >= 0 && index < items.size() && items.get(index).mention;
    }

    public boolean isMutedAt(int index) {
        return index >= 0 && index < items.size() && items.get(index).muted;
    }

    /** What the badge says (unread messages in the other topics), or null when it is hidden. */
    public String getBadgeString() {
        return hasBadge ? (badgeMention ? "@" : "") + (badgeCount > 0 ? (badgeCount > 999 ? "999+" : LocaleController.formatNumber(badgeCount, ',')) : "") : null;
    }

    public boolean isBadgeMuted() {
        return badgeMuted;
    }

    /** The names of the other topics with news, as many as max, or null when there is none. */
    public CharSequence getNewsTitles(int max) {
        final boolean onlyMuted = badgeMuted;
        final StringBuilder sb = new StringBuilder();
        int count = 0;
        for (int i = 0; i < items.size() && count < max; ++i) {
            final Item item = items.get(i);
            if (item.all || item.id == currentId || item.unread <= 0 && !item.mention) continue;
            if (item.muted && !item.mention && !onlyMuted) continue;
            if (count++ > 0) sb.append(", ");
            sb.append(getTitleAt(i));
        }
        return count > 0 ? sb : null;
    }

    /**
     * Stands the wheel up for side tabs: the drum turns up and down under a vertical swipe and its
     * scale runs down its right edge, so the rail rolls up into it and drops down out of it.
     */
    public void setVertical(boolean vertical) {
        if (this.vertical != vertical) {
            this.vertical = vertical;
            requestLayout();
            invalidate();
        }
    }

    public boolean isVertical() {
        return vertical;
    }

    /** The bead: it stays on your topic as the bar rolls out, so the bar's puck takes over from the wheel's. */
    public void setLensAlpha(float alpha) {
        if (lensAlpha != alpha) {
            lensAlpha = alpha;
            invalidate();
        }
    }

    /** While the bar rolls in or out, TopicsTabsView turns the wheel to the tab it is passing. */
    public void setBeltOverride(float override) {
        if (beltOverride != override) {
            beltOverride = override;
            invalidate();
        }
    }

    /* Turning */

    private int settleTarget;
    private long settleTime, settleFrame;

    // The wheel settles on its own clock, a frame at a time, by no more than a frame and a half: when
    // opening the topic holds up a frame or two, it carries on from where it was instead of leaping ahead.
    private void animatePosition(int target, boolean fromSwipe) {
        if (settleAnimator != null) {
            settleAnimator.cancel();
        }
        final float from = position;
        final OvershootInterpolator interpolator = new OvershootInterpolator(1.15f);
        settleTarget = target;
        settleTime = 0;
        settleFrame = 0;
        final ValueAnimator animator = settleAnimator = ValueAnimator.ofFloat(0f, 1f);
        animator.addUpdateListener(a -> {
            if (settleAnimator != a) return;
            final long now = SystemClock.uptimeMillis();
            if (settleFrame != 0) {
                settleTime += Math.min(24, now - settleFrame);
            }
            settleFrame = now;
            final float t = Math.min(1f, settleTime / (float) SETTLE_MS);
            position = lerp(from, target, interpolator.getInterpolation(t));
            if (fromSwipe) {
                checkTick();
            }
            invalidate();
            if (t >= 1f) {
                settleAnimator = null;
                position = target;
                lastTick = target;
                evictSlots();
                if (fromSwipe && delegate != null) {
                    delegate.onWheelScrub(-1);
                }
                a.cancel();
            }
        });
        // The animator only ticks; the clock above says where the wheel is, and ends it.
        animator.setInterpolator(null);
        animator.setDuration(SETTLE_MS * 8);
        animator.start();
    }

    // One click for every topic that passes the lens, a firmer one where the pinned topics end.
    private void checkTick() {
        if (items.isEmpty()) return;
        final int index = Utilities.clamp(Math.round(position), items.size() - 1, 0);
        if (index == lastTick) return;
        final boolean edge = lastTick >= 0 && lastTick < items.size() && items.get(lastTick).pinned != items.get(index).pinned;
        lastTick = index;
        if (edge) {
            AndroidUtilities.vibrate(this);
        } else {
            AndroidUtilities.vibrateCursor(this);
        }
        if (delegate != null) {
            delegate.onWheelScrub(index);
        }
    }

    /** Turns back to a topic without opening it, when the chat could not open the one the wheel turned to. */
    public void turnTo(long id) {
        currentId = id;
        bindBadge(true);
        updateContentDescription();
        animatePosition(indexOf(id), false);
    }

    /** Turns to the next (+1) or previous (-1) topic and opens it, as a swipe would. */
    public boolean turnBy(int direction) {
        if (items.isEmpty() || dragging) return false;
        final int from = indexOf(currentId);
        final int target = Utilities.clamp(from + direction, items.size() - 1, 0);
        if (target == from) return false;
        lastTick = Math.round(position);
        finishTurn(from, target);
        return true;
    }

    private void finishTurn(int from, int target) {
        animatePosition(target, true);
        if (target != from) {
            currentId = items.get(target).id;
            bindBadge(true);
            updateContentDescription();
            if (delegate != null) {
                delegate.onWheelTurned(currentId, target > from ? 1 : -1);
            }
        } else if (delegate != null) {
            delegate.onWheelScrub(-1);
        }
    }

    private float downX, downY, dragFrom;
    private boolean dragging, longPressed;
    private VelocityTracker velocityTracker;
    private final int touchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
    private final Runnable longPress = () -> {
        if (dragging || delegate == null) return;
        longPressed = true;
        setPressed(false);
        try {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        } catch (Exception ignore) {}
        delegate.onWheelLongPress(currentId);
    };

    private float rubberBand(float p) {
        final int last = items.size() - 1;
        if (p < 0) return -0.45f * (1f - (float) Math.exp(p / 0.9f));
        if (p > last) return last + 0.45f * (1f - (float) Math.exp(-(p - last) / 0.9f));
        return p;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!isEnabled() || items.isEmpty()) return false;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                downX = ev.getX();
                downY = ev.getY();
                dragging = false;
                longPressed = false;
                if (velocityTracker != null) velocityTracker.recycle();
                velocityTracker = VelocityTracker.obtain();
                velocityTracker.addMovement(ev);
                // The wheel turns along its axis, so it keeps the swipe for itself (no swipe back, or
                // no scrolling the chat when it stands up, from here).
                final ViewParent parent = getParent();
                if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
                setPressed(true);
                AndroidUtilities.runOnUIThread(longPress, ViewConfiguration.getLongPressTimeout());
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (velocityTracker != null) velocityTracker.addMovement(ev);
                final float dx = ev.getX() - downX, dy = ev.getY() - downY;
                final float along = vertical ? dy : dx, across = vertical ? dx : dy;
                if (!dragging && !longPressed && Math.abs(along) > touchSlop && Math.abs(along) > Math.abs(across)) {
                    dragging = true;
                    AndroidUtilities.cancelRunOnUIThread(longPress);
                    setPressed(false);
                    if (settleAnimator != null) {
                        settleAnimator.cancel();
                        settleAnimator = null;
                    }
                    nudgeStart = 0;
                    dragFrom = position;
                    downX = ev.getX();
                    downY = ev.getY();
                    lastTick = Utilities.clamp(Math.round(position), items.size() - 1, 0);
                    if (delegate != null) delegate.onWheelScrub(lastTick);
                } else if (!dragging && (Math.abs(dx) > touchSlop * 2 || Math.abs(dy) > touchSlop * 2)) {
                    AndroidUtilities.cancelRunOnUIThread(longPress);
                    setPressed(false);
                }
                if (dragging) {
                    position = rubberBand(dragFrom - (vertical ? ev.getY() - downY : ev.getX() - downX) / dp(CELL));
                    checkTick();
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                AndroidUtilities.cancelRunOnUIThread(longPress);
                final boolean wasPressed = isPressed();
                setPressed(false);
                if (dragging) {
                    dragging = false;
                    float velocity = 0;
                    if (velocityTracker != null) {
                        velocityTracker.addMovement(ev);
                        velocityTracker.computeCurrentVelocity(1000);
                        velocity = vertical ? velocityTracker.getYVelocity() : velocityTracker.getXVelocity();
                    }
                    // A flick carries the wheel on by up to two more topics.
                    final int last = items.size() - 1;
                    final int from = indexOf(currentId);
                    final float carry = Utilities.clamp(-velocity / dp(CELL) * 0.08f, 2f, -2f);
                    int target = Utilities.clamp(Math.round(position + carry), last, 0);
                    final float moved = position - dragFrom;
                    if (target == from && Math.abs(moved) > 0.28f) {
                        target = Utilities.clamp(from + (moved > 0 ? 1 : -1), last, 0);
                    }
                    finishTurn(from, target);
                } else if (ev.getActionMasked() == MotionEvent.ACTION_UP && wasPressed && !longPressed && delegate != null) {
                    playSoundEffect(android.view.SoundEffectConstants.CLICK);
                    delegate.onWheelTap();
                }
                if (velocityTracker != null) {
                    velocityTracker.recycle();
                    velocityTracker = null;
                }
                return true;
            }
        }
        return super.onTouchEvent(ev);
    }

    public boolean isTurning() {
        return dragging || settleAnimator != null;
    }

    /** Whether the wheel is under a finger or still on its way to its topic, past the last part of its overshoot. */
    public boolean isTurningFast() {
        return dragging || settleAnimator != null && Math.abs(position - settleTarget) > 0.12f;
    }

    /* Drawing */

    private float nudgeOffset() {
        if (nudgeStart == 0) return 0;
        final float t = (SystemClock.elapsedRealtime() - nudgeStart) / 1000f;
        if (t > NUDGE_MS / 1000f) {
            nudgeStart = 0;
            return 0;
        }
        invalidate();
        return nudgeDirection * 0.32f * (float) Math.sin(t * Math.PI * 2 * 1.9f) * (float) Math.exp(-t * 5.2f);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (items.isEmpty()) return;
        // Standing up on the side, the drum turns along y and its scale runs down its right edge.
        final float left = dp(INSET), top = dp(INSET + TOP_SPACE);
        final float width = dp(vertical ? HEIGHT : WIDTH), height = dp(vertical ? WIDTH : HEIGHT);
        final float cx = left + width / 2f, cy = top + height / 2f;
        final int accent = accent();
        final boolean dark = isDark();
        final float belt = (beltOverride >= 0 ? beltOverride : position) + nudgeOffset();

        canvas.save();
        rect.set(left, top, left + width, top + height);
        clipPath.rewind();
        clipPath.addRoundRect(rect, Math.min(width, height) / 2f, Math.min(width, height) / 2f, Path.Direction.CW);
        canvas.clipPath(clipPath);

        // News further along than the wheel shows makes that end of the well glow.
        float loudStart = 0, loudEnd = 0, quietStart = 0, quietEnd = 0;
        for (int i = 0; i < items.size(); ++i) {
            final Item item = items.get(i);
            if (!lit(item)) continue;
            final float away = smoothstep(1.3f, 2.3f, Math.abs(i - belt));
            final boolean loud = !item.muted || item.mention || item.typing;
            if (i < belt) {
                if (loud) loudStart += away; else quietStart += away;
            } else {
                if (loud) loudEnd += away; else quietEnd += away;
            }
        }
        drawEndGlow(canvas, rect, loudStart, quietStart, false);
        drawEndGlow(canvas, rect, loudEnd, quietEnd, true);

        // Knurled ticks along the lower edge (the right edge, standing up), turning with the drum; a
        // lit topic takes its tick's place.
        final float radius = dp(RADIUS);
        knurlPaint.setColor(getKnurlColor(resourcesProvider, dark));
        final int baseKnurl = (int) Math.floor(belt * 2);
        for (int j = -8; j <= 8; ++j) {
            final int tick = baseKnurl + j;
            if ((tick & 1) == 0 && tick >= 0 && tick / 2 < items.size() && lit(items.get(tick / 2))) continue;
            final float u = tick / 2f - belt;
            final float angle = u * STEP;
            if (Math.abs(angle) > 1.45f) continue;
            final float c = (float) Math.cos(angle);
            final float along = radius * (float) Math.sin(angle);
            knurlPaint.setAlpha((int) (knurlPaint.getAlpha() * c));
            if (vertical) {
                canvas.drawLine(left + width - dp(KNURL_FROM_EDGE + KNURL), cy + along, left + width - dp(KNURL_FROM_EDGE), cy + along, knurlPaint);
            } else {
                canvas.drawLine(cx + along, top + height - dp(KNURL_FROM_EDGE + KNURL), cx + along, top + height - dp(KNURL_FROM_EDGE), knurlPaint);
            }
            knurlPaint.setColor(getKnurlColor(resourcesProvider, dark));
        }

        // The bead over the topic you are in: a drop of glass the drum turns under, the same glass as
        // the bar's puck. (The well the wheel turns in is the island's.)
        if (lensAlpha > 0) {
            lensRect.set(cx - dp(16), cy - dp(16), cx + dp(16), cy + dp(16));
            bead.draw(canvas, lensRect, dp(16), lensAlpha, dark);
        }

        // Topic icons on the drum: full size at the bead, smaller and fainter as they curve away.
        final int first = Math.max(0, (int) Math.floor(belt - 2.2f)), lastIndex = Math.min(items.size() - 1, (int) Math.ceil(belt + 2.2f));
        for (int i = first; i <= lastIndex; ++i) {
            final float angle = (i - belt) * STEP;
            if (Math.abs(angle) > 1.52f) continue;
            final float c = (float) Math.cos(angle);
            final float face = Utilities.clamp(1f - Math.abs(i - belt), 1f, 0f);
            final float scale = (0.46f + 0.54f * c) * (1f + 0.08f * face);
            final float alpha = (float) Math.pow(c, 1.4f) * (0.62f + 0.38f * face);
            final float along = radius * (float) Math.sin(angle);
            drawIcon(canvas, items.get(i), vertical ? cx : cx + along, vertical ? cy + along : cy, dp(ICON) * scale, alpha, accent);
        }

        // Topics with news light up on the scale, turning with the drum like stations on a radio dial.
        boolean anyTyping = false;
        for (int i = first; i <= lastIndex; ++i) {
            final Item item = items.get(i);
            if (!lit(item)) continue;
            final float angle = (i - belt) * STEP;
            if (Math.abs(angle) > 1.45f) continue;
            final float c = (float) Math.cos(angle);
            final float along = radius * (float) Math.sin(angle);
            final float x = vertical ? left + width - dp(LIGHT_FROM_EDGE) : cx + along;
            final float y = vertical ? cy + along : top + height - dp(LIGHT_FROM_EDGE);
            final boolean typing = item.typing && item.id != currentId;
            drawLight(canvas, lightPaint, rect, x, y, Math.max(0.35f, c), (float) Math.pow(c, 1.2f), typing, item.mention, item.muted && !item.mention, vertical, resourcesProvider);
            anyTyping |= typing;
        }
        if (anyTyping) {
            invalidate();
        }

        // The drum's ends, shaded where it curves away.
        rect.set(left, top, left + width, top + height);
        ends.draw(canvas, rect, Math.min(width, height) / 2f, dp(20), 1f, 1f, vertical, dark);
        canvas.restore();

        drawBadge(canvas, left + width, top);
    }

    public static int getKnurlColor(Theme.ResourcesProvider resourcesProvider, boolean dark) {
        return Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), dark ? 0.22f : 0.28f);
    }

    /** The shade at the ends of a drum, where it curves away. */
    public static int getShadeColor(boolean dark) {
        return dark ? 0x52000000 : 0x21142832;
    }

    /**
     * A light on a scale, like a station on a radio dial: a short bar in the counter's colour, wider
     * for a mention, grey and without its halo when the news is muted. Where someone is typing, it is
     * three dots that rise and fall one after another (the caller keeps redrawing while it shows).
     * squeeze narrows it as a drum turns it away; vertical lays it along a rail's edge.
     */
    public static void drawLight(Canvas canvas, Paint paint, RectF rect, float x, float y, float squeeze, float alpha,
                                 boolean typing, boolean mention, boolean muted, boolean vertical, Theme.ResourcesProvider resourcesProvider) {
        if (alpha <= 0) return;
        if (vertical) {
            canvas.save();
            canvas.rotate(90, x, y);
        }
        if (typing) {
            final float t = SystemClock.uptimeMillis() / 1000f;
            final int color = Theme.getColor(Theme.key_chats_unreadCounter, resourcesProvider);
            for (int k = 0; k < 3; ++k) {
                final float phase = ((t * 1.6f - k * 0.2f) % 1f + 1f) % 1f;
                final float s = (float) Math.sin(Math.PI * phase);
                final float dx = (k - 1) * dp(5) * squeeze;
                final float pulse = alpha * (0.35f + 0.65f * s * s);
                paint.setColor(Theme.multAlpha(color, pulse * 0.25f));
                canvas.drawCircle(x + dx, y, dp(2.75f), paint);
                paint.setColor(Theme.multAlpha(color, pulse));
                canvas.drawCircle(x + dx, y, dp(1.5f), paint);
            }
        } else {
            final int color = Theme.getColor(muted ? Theme.key_chats_unreadCounterMuted : Theme.key_chats_unreadCounter, resourcesProvider);
            final float w = dp(mention ? 6.5f : 4.5f) * squeeze, h = dp(1.5f);
            if (!muted) {
                for (int k = 2; k >= 1; --k) {
                    final float halo = dp(1.5f * k);
                    paint.setColor(Theme.multAlpha(color, alpha * (k == 2 ? 0.12f : 0.24f)));
                    rect.set(x - w - halo, y - h - halo, x + w + halo, y + h + halo);
                    canvas.drawRoundRect(rect, h + halo, h + halo, paint);
                }
            }
            paint.setColor(Theme.multAlpha(color, alpha));
            rect.set(x - w, y - h, x + w, y + h);
            canvas.drawRoundRect(rect, h, h, paint);
        }
        if (vertical) {
            canvas.restore();
        }
    }

    /**
     * A well set into glass: a little darker than the glass, shaded under its upper edge and lit
     * along its lower one, like the slot of a dial. The wheel turns in one, the bar's and the rail's
     * surface is the same track, and Back and the menu in the header sit in their own.
     */
    public static class Well {
        private final RectF rect = new RectF();
        private final Path clip = new Path(), lip = new Path();
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private LinearGradient shade;
        private int shadeColor;

        /** Draws the well over bounds, with fully rounded ends, sunk in by amount (0 to 1). */
        public void draw(Canvas canvas, RectF bounds, float amount, boolean dark) {
            draw(canvas, bounds, Math.min(bounds.width(), bounds.height()) / 2f, amount, dark);
        }

        /** Draws the well over bounds with the given corner radius, sunk in by amount (0 to 1). */
        public void draw(Canvas canvas, RectF bounds, float radius, float amount, boolean dark) {
            if (amount <= 0f) return;
            rect.set(bounds);
            paint.setColor(Theme.multAlpha(0xFF000000, (dark ? 0.18f : 0.045f) * amount));
            canvas.drawRoundRect(rect, radius, radius, paint);

            canvas.save();
            clip.rewind();
            clip.addRoundRect(rect, radius, radius, Path.Direction.CW);
            canvas.clipPath(clip);
            final int color = dark ? 0x52000000 : 0x170F1E23;
            if (shade == null || shadeColor != color) {
                shadeColor = color;
                shade = new LinearGradient(0, 0, 0, dp(3.5f), color, 0, Shader.TileMode.CLAMP);
            }
            shadePaint.setShader(shade);
            shadePaint.setAlpha((int) (0xFF * amount));
            canvas.save();
            canvas.translate(0, rect.top);
            canvas.drawRect(rect.left, 0, rect.right, dp(3.5f), shadePaint);
            canvas.restore();

            lip.rewind();
            rect.offset(0, -dp(1));
            lip.addRoundRect(rect, radius, radius, Path.Direction.CW);
            rect.offset(0, dp(1));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                canvas.clipOutPath(lip);
            } else {
                canvas.clipPath(lip, Region.Op.DIFFERENCE);
            }
            paint.setColor(Theme.multAlpha(0xFFFFFFFF, (dark ? 0.05f : 0.7f) * amount));
            canvas.drawRect(rect, paint);
            canvas.restore();
        }
    }

    /**
     * A puck of glass over the topic you are in: white glass, a little brighter at the top, with a
     * soft shadow under it and a fine edge (frosted grey in a dark theme). The wheel's bead is a round
     * one; the bar's and the rail's are the same glass drawn around the whole topic.
     */
    public static class Puck {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint highlight = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix matrix = new Matrix();
        private final RectF rect = new RectF();
        private LinearGradient body, shine;
        private boolean shaderDark;

        public Puck() {
            edge.setStyle(Paint.Style.STROKE);
            edge.setStrokeWidth(Math.max(1, dp(0.5f)));
            highlight.setStyle(Paint.Style.STROKE);
            highlight.setStrokeWidth(Math.max(1, dp(1)));
        }

        public void draw(Canvas canvas, RectF bounds, float radius, float alpha, boolean dark) {
            if (alpha <= 0 || bounds.isEmpty()) return;
            if (body == null || shaderDark != dark) {
                shaderDark = dark;
                body = new LinearGradient(0, 0, 0, 1, dark ? 0x2BFFFFFF : 0xFAFFFFFF, dark ? 0x17FFFFFF : 0xF0F9FAFB, Shader.TileMode.CLAMP);
                shine = new LinearGradient(0, 0, 0, 1, new int[] { dark ? 0x24FFFFFF : 0xE6FFFFFF, 0 }, new float[] { 0f, 0.5f }, Shader.TileMode.CLAMP);
            }
            // A soft shadow under it, a little lower than the glass.
            final int shade = dark ? 0x73000000 : 0x2B102824;
            for (int i = 2; i >= 1; --i) {
                shadow.setColor(Theme.multAlpha(shade, alpha * (i == 2 ? 0.35f : 0.6f)));
                rect.set(bounds);
                rect.inset(-dp(0.5f) * (i - 1), -dp(0.5f) * (i - 1));
                rect.offset(0, dp(0.5f) * i);
                canvas.drawRoundRect(rect, radius + dp(0.5f) * (i - 1), radius + dp(0.5f) * (i - 1), shadow);
            }
            matrix.setScale(1, bounds.height());
            matrix.postTranslate(0, bounds.top);
            body.setLocalMatrix(matrix);
            shine.setLocalMatrix(matrix);
            fill.setShader(body);
            fill.setAlpha((int) (0xFF * alpha));
            canvas.drawRoundRect(bounds, radius, radius, fill);
            edge.setColor(Theme.multAlpha(dark ? 0x0FFFFFFF : 0x120F1E23, alpha));
            canvas.drawRoundRect(bounds, radius, radius, edge);
            // A line of light along its upper edge, inside.
            rect.set(bounds);
            final float in = highlight.getStrokeWidth() / 2f + dp(0.5f);
            rect.inset(in, in);
            highlight.setShader(shine);
            highlight.setAlpha((int) (0xFF * alpha));
            canvas.drawRoundRect(rect, Math.max(0, radius - in), Math.max(0, radius - in), highlight);
        }
    }

    /**
     * The ends of a drum, shaded where it curves away: its left and right ends, or its top and bottom
     * when it stands up. Drawn over the drum's whole rounded shape, so the shade follows its ends.
     */
    public static class Ends {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix matrix = new Matrix();
        private LinearGradient gradient;
        private int gradientColor;
        private float gradientLength;

        public void draw(Canvas canvas, RectF drum, float radius, float length, float start, float end, boolean vertical, boolean dark) {
            final int color = getShadeColor(dark);
            if (gradient == null || gradientColor != color || gradientLength != length) {
                gradientColor = color;
                gradientLength = length;
                gradient = new LinearGradient(0, 0, length, 0, color, 0, Shader.TileMode.CLAMP);
            }
            paint.setShader(gradient);
            for (int i = 0; i < 2; ++i) {
                final float amount = i == 0 ? start : end;
                if (amount <= 0) continue;
                if (vertical) {
                    matrix.setRotate(i == 0 ? 90 : -90);
                    matrix.postTranslate(0, i == 0 ? drum.top : drum.bottom);
                } else {
                    matrix.setScale(i == 0 ? 1 : -1, 1);
                    matrix.postTranslate(i == 0 ? drum.left : drum.right, 0);
                }
                gradient.setLocalMatrix(matrix);
                paint.setAlpha((int) (0xFF * amount));
                canvas.drawRoundRect(drum, radius, radius, paint);
            }
        }
    }

    private void drawIcon(Canvas canvas, Item item, float x, float y, float size, float alpha, int accent) {
        if (item.all || item.general) {
            final Drawable drawable = item.all ? allDrawable : generalDrawable;
            if (staticIconColor != accent) {
                staticIconColor = accent;
                allDrawable.setColorFilter(new PorterDuffColorFilter(accent, PorterDuff.Mode.SRC_IN));
                generalDrawable.setColorFilter(new PorterDuffColorFilter(accent, PorterDuff.Mode.SRC_IN));
            }
            final float s = item.general ? size * 0.78f : size;
            drawable.setBounds((int) (x - s / 2f), (int) (y - s / 2f), (int) (x + s / 2f), (int) (y + s / 2f));
            drawable.setAlpha((int) (0xFF * alpha));
            drawable.draw(canvas);
        } else {
            final Slot slot = slotFor(item);
            slot.receiver.setImageCoords(x - size / 2f, y - size / 2f, size, size);
            slot.receiver.setAlpha(alpha);
            slot.receiver.draw(canvas);
        }
    }

    private boolean hasNews(Item item) {
        return !item.all && item.id != currentId && (item.unread > 0 || item.mention);
    }

    /** A light on the scale: news, or someone typing there. */
    private boolean lit(Item item) {
        return hasNews(item) || !item.all && item.id != currentId && item.typing;
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        final float t = Utilities.clamp((x - edge0) / (edge1 - edge0), 1f, 0f);
        return t * t * (3f - 2f * t);
    }

    // A soft glow inside one end of the well, where the drum starts or ends, in the counter's color
    // (grey when the news there is muted).
    private void drawEndGlow(Canvas canvas, RectF well, float loud, float quiet, boolean end) {
        final float amount = Math.min(1f, loud + 0.6f * quiet);
        if (amount <= 0.01f) return;
        final int color = Theme.getColor(loud > 0.05f || quiet <= 0 ? Theme.key_chats_unreadCounter : Theme.key_chats_unreadCounterMuted, resourcesProvider);
        if (glowShader == null || glowColor != color) {
            glowColor = color;
            glowShader = new RadialGradient(0, 0, 1, new int[] { ColorUtils.setAlphaComponent(color, 0x6B), ColorUtils.setAlphaComponent(color, 0) }, null, Shader.TileMode.CLAMP);
        }
        final float reach = dp(34);
        glowPaint.setShader(glowShader);
        glowPaint.setAlpha((int) (0xFF * amount));
        if (vertical) {
            final float edge = end ? well.bottom : well.top;
            glowMatrix.setScale(well.width() * 0.72f, reach);
            glowMatrix.postTranslate(well.centerX(), edge);
            glowShader.setLocalMatrix(glowMatrix);
            canvas.drawRect(well.left, end ? edge - reach : edge, well.right, end ? edge : edge + reach, glowPaint);
        } else {
            final float edge = end ? well.right : well.left;
            glowMatrix.setScale(reach, well.height() * 0.72f);
            glowMatrix.postTranslate(edge, well.centerY());
            glowShader.setLocalMatrix(glowMatrix);
            canvas.drawRect(end ? edge - reach : edge, well.top, end ? edge : edge + reach, well.bottom, glowPaint);
        }
    }

    private Slot slotFor(Item item) {
        Slot slot = slots.get(item.id);
        if (slot == null) {
            slot = new Slot(this, currentAccount);
            if (attached) slot.receiver.onAttachedToWindow();
            slots.put(item.id, slot);
        }
        if (!TextUtils.equals(slot.key, item.key)) {
            slot.key = item.key;
            if (mono) {
                final TLObject peer = MessagesController.getInstance(currentAccount).getUserOrChat(item.id);
                slot.avatar.setInfo(currentAccount, peer);
                slot.receiver.setRoundRadius(dp(ICON) / 2);
                slot.receiver.setForUserOrChat(peer, slot.avatar);
            } else if (item.topic != null) {
                slot.receiver.setRoundRadius(0);
                ForumUtilities.setTopicIcon(getContext(), slot.receiver, item.topic, false, false, resourcesProvider);
            }
        }
        return slot;
    }

    // Unread messages in the other topics, on the rim of the well at its right end, clear of the icons.
    private void drawBadge(Canvas canvas, float right, float top) {
        final float shown = animatedBadge.set(hasBadge);
        if (shown <= 0) return;
        badgeText.setTextColor(Theme.getColor(Theme.key_chats_unreadCounterText, resourcesProvider));
        float pop = 1f;
        final long since = SystemClock.elapsedRealtime() - popStart;
        if (since < POP_MS) {
            final float p = since / (float) POP_MS;
            pop = p < 0.6f ? lerp(0.7f, 1.22f, CubicBezierInterpolator.EASE_OUT.getInterpolation(p / 0.6f)) : lerp(1.22f, 1f, CubicBezierInterpolator.EASE_OUT.getInterpolation((p - 0.6f) / 0.4f));
            invalidate();
        }
        final float h = dp(17), w = Math.max(h, badgeText.getCurrentWidth() + dp(9));
        rect.set(right + dp(3) - w, top - h / 2f, right + dp(3), top + h / 2f);
        canvas.save();
        final float s = pop * lerp(0.6f, 1f, shown);
        canvas.scale(s, s, rect.centerX(), rect.centerY());
        badgePaint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider), shown));
        rect.inset(-dp(2), -dp(2));
        canvas.drawRoundRect(rect, h / 2f + dp(2), h / 2f + dp(2), badgePaint);
        rect.inset(dp(2), dp(2));
        badgePaint.setColor(Theme.multAlpha(ColorUtils.blendARGB(
            Theme.getColor(Theme.key_chats_unreadCounter, resourcesProvider),
            Theme.getColor(Theme.key_chats_unreadCounterMuted, resourcesProvider),
            animatedBadgeMuted.set(badgeMuted)), shown));
        canvas.drawRoundRect(rect, h / 2f, h / 2f, badgePaint);
        badgeText.setBounds(rect);
        badgeText.setAlpha((int) (0xFF * shown));
        badgeText.draw(canvas);
        canvas.restore();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (vertical) {
            setMeasuredDimension(dp(HEIGHT + INSET * 2), dp(WIDTH + INSET * 2 + TOP_SPACE));
        } else {
            setMeasuredDimension(dp(WIDTH + INSET * 2), dp(HEIGHT + INSET * 2 + TOP_SPACE));
        }
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return who == badgeText || super.verifyDrawable(who);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        for (Slot slot : slots.values()) {
            slot.receiver.onAttachedToWindow();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        attached = false;
        AndroidUtilities.cancelRunOnUIThread(longPress);
        if (settleAnimator != null) {
            settleAnimator.cancel();
            settleAnimator = null;
        }
        for (Slot slot : slots.values()) {
            slot.receiver.onDetachedFromWindow();
        }
    }

    /* Accessibility: the wheel is a button that also scrolls through the topics. */

    private void updateContentDescription() {
        final StringBuilder sb = new StringBuilder();
        sb.append(getTitleAt(indexOf(currentId)));
        if (hasBadge && badgeCount > 0) {
            sb.append(", ").append(LocaleController.formatPluralString("TopicsRollUnreadElsewhere", badgeCount));
        }
        sb.append(". ").append(getString(R.string.TopicsRollWheelHelp));
        setContentDescription(sb);
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.Button");
        info.setClickable(true);
        info.setScrollable(true);
        info.addAction(AccessibilityNodeInfo.ACTION_CLICK);
        info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
        info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        if (action == AccessibilityNodeInfo.ACTION_CLICK) {
            if (delegate != null) delegate.onWheelTap();
            return true;
        } else if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) {
            return turnBy(1);
        } else if (action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
            return turnBy(-1);
        }
        return super.performAccessibilityAction(action, arguments);
    }
}
