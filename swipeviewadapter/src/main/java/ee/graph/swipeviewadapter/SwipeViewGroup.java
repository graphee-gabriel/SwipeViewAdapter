/*
 * Copyright 2014 Wouter Dullaert
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

package ee.graph.swipeviewadapter;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Checkable;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * Class to hold a ListView item and the swipe backgrounds
 *
 * Created by wdullaer on 22.06.14.
 */
public class SwipeViewGroup extends FrameLayout implements Checkable {
    private static final String TAG = SwipeViewGroup.class.getName();

    public static final int
            LAYOUT_MATCH_PARENT = -1,
            LAYOUT_WRAP_CONTENT = -2,
            LAYOUT_DISMISS= -3,
            LAYOUT_AUTOLAYOUT = -4;


    private static final int
            MATCH = LayoutParams.MATCH_PARENT,
            WRAP = LayoutParams.WRAP_CONTENT,
            HEIGHT_MEASURE_SPEC = MeasureSpec.makeMeasureSpec(1000, MeasureSpec.AT_MOST);

    private static final float
            mExpandSwipeRatio = 0.55f;

    private SparseArray<Integer>
            mBackgroundHeight = new SparseArray<>(),
            mBackgroundType = new SparseArray<>();
    private SparseArray<View>
            mBackgroundMap = new SparseArray<>();

    private View contentView = null;
    private List<Integer> mBackgroundMatchParent = new ArrayList<>();
    private List<Integer> mBackgroundAutoLayout = new ArrayList<>();
    private OnTouchListener swipeTouchListener;

