/*
* Copyright 2013 Google Inc
* Copyright 2014 Wouter Dullaert
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
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
import android.graphics.Rect;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewPropertyAnimator;
import android.widget.AbsListView;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link View.OnTouchListener} that makes the list items in a {@link ListView}
 * dismissable. {@link ListView} is given special treatment because by default it handles touches
 * for its list items... i.e. it's in charge of drawing the pressed state (the list selector),
 * handling list item clicks, etc.
 *
 *
 * <pre>
 * SwipeViewTouchListener touchListener =
 * new SwipeViewTouchListener(
 * listView,
 * new SwipeViewTouchListener.OnDismissCallback() {
 * public void onSwipeToDismiss(ListView listView, int[] reverseSortedPositions) {
 * for (int position : reverseSortedPositions) {
 * adapter.remove(adapter.getItem(position));
 * }
 * adapter.notifyDataSetChanged();
 * }
 * });
 * listView.setOnTouchListener(touchListener);
 * listView.setOnScrollListener(touchListener.makeScrollListener());
 * </pre>
 *
 * <p>This class Requires API level 12 or later due to use of {@link
 * ViewPropertyAnimator}.</p>
 */
public class SwipeViewTouchListener implements View.OnTouchListener {
    private static final String TAG = SwipeViewTouchListener.class.getName();

    private float
            mFarSwipeFraction = 0.5f,
            mNormalSwipeFraction = 0.25f,
            mDownX,
            mDownY,
            mLatestDeltaX = 0;

    private boolean
            mIsDown,
            mIsFadeOut,
            mIsMoving,
            mIsEnabled,
            mIsFar,
            mIsPerformingDismiss = false;

    private int
            mViewWidth = 1, // 1 and not 0 to prevent dividing by zero
            mSlop,
            mMinFlingVelocity,
            mMaxFlingVelocity,
            mSwipingSlop,
            mDownPosition,
            mDirection,
            mDirectionTemporary,
            mSlideInView = -1,
            mSlideInOffset = 0;

    private ListView mListView;
    private ActionCallbacks mCallbacks;
    private VelocityTracker mVelocityTracker;
    private View mDownView;
    private SwipeViewGroup mDownViewGroup;
    private List<Integer> mEnabledDirections = new ArrayList<>();
    private OnSwipeActionTouchListener onSwipeActionTouchListener;

    /**
     * The callback interface used by {@link SwipeViewTouchListener} to inform its client
     * about a successful dismissal of one or more list item positions.
     */
    public interface ActionCallbacks {
        /**
         * Called to determine whether the given position can be dismissed.
         *
         * @param position the position of the item that was swiped
         * @return boolean indicating whether the item has actions
         */
        boolean hasActions(int position);

        /**
         * Called after the dismiss or reappear animation of a swiped item has finished.
         *
         * @param position The position to perform the action on, sorted in descending  order
         *                 for convenience.
         * @param direction The type of swipe that triggered the action
         */
        void onSwipeToDismiss(int position, int direction);

        void onSwipeNormal(int position, int direction);
    }

    /**
     * Constructs a new swipe-to-dismiss touch listener for the given list view.
     *
     * @param listView The list view whose items should be dismissable.
     * @param callbacks The callback to trigger when the user has indicated that she would like to
     * dismiss one or more list items.
     */
    public SwipeViewTouchListener(ListView listView, ActionCallbacks callbacks) {
        ViewConfiguration vc = ViewConfiguration.get(listView.getContext());
        mSlop = vc.getScaledTouchSlop();
        mMinFlingVelocity = vc.getScaledMinimumFlingVelocity() * 16;
        mMaxFlingVelocity = vc.getScaledMaximumFlingVelocity();
        mListView = listView;
        mCallbacks = callbacks;
    }

    public void setSlideInOffset(int mSlideInOffset) {
        this.mSlideInOffset = mSlideInOffset;
    }

    /**
     * Enables or disables (pauses or resumes) watching for swipe-to-dismiss gestures.
     *
     * @param enabled Whether or not to watch for gestures.
     */
    public void setEnabled(boolean enabled) {
        mIsEnabled = enabled;
    }

