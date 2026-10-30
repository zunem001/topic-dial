package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;

import me.vkryl.android.animator.ListAnimator;

public class ChatActivityTopPanelLayout extends AnimatedLinearLayout {
    public ChatActivityTopPanelLayout(@NonNull Context context) {
        super(context);

        setOrientation(LinearLayout.VERTICAL);
        updateColors();
    }

    BlurredBackgroundDrawable backgroundDrawable;
    private float glassAlpha = 1f;

    public void setBlurredBackground(BlurredBackgroundDrawable background) {
        backgroundDrawable = background;
    }

    /** The topic wheel's island stands in for this glass while the panel shares the island. */
    public void setGlassAlpha(float alpha) {
        if (glassAlpha != alpha) {
            glassAlpha = alpha;
            checkBoundsAndClipping();
            invalidate();
        }
    }

    /** How many panels show, or are about to. */
    public int getVisiblePanelsCount() {
        int count = 0;
        for (int a = 0, N = getChildCount(); a < N; a++) {
            if (isViewVisible(getChildAt(a))) {
                count++;
            }
        }
        return count;
    }

    /** Whether exactly one panel shows, or is about to, and it is {@code height} px tall. */
    public boolean isSinglePanel(int height) {
        View single = null;
        for (int a = 0, N = getChildCount(); a < N; a++) {
            final View view = getChildAt(a);
            if (isViewVisible(view)) {
                if (single != null) return false;
                single = view;
            }
        }
        if (single == null) return false;
        final int h = single.getLayoutParams() != null && single.getLayoutParams().height > 0 ? single.getLayoutParams().height : single.getMeasuredHeight();
        return Math.abs(h - height) <= dp(2);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        checkBoundsAndClipping();
    }

    @Override
    protected void onItemsChanged() {
        super.onItemsChanged();
        checkBoundsAndClipping();
        invalidate();
    }

    @Override
    public void setPadding(int left, int top, int right, int bottom) {
        super.setPadding(left, top, right, bottom);
        checkBoundsAndClipping();
        invalidate();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        // Without its own glass, the panels leave the margin on their left to the topic wheel beside them.
        return super.dispatchTouchEvent(ev)
                || ev.getAction() == MotionEvent.ACTION_DOWN && backgroundDrawable != null && backgroundDrawable.getBounds().contains((int) ev.getX(), (int) ev.getY())
                && ev.getX() >= getPaddingLeft() - dp(7) * glassAlpha;
    }

    private final Path clipPath = new Path();
    private final RectF clipRectF = new RectF();

    private void checkBoundsAndClipping() {
        final float bgHeight = getMetadata().getTotalHeight();
        final float bgAlpha = getMetadata().getTotalVisibility();

        clipRectF.set(getPaddingLeft(), getPaddingTop(), getMeasuredWidth() - getPaddingRight(), getPaddingTop() + bgHeight);

        final float r = Math.min(dp(18), Math.min(clipRectF.width(), clipRectF.height()) / 2f);
        clipPath.rewind();
        clipPath.addRoundRect(clipRectF, r, r, Path.Direction.CW);

        if (backgroundDrawable != null) {
            backgroundDrawable.setAlpha((int) (bgAlpha * glassAlpha * 255));
            backgroundDrawable.setBounds(getPaddingLeft() - dp(7), 0, getMeasuredWidth() - getPaddingRight() + dp(7), getPaddingTop() + getPaddingBottom() + (int) bgHeight);
            backgroundDrawable.setRadius(Math.min(dp(18), bgHeight / 2));
        }
    }

    public void updateColors() {
        if (backgroundDrawable != null) {
            backgroundDrawable.updateColors();
        }

        final int color = Theme.getColor(Theme.key_windowBackgroundWhite);
        final int alpha = Color.alpha(color);

        invalidate();
    }

    private FragmentContextView callFragmentContextView;

    public void setCallFragmentContextView(FragmentContextView fragmentContextView) {
        callFragmentContextView = fragmentContextView;
        callFragmentContextView.getCapsuleBlobDrawable().setCallback(this);
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return super.verifyDrawable(who) || callFragmentContextView != null && callFragmentContextView.getCapsuleBlobDrawable() == who;
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        if (getMetadata().getTotalVisibility() == 0) return;

        if (backgroundDrawable != null && glassAlpha > 0) {
            backgroundDrawable.draw(canvas);
        }

        boolean callDrawn = false;
        if (callFragmentContextView != null) {
            final int style = callFragmentContextView.getCurrentStyle();
            if (style == FragmentContextView.STYLE_ACTIVE_GROUP_CALL || style == FragmentContextView.STYLE_CONNECTING_GROUP_CALL) {
                for (int a = 0, N = getEntriesCount(); a < N; a++) {
                    final ListAnimator.Entry<AnimatedLinearLayout.Holder> entry = getEntry(a);
                    final float top = getPaddingTop() + entry.getRectF().top;
                    final View view = entry.item.view;
                    final float alpha = entry.getVisibility();
                    if (alpha <= 0) {
                        continue;
                    }

                    if (callFragmentContextView == view || callFragmentContextView.getParent() == view) {
                        final CapsuleBlobDrawable capsuleBlobDrawable = callFragmentContextView.getCapsuleBlobDrawable();

                        final int p = capsuleBlobDrawable.getRequiredInset();
                        final int h = dp(36) + p * 2;
                        capsuleBlobDrawable.setBounds(getPaddingLeft() - p, -p, getMeasuredWidth() - getPaddingRight() + p, -p + h);
                        capsuleBlobDrawable.setAlpha((int) (255 * alpha));
                        canvas.save();
                        canvas.translate(0, top);
                        capsuleBlobDrawable.draw(canvas);
                        canvas.restore();

                        callDrawn = true;
                    }
                }
            }
        }

        canvas.save();
        canvas.clipPath(clipPath);
        for (int a = 0, N = getEntriesCount(); a < N; a++) {
            final ListAnimator.Entry<AnimatedLinearLayout.Holder> entry = getEntry(a);
            final float top = getPaddingTop() + entry.getRectF().top;
            final View view = entry.item.view;

            final float position = entry.getPosition();
            final float alpha = entry.getVisibility() * Math.min(1, position);

            if (alpha <= 0) {
                continue;
            }

            if (callDrawn && callFragmentContextView != null && (callFragmentContextView == view || callFragmentContextView.getParent() == view)) {
                continue;
            }

            final int wasAlpha = Theme.dividerPaint.getAlpha();
            Theme.dividerPaint.setAlpha((int) (wasAlpha * alpha));
            final float offsetL = getPaddingLeft() + dp(16) * (1f - alpha);
            final float offsetR = getPaddingRight() + dp(16) * (1f - alpha);
            canvas.drawLine(offsetL, top, getWidth() - offsetR, top, Theme.dividerPaint);
            Theme.dividerPaint.setAlpha(wasAlpha);
        }

        super.dispatchDraw(canvas);
        canvas.restore();
    }
}