    private int
            slideInOffsetLeft = 0,
            slideInOffsetRight = 0,
            mContentViewHeight = -1,
            visibleView = SwipeDirections.DIRECTION_NEUTRAL,
            slideInView = SwipeDirections.DIRECTION_NEUTRAL,
            mLayoutParamHeight = MATCH,
            mLayoutParamWidth = MATCH,
            mMinAnimDuration,
            mMaxAnimDuration,
            mWidthMeasureSpec = MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.EXACTLY);

    private boolean
            mViewIsInitialized = false,
            fadeOnSlideLeft = true,
            fadeOnSlideRight = true,
            fixedBackground,
            fadeOnTranslation,
            isChecked;

    private OnSlidingListener onSlidingListener;
    private OnSlideIn onSlideInListener;
    private List<OnSlidingListener> onSlidingListeners = new ArrayList<>();

    /**
     * Standard android View constructor
     *
     * @param context
     */
    public SwipeViewGroup(Context context) {
        super(context);
        initialize();
    }

    /**
     * Standard android View constructor
     *
     * @param context
     * @param attrs
     */
    public SwipeViewGroup(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    /**
     * Standard android View constructor
     *
     * @param context
     * @param attrs
     * @param defStyle
     */
    public SwipeViewGroup(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initialize();
    }




    /**
     * Common code for all the constructors
     */
    private void initialize() {
        // Allows click events to reach the ListView in case the row has a clickable View like a Button
        // FIXME: probably messes with accessibility. Doesn't fix root cause (see onTouchEvent)
        //setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        setClipChildren(false);
        mMinAnimDuration = getContext().getResources().getInteger(
                android.R.integer.config_shortAnimTime);
        mMaxAnimDuration = mMinAnimDuration * 3;
        getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @SuppressWarnings("deprecation")
            @Override
            public void onGlobalLayout() {
                getViewTreeObserver().removeGlobalOnLayoutListener(this);
                mLayoutParamHeight = getLayoutParams().height;
                mLayoutParamWidth = getLayoutParams().width;
                mViewIsInitialized = true;
                setContentView(contentView);
            }
        });
    }




    /**
     * Add a contentView to the Layout
     *
     * @param contentView The View to be added
     * @return A reference to the layout so commands can be chained
     */
    public SwipeViewGroup setContentView(final View contentView) {
        if (mViewIsInitialized) {
            if(this.contentView != null)
                removeView(this.contentView);
            addView(contentView);
        }
        this.contentView = contentView;
        //setLayoutParams(this, mLayoutParamWidth, mLayoutParamHeight);
        //this.measure(mLayoutParamWidth, mLayoutParamHeight);
        setLayoutParams(contentView, mLayoutParamWidth, mLayoutParamHeight);
        mContentViewHeight = -1;
        measureContentViewHeight();
        mWidthMeasureSpec = MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.EXACTLY);
        return this;
    }




    /**
     * Add a View to the background of the Layout.
     *
     * @param background The View to be added to the Layout
     * @param direction The key to be used to find it again
     * @param type int -1 -2 -3 if set to wrap_content, the row height will be resized to the background height when shown, if set to dismiss, the row will shrink to 0
     * @return A reference to the a layout so commands can be chained
     */
    public SwipeViewGroup addBackground(final View background, final int direction, final int type) {
        if(mBackgroundMap.get(direction) != null)
            removeView(mBackgroundMap.get(direction));
        mBackgroundMap.put(direction, background);
        mBackgroundType.put(direction, type);
        //refreshVisibleView();
        background.setVisibility(slideInView == direction ? VISIBLE : INVISIBLE);
        addView(background, 0);
        translateBackgrounds();
        if(type == LAYOUT_MATCH_PARENT) {
            mBackgroundMatchParent.remove((Integer) direction);
            mBackgroundMatchParent.add(direction);
        } else if(type == LAYOUT_AUTOLAYOUT) {
            mBackgroundAutoLayout.remove((Integer) direction);
            mBackgroundAutoLayout.add(direction);
        } else {
            //measureBackground(direction);
            background.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
            background.getViewTreeObserver().removeOnPreDrawListener(this);
                measureBackground(direction);
                return true;
            }
            });
        }
        return this;
    }

    public View getBackground(int direction){
        return mBackgroundMap.get(direction);
    }




    public void updateView() {
        if(SwipeDirections.DIRECTION_NEUTRAL != visibleView && mBackgroundMap.get(visibleView) == null)
            return;
        translateBackgrounds();
        //contentView.setLayoutParams(new LayoutParams(mLayoutParamWidth, mLayoutParamHeight));
        contentView.measure(mLayoutParamWidth, mLayoutParamHeight);

        //int height = contentView.getHeight();
        final int heightContent = mContentViewHeight;

        if (getTranslationX() == 0) {
            //resetBackgrounds(0);
            this.getLayoutParams().height = mLayoutParamHeight;
            //contentView.getLayoutParams().height = mLayoutParamHeight;
            setLayoutParams(contentView, mLayoutParamWidth, mLayoutParamHeight);
            contentView.setAlpha(1.f);
        }

        for (int i = 0; i < mBackgroundMap.size(); i++)
            mBackgroundMap.valueAt(i).setVisibility(View.GONE);

        if(visibleView != SwipeDirections.DIRECTION_NEUTRAL) {
            View background = mBackgroundMap.get(visibleView);
            background.setVisibility(View.VISIBLE);

            // FADE IN / FADE OUT
            if(fadeOnTranslation) {
                background.setAlpha(getSwipeRatio());
                if (((visibleView == SwipeDirections.DIRECTION_NORMAL_LEFT || visibleView == SwipeDirections.DIRECTION_FAR_LEFT) && (fadeOnSlideLeft || slideInOffsetLeft == 0))
                    ||
                    ((visibleView == SwipeDirections.DIRECTION_NORMAL_RIGHT || visibleView == SwipeDirections.DIRECTION_FAR_RIGHT) && (fadeOnSlideRight || slideInOffsetRight == 0))) {
                    contentView.setAlpha(getSwipeRatioReversed());
                }
            }

            if (mBackgroundMatchParent.contains(visibleView)) {
                setLayoutHeight(background, heightContent);
            } else if (mBackgroundAutoLayout.contains(visibleView)) {
                setLayoutHeight(background, mLayoutParamHeight);
            } else {
                //Integer temp = mBackgroundHeight.get(visibleView);
                //int heightBackground = temp == null ? 0 : temp;
                int heightBackground = mBackgroundHeight.get(visibleView);
                if (heightBackground > heightContent) {
                    int tempHeight = getCurrentValueFromRatio(heightContent, heightBackground, getSwipeRatio(mExpandSwipeRatio));
                    setLayoutParams(contentView, mLayoutParamWidth, tempHeight);
                    background.getLayoutParams().height = tempHeight;
                    this.getLayoutParams().height = mLayoutParamHeight;
                } else {
                    int tempHeight = getCurrentValueFromRatio(mContentViewHeight, heightBackground, getSwipeRatio(heightBackground == 0 ? 1 : mExpandSwipeRatio));
                    background.getLayoutParams().height = tempHeight;
                    this.getLayoutParams().height = tempHeight;
                }
            }
        }
    }




    public int getCurrentValueFromRatio(int start, int end, float ratio) {
        return (int) (start + (end - start) * ratio);
    }


    public float getSwipeRatio(float expandSwipeRatio) {
        float ratio = Math.abs(this.getTranslationX()) / (this.getWidth() * expandSwipeRatio);
        if(ratio > 1.f)
            ratio = 1.f;
        return ratio;
    }

    private void resetBackgrounds(int height) {
        for (int i = 0; i < mBackgroundMap.size(); i++) {
            int key = mBackgroundMap.keyAt(i);
            View background = mBackgroundMap.get(key);
            background.setAlpha(1.f);
            setLayoutParams(background, MATCH, height);
            background.setVisibility(key == visibleView ? VISIBLE : INVISIBLE);
        }
        translateBackgrounds();
    }

    public static void setLayoutParams(View view, int width, int height) {
        if(view.getLayoutParams() != null) {
            view.getLayoutParams().width = width;
            view.getLayoutParams().height = height;
            view.requestLayout();
        }
    }


    public static void setLayoutHeight(View view, int height) {
        if(view.getLayoutParams() != null) {
            view.getLayoutParams().height = height;
            view.requestLayout();
        }
    }


    public void measureBackgroundsOnNextLayoutChange() {
        getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @SuppressWarnings("deprecation")
            @Override
            public void onGlobalLayout() {
                getViewTreeObserver().removeGlobalOnLayoutListener(this);
                measureBackgrounds();
            }
        });
    }

    public void measureBackground(int direction) {
        boolean isDismiss = mBackgroundType.get(direction) == LAYOUT_DISMISS;
        if(isDismiss) {
            mBackgroundHeight.put(direction, 0);
        } else {
            View background = mBackgroundMap.get(direction);
            background.measure(mWidthMeasureSpec, HEIGHT_MEASURE_SPEC);
            int height = background.getMeasuredHeight();
            mBackgroundHeight.put(direction, height);
        }
    }

    public void measureBackgrounds() {
        for (int i = 0; i < mBackgroundMap.size(); i++) {
            int direction = mBackgroundMap.keyAt(i);
            measureBackground(direction);
        }
    }

    public float getSwipeRatio() {
        boolean right = visibleView == SwipeDirections.DIRECTION_NORMAL_RIGHT || visibleView == SwipeDirections.DIRECTION_FAR_RIGHT;
        float ratio = 1.66f*Math.abs(getTranslationX()) / (getWidth() - (right ? slideInOffsetRight : slideInOffsetLeft));
        ratio = ratio > 1.f ? 1.f : ratio;
        return ratio;
    }

    public float getRealSwipeRatio() {
        boolean right = visibleView == SwipeDirections.DIRECTION_NORMAL_RIGHT || visibleView == SwipeDirections.DIRECTION_FAR_RIGHT;
        float ratio = Math.abs(getTranslationX()) / (getWidth() - (right ? slideInOffsetRight : slideInOffsetLeft));
        ratio = ratio > 1.f ? 1.f : ratio;
        return ratio;
    }

    public float getSwipeRatioReversed() {
        return 1-getSwipeRatio();
    }

    /**
     * Returns the current contentView of the Layout
     *
     * @return contentView of the Layout
     */
    public View getContentView() {
        return contentView;
    }

    /**
     * Move all backgrounds to the edge of the Layout so they can be swiped in
     */
    public void translateBackgrounds() {
        if (mBackgroundMap == null)
            return;
        //float ratio = fixedBackground ? getRealSwipeRatio() : 1.f;
        float ratio = 0.f;
        for (int i=0;i<mBackgroundMap.size();i++) {
            int key = mBackgroundMap.keyAt(i);
            View background = mBackgroundMap.valueAt(i);
            background.setTranslationX(-Integer.signum(key)*background.getWidth()*ratio);
        }
    }

    public void setVisibleView(int visibleView) {
        this.visibleView = visibleView;
    }

    public void resetViewPos() {
        setTranslationX(0);
        slideInView = SwipeDirections.DIRECTION_NEUTRAL;
        refreshVisibleView();
    }

    public void slideBack() {
        slideBack(null);
    }

    public void slideBack(final OnSlideBack listener) {
        AnimatorListenerAdapter animatorListenerAdapter = listener == null ? null : new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                resetOnEndAnim(listener, animation);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                resetOnEndAnim(listener, animation);
            }
        };
        animateTranslationX(0, animatorListenerAdapter);
        slideInView = SwipeDirections.DIRECTION_NEUTRAL;
    }

    private void resetOnEndAnim(OnSlideBack listener, Animator animation) {
        listener.onSlideBackEnd(animation);
        refreshVisibleView();
        resetBackgrounds(0);
        updateView();
    }

    private void slideIn(int direction, float velocity, boolean useVelocity, int translation, AnimatorListenerAdapter animatorListenerAdapter) {
        if (onSlideInListener != null)
            onSlideInListener.onSlideIn(this);
        boolean right = direction == SwipeDirections.DIRECTION_NORMAL_RIGHT || direction == SwipeDirections.DIRECTION_FAR_RIGHT;
        int translationFinal = right ? (translation-slideInOffsetRight) : -(translation-slideInOffsetLeft);
        setVisibleView(direction);
        animateTranslationX(translationFinal, velocity, useVelocity, animatorListenerAdapter);
        slideInView = direction;
        refreshVisibleView();
    }

    public void slideIn(int direction, int translation, AnimatorListenerAdapter animatorListenerAdapter) {
        slideIn(direction, 0.f, false, translation, animatorListenerAdapter);
    }

    public void slideIn(int direction, float velocity, int translation, AnimatorListenerAdapter animatorListenerAdapter) {
        slideIn(direction, velocity, true, translation, animatorListenerAdapter);
    }

    public boolean isSlideIn() {
        return slideInView != SwipeDirections.DIRECTION_NEUTRAL;
    }

    public boolean toggleSlide(int direction, int translation, AnimatorListenerAdapter animatorListenerAdapter) {
        boolean isSlideIn = isSlideIn();
        if(isSlideIn)
            slideBack();
        else
            slideIn(direction, translation, animatorListenerAdapter);
        return !isSlideIn;
    }

    private void animateTranslationX(int translationX, float velocity, boolean useVelocity, final AnimatorListenerAdapter animatorListenerAdapter) {
        int dur = mMinAnimDuration;
        if(useVelocity) {
            float distance = translationX - getTranslationX();
            int duration = (int) (distance * 1000 / velocity);
            if(duration > mMinAnimDuration) {
                dur = duration > mMaxAnimDuration ? mMaxAnimDuration : duration;
            }
        }
        ValueAnimator animator = ValueAnimator.ofFloat(getTranslationX(), translationX).setDuration(dur);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                setTranslationX((Float) animation.getAnimatedValue());
            }
        });
        if(animatorListenerAdapter != null)
            animator.addListener(animatorListenerAdapter);
        animator.start();
    }

    public void animateTranslationX(int translationX, AnimatorListenerAdapter animatorListenerAdapter) {
       animateTranslationX(translationX, 0.f, false, animatorListenerAdapter);
    }

    public void animateTranslationX(int translationX, float velocity, AnimatorListenerAdapter animatorListenerAdapter) {
       animateTranslationX(translationX, velocity, true, animatorListenerAdapter);
    }

    /**
     * Set a touch listener the SwipeViewGroup will watch: once the OnTouchListener is interested in
     * events, the SwipeViewGroup will stop propagating touch events to its children
     *
     * @param swipeTouchListener The OnTouchListener to watch
     * @return A reference to the layout so commands can be chained
     */
    public SwipeViewGroup setSwipeTouchListener(OnTouchListener swipeTouchListener) {
        this.swipeTouchListener = swipeTouchListener;
        return this;
    }

    public int getBackgroundType(int direction) {
        if(mBackgroundType != null) {
            Integer type = mBackgroundType.get(direction);
            return type == null ? -1 : type;
        }
        return -1;
    }

    @Override
    public Object getTag() {
        return contentView == null ? null : contentView.getTag();
    }

    @Override
    public void setTag(Object tag) {
        if(contentView != null) contentView.setTag(tag);
    }

    @Override
    public Object getTag(int key) {
        if(contentView != null)
            return contentView.getTag(key);
        return null;
    }

    @Override
    public void setTag(int key, Object tag) {
        if(contentView != null)
            contentView.setTag(key, tag);
    }

    /*
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // Start tracking the touch when a child is processing it
        Log.d(TAG,"onInterceptTouchEvent");
        return super.onInterceptTouchEvent(ev) || (swipeTouchListener != null && swipeTouchListener.onTouch(this, ev));
    }
    */

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        // Finish the swipe gesture: our parent will no longer do it if this function is called
        return swipeTouchListener == null || swipeTouchListener.onTouch(this, ev);
    }

    @Override
    public void setChecked(boolean checked) {
        this.isChecked = checked;
        if (contentView != null && contentView instanceof Checkable) {
            ((Checkable)contentView).setChecked(checked);
        }
    }

    @Override
    public boolean isChecked() {
        return isChecked;
    }

    @Override
    public void toggle() {
        this.setChecked(!isChecked);
    }

    public void setFixedBackground(boolean fixedBackground) {
        this.fixedBackground = fixedBackground;
    }

    public void setFadeOnTranslation(boolean fadeOnTranslation) {
        this.fadeOnTranslation = fadeOnTranslation;
    }

    public boolean isSliding() {
        return getTranslationX() != 0.f;
    }

    public int getSlideInOffsetRight() {
        return slideInOffsetRight;
    }

    public void setSlideInOffsetRight(int slideInOffsetRight) {
        this.slideInOffsetRight = slideInOffsetRight;
    }

    public int getSlideInOffsetLeft() {
        return slideInOffsetLeft;
    }

    public void setSlideInOffsetLeft(int slideInOffsetLeft) {
        this.slideInOffsetLeft = slideInOffsetLeft;
    }

    public boolean getFadeOnSlideLeft() {
        return fadeOnSlideLeft;
    }

    public void setFadeOnSlideLeft(boolean fadeOnSlideLeft) {
        this.fadeOnSlideLeft = fadeOnSlideLeft;
    }

    public boolean getFadeOnSlideRight() {
        return fadeOnSlideRight;
    }

    public void setFadeOnSlideRight(boolean fadeOnSlideRight) {
        this.fadeOnSlideRight = fadeOnSlideRight;
    }

    public interface OnSlideIn {
        void onSlideIn(SwipeViewGroup view);
    }

    public interface OnSlideBack {
        void onSlideBackEnd(Animator animation);
    }

    public interface OnSlidingListener {
        void onSliding(float translationX);
    }

    @Override
    public void setTranslationX(float translationX) {
        if (fixedBackground)
            contentView.setTranslationX(translationX);
        else
            super.setTranslationX(translationX);
        updateView();
        for (OnSlidingListener listener : onSlidingListeners) {
            if(listener != null)
                listener.onSliding(translationX);
        }
    }

    @Override
    public float getTranslationX() {
        return fixedBackground ? contentView.getTranslationX() : super.getTranslationX();
    }

    public void setOnSlideInListener(OnSlideIn onSlideInListener) {
        this.onSlideInListener = onSlideInListener;
    }

    public void setOnSlidingListener(OnSlidingListener onSlidingListener) {
        onSlidingListeners.remove(this.onSlidingListener);
        this.onSlidingListener = onSlidingListener;
        onSlidingListeners.add(this.onSlidingListener);
    }

    public void addOnSlidingListener(OnSlidingListener onSlidingListener) {
        this.onSlidingListeners.add(onSlidingListener);
    }

    public void removeOnSlidingListener(OnSlidingListener onSlidingListener) {
        this.onSlidingListeners.remove(onSlidingListener);
    }

    public void clearOnSlidingListener() {
        this.onSlidingListeners.clear();
    }

    public void measureContentViewHeight() {
        resetBackgrounds(0);
        getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                getViewTreeObserver().removeOnPreDrawListener(this);
                View parent = ((View) contentView.getParent());
                int width = 1000;
                if (parent != null)
                    width = parent.getWidth();

                contentView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), HEIGHT_MEASURE_SPEC);
                mContentViewHeight = contentView.getMeasuredHeight();
                return mViewIsInitialized;
            }
        });
    }

    public int getSlideInView() {
        return slideInView;
    }

    public void refreshVisibleView() {
        setVisibleView(slideInView);
    }

    public void setMinAnimDuration(int minAnimDuration) {
        this.mMinAnimDuration = minAnimDuration;
    }

}