    public void setOnSwipeActionTouchListener(OnSwipeActionTouchListener onSwipeActionTouchListener) {
        this.onSwipeActionTouchListener = onSwipeActionTouchListener;
    }

    /**
     * Returns an {@link AbsListView.OnScrollListener} to be added to the {@link
     * ListView} using {@link ListView#setOnScrollListener(AbsListView.OnScrollListener)}.
     * If a scroll listener is already assigned, the caller should still pass scroll changes through
     * to this listener. This will ensure that this {@link SwipeViewTouchListener} is
     * paused during list view scrolling.</p>
     *
     * @see SwipeViewTouchListener
     */

    public void onScrollStateChanged(AbsListView absListView, int scrollState) {
        setEnabled(scrollState != AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL);
        slideInView(-1);
    }
    /**
     * Set whether the list item should fade out when swiping or not.
     * The default value for this property is false
     *
     * @param fadeOut true for a fade out, false for no fade out.
     */
    protected void setFadeOut(boolean fadeOut){
        mIsFadeOut = fadeOut;
    }

    /**
     * Set the fraction of the View Width that needs to be swiped before it is counted as a far swipe
     *
     * @param farSwipeFraction float between 0 and 1, should be equal to or greater than normalSwipeFraction
     */
    protected void setFarSwipeFraction(float farSwipeFraction) {
        mFarSwipeFraction = farSwipeFraction;
    }

    /**
     * Set the fraction of the View Width that needs to be swiped before it is counted as a normal swipe
     *
     * @param normalSwipeFraction float between 0 and 1, should be equal to or less than farSwipeFraction
     */
    protected void setNormalSwipeFraction(float normalSwipeFraction) {
        mNormalSwipeFraction = normalSwipeFraction;
    }

    /**
     * Enable a swipe direction (none are enabled by default)
     * Automatically adds on addBackground
     *
     * @param direction Integer const from SwipeDirections
     */
    protected void addEnabledDirection(Integer direction) {
        if(!mEnabledDirections.contains(direction))
            mEnabledDirections.add(direction);
    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {
        if(mIsPerformingDismiss)
            return false;
        if (mViewWidth < 2)
            mViewWidth = mListView.getWidth();

        //int index = event.getActionIndex();
        //int pointerId = event.getPointerId(index);
        int action = event.getActionMasked();

        switch (action) {

            case MotionEvent.ACTION_DOWN: {
                // TODO: ensure this is a finger, and set a flag
                if (!mIsEnabled || mIsDown)
                    return false;
                cancel();
                reset();
                setDownView(getClickedView(event));
                if (mDownView != null) {
                    mDownPosition = mListView.getPositionForView(mDownView);
                    if (mCallbacks.hasActions(mDownPosition)) {
                        mIsDown = true;
                        mDownX = event.getRawX();
                        mDownY = event.getRawY();
                        initVelocityTracker();
                        addMovement(event);
                    } else {
                        reset();
                    }
                }
                return false;
            }

            case MotionEvent.ACTION_CANCEL: {
                cancel();
                reset();
                break;
            }

            case MotionEvent.ACTION_UP: {
                if (mVelocityTracker == null || !mEnabledDirections.contains(mDirection) || mDownPosition == ListView.INVALID_POSITION || mDownViewGroup.isSlideIn()) {
                    cancel();
                    reset();
                    break;
                }

                addMovementAndCompute(event);
                float deltaX = event.getRawX() - mDownX;
                boolean isSwipe = Math.abs(deltaX) > (mViewWidth * mNormalSwipeFraction);
                boolean validate = mDirection == mDirectionTemporary && mIsMoving && (isSwipe || isFling());
                if (validate) {
                    if(canDismiss()) {
                        dismiss(isSwipe ? deltaX > 0 : mVelocityTracker.getXVelocity() > 0);
                    } else {
                        mCallbacks.onSwipeNormal(mDownPosition, mDirection);
                        slideInView(mDownPosition);
                    }
                } else {
                    cancel();
                }
                reset();
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                if (mVelocityTracker == null || !mIsEnabled || mDownViewGroup.isSlideIn()) {
                    cancel();
                    reset();
                    break;
                }

                addMovement(event);
                float deltaX = event.getRawX() - mDownX;
                float deltaY = event.getRawY() - mDownY;

                if (mIsMoving) {
                    mIsFar = mDirection*deltaX >= 0 && Math.abs(deltaX) > mViewWidth*mFarSwipeFraction;
                    mDirection = getDirection(mIsFar, deltaX > 0);
                    mDirectionTemporary = getDirection(mIsFar, deltaX - mLatestDeltaX > 0);
                    mLatestDeltaX = deltaX;
                    if(mEnabledDirections.contains(mDirection)) {
                        mDownViewGroup.setVisibleView(mDirection);
                        mDownView.setTranslationX(deltaX - mSwipingSlop);
                        if(onSwipeActionTouchListener != null)
                            onSwipeActionTouchListener.onSliding(mDownViewGroup, mDownPosition);
                        if(mIsFadeOut)
                            mDownView.setAlpha(Math.max(0f, Math.min(1f, 1f - 2f * Math.abs(deltaX) / mViewWidth)));
                        return true;
                    }
                } else if (Math.abs(deltaX) > mSlop && Math.abs(deltaY) < Math.abs(deltaX) / 2) {
                    mIsMoving = true;
                    mSwipingSlop = (deltaX > 0 ? mSlop : -mSlop);
                    cancelListViewEvent(event);
                }
                break;
            }
        }
        return false;
    }

    private int getDirection(boolean isFarSwipe, boolean isRight) {
        return isFarSwipe ?
                (isRight ? SwipeDirections.DIRECTION_FAR_RIGHT     : SwipeDirections.DIRECTION_FAR_LEFT):
                (isRight ? SwipeDirections.DIRECTION_NORMAL_RIGHT  : SwipeDirections.DIRECTION_NORMAL_LEFT);
    }

    private void reset() {
        if(mVelocityTracker != null)
            mVelocityTracker.recycle();
        mVelocityTracker = null;
        mDownView = null;
        mDownViewGroup = null;
        mDownPosition = ListView.INVALID_POSITION;
        mDownX = 0;
        mDownY = 0;
        mDirection = SwipeDirections.DIRECTION_NEUTRAL;
        mIsMoving = false;
        mIsFar = false;
        mIsDown = false;
    }

    private void cancel() {
        if(mDownViewGroup != null && (mDownViewGroup.isSlideIn() || mDownViewGroup.getTranslationX() != 0))
            mDownViewGroup.slideBack(null);
    }

    private void initVelocityTracker() {
        if(mVelocityTracker == null)
            mVelocityTracker = VelocityTracker.obtain();
        else
            mVelocityTracker.clear();
    }

    private void addMovement(MotionEvent motionEvent, boolean compute) {
        if (mVelocityTracker == null)
            return;

        mVelocityTracker.addMovement(motionEvent);
        if(compute)
            mVelocityTracker.computeCurrentVelocity(1000);
    }

    private void addMovement(MotionEvent motionEvent) {
       addMovement(motionEvent, false);
    }

    private void addMovementAndCompute(MotionEvent motionEvent) {
       addMovement(motionEvent, true);
    }

    private void setDownView(View view) {
        try {
            mDownViewGroup = (SwipeViewGroup) view;
            mDownView = view;
        }
        catch(Exception e) {
            mDownView = view;
        }
    }

    private void cancelListViewEvent(MotionEvent motionEvent) {
        // Cancel ListView's touch (un-highlighting the item)
        mListView.requestDisallowInterceptTouchEvent(true);
        MotionEvent cancelEvent = MotionEvent.obtain(motionEvent);
        cancelEvent.setAction(MotionEvent.ACTION_CANCEL |
                (motionEvent.getActionIndex()
                        << MotionEvent.ACTION_POINTER_INDEX_SHIFT));
        mListView.onTouchEvent(cancelEvent);
        cancelEvent.recycle();
    }

    private void dismiss(boolean dismissRight) {
        final View downView = mDownView; // mDownView gets null'd before animation ends
        final int downPosition = mDownPosition;
        final int direction = mDirection;
        final int translation = dismissRight ? mViewWidth : -mViewWidth;
        mIsPerformingDismiss = true;
        mDownViewGroup.animateTranslationX(
                (int) (translation*0.997f), // HACK to prevent bug with view flashing big quickly
                //mVelocityTracker.getXVelocity(pointerId),
                new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        performDismiss(downView, downPosition, direction);
                        mIsPerformingDismiss = false;
                    }
                });
    }

    // Find the child view that was touched (perform a hit test)
    private View getClickedView(MotionEvent motionEvent) {
        Rect rect = new Rect();
        int childCount = mListView.getChildCount();
        int[] listViewCoordinates = new int[2];
        mListView.getLocationOnScreen(listViewCoordinates);
        int x = (int) motionEvent.getRawX() - listViewCoordinates[0];
        int y = (int) motionEvent.getRawY() - listViewCoordinates[1];
        View child;
        for (int i = 0; i < childCount; i++) {
            child = mListView.getChildAt(i);
            child.getHitRect(rect);
            if (rect.contains(x, y)) {
                return child;
            }
        }
        return null;
    }

    private boolean isDownView(MotionEvent motionEvent) {
        View view = getClickedView(motionEvent);
        if(view == null)
            return false;
        return mListView.getPositionForView(view) == mDownPosition;
    }

    private boolean canDismiss() {
        return mDownViewGroup.getBackgroundType(mDirection) == SwipeViewGroup.LAYOUT_DISMISS;
    }

    private boolean isFling() {
        float absVelocityX = Math.abs(mVelocityTracker.getXVelocity());
        float absVelocityY = Math.abs(mVelocityTracker.getYVelocity());
        return mMinFlingVelocity <= absVelocityX && absVelocityX <= mMaxFlingVelocity && absVelocityY < absVelocityX;
    }

    private void performDismiss(View dismissView, int dismissPosition, final int direction) {
        mCallbacks.onSwipeToDismiss(dismissPosition, direction);

        Log.d(TAG, "SET TRANSLATION TO 0");
        mDownPosition = ListView.INVALID_POSITION; // Reset mDownPosition to avoid MotionEvent.ACTION_UP trying to start a dismiss
        dismissView.setAlpha(1f);
        dismissView.setTranslationX(0);
/*
            // Send a cancel event
                    long time = SystemClock.uptimeMillis();
                    MotionEvent cancelEvent = MotionEvent.obtain(time, time,
                            MotionEvent.ACTION_CANCEL, 0, 0, 0);
                    mListView.dispatchTouchEvent(cancelEvent);
*/
    }

    public void slideInView(int position) {
        if (mListView != null) {
            mSlideInView = mSlideInView == position ? -1 : position;
            int first = mListView.getFirstVisiblePosition();
            for (int i = 0; i < mListView.getChildCount(); i++) {
                int viewPosition = first + i;
                if(mListView.getChildAt(i) instanceof SwipeViewGroup) {
                    final SwipeViewGroup view = (SwipeViewGroup) mListView.getChildAt(i);
                    if (view.isSlideIn()) {
                        view.slideBack(new SwipeViewGroup.OnSlideBack() {
                            @Override
                            public void onSlideBackEnd(Animator animation) {
                                view.setVisibleView(SwipeDirections.DIRECTION_NEUTRAL);
                            }
                        });
                    } else if (viewPosition == mSlideInView) {
                        int translationX = view.getWidth() - mSlideInOffset;
                        view.slideIn(SwipeDirections.DIRECTION_NORMAL_RIGHT, translationX, null);

                    }
                }
            }
        }
    }

    public int getSlideInViewPosition() {
        return mSlideInView;
    }

    public boolean hasSlideInView() {
        return mSlideInView != -1;
    }

    public interface OnSwipeActionTouchListener {
        void onSliding(SwipeViewGroup swipeViewGroup, int position);
    }
}