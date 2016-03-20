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

import android.util.SparseIntArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter that adds support for multiple swipe actions to your ListView
 *
 * Created by wdullaer on 04.06.14.
 * Modified by Gabriel Morin from Graphee on 11.16.2015
 */
public class SwipeViewAdapter extends WrappingAdapter implements SwipeViewTouchListener.ActionCallbacks {
    private static final String TAG = SwipeViewAdapter.class.getName();
    private SwipeViewTouchListener mTouchListener;
    private List<Integer> itemViewTypesWithoutSwipeList = new ArrayList<>();
    protected SwipeActionListener mSwipeActionListener;

    private boolean
            mFixedBackgrounds = false,
            canSlideIn = true,
            mFadeOut = false,
            mFadeOutLeft = true,
            mFadeOutRight = true,
            isFlinging = false;

    private float
            mFarSwipeFraction = 0.5f,
            mNormalSwipeFraction = 0.25f;

    private int animSlideDuration = 200;

    protected SparseIntArray
            mBackgroundResIds = new SparseIntArray(),
            mBackgroundType = new SparseIntArray();


    public SwipeViewAdapter(BaseAdapter baseAdapter){
        super(baseAdapter);
    }

    @Override
    public View getView(final int position, final View convertView, final ViewGroup parent){
        SwipeViewGroup output = (SwipeViewGroup) convertView;
        int itemViewType = getItemViewType(position);
        if (!itemViewTypesWithoutSwipeList.contains(itemViewType)) {
            if (output == null) {
                output = new SwipeViewGroup(parent.getContext());
                output.setFixedBackground(mFixedBackgrounds);
                output.setFadeOnTranslation(mFadeOut);
                output.setFadeOnSlideLeft(mFadeOutLeft);
                output.setFadeOnSlideRight(mFadeOutRight);
                for (int i = 0; i < mBackgroundResIds.size(); i++) {
                    int direction = mBackgroundResIds.keyAt(i);
                    int layoutId = mBackgroundResIds.valueAt(i);
                    int type = mBackgroundType.get(direction);
                    View bg = View.inflate(parent.getContext(), layoutId, null);
                    if(bg != null) {
                        output.addBackground(bg, direction, type);
                        onGetBackground(true, direction, position, bg, output);
                    }
                }
                output.setMinAnimDuration(animSlideDuration);
                output.setSwipeTouchListener(mTouchListener);
            } else {
                for (int i = 0; i < mBackgroundResIds.size(); i++) {
                    int direction = mBackgroundResIds.keyAt(i);
                    View bg = output.getBackground(direction);
                    if(bg != null) {
                        onGetBackground(false, direction, position, bg, output);
                    }
                }
            }
            output.measureBackgrounds();
            output.setContentView(super.getView(position, output.getContentView(), output));
        } else {
            output = new SwipeViewGroup(parent.getContext());
            output.setContentView(super.getView(position, output.getContentView(), output));
            output.setSwipeTouchListener(null);
        }
        output.refreshVisibleView();
        output.translateBackgrounds();
        return output;
    }

    public void onGetBackground(boolean isCreate, int direction, int position, View background, SwipeViewGroup parent) {

    }

    public void setItemViewTypesWithoutExpand(int... itemViewTypesWithoutExpand) {
        itemViewTypesWithoutSwipeList.clear();
        for(int i : itemViewTypesWithoutExpand) {
            itemViewTypesWithoutSwipeList.add(itemViewTypesWithoutExpand[i]);
        }
    }

    public static void measureBackgrounds(ListView listView) {
        for (int i = 0; i < listView.getChildCount(); i++)
            ((SwipeViewGroup) listView.getChildAt(i)).measureBackgrounds();
    }

    /**
     * SwipeViewTouchListener.ActionCallbacks callback
     * We just link it through to our own interface
     *
     * @param position the position of the item that was swiped
     * @return boolean indicating whether the item has actions
     */
    @Override
    public boolean hasActions(int position){
        return mSwipeActionListener != null && mSwipeActionListener.hasActions(position);
    }

    /**
     * SwipeViewTouchListener.ActionCallbacks callback
     * We just link it through to our own interface
     *
     * @param position The positions to perform the action on, sorted in descending  order
     *                 for convenience.
     * @param direction The type of swipe that triggered the action.
     */
    @Override
    public void onSwipeToDismiss(int position, int direction){
        if (mSwipeActionListener != null)
            mSwipeActionListener.onSwipeToDismiss(position, direction);
    }

    @Override
    public void onSwipeNormal(int position, int direction) {
        if (mSwipeActionListener != null)
            mSwipeActionListener.onSwipeNormal(position, direction);
    }

    public SwipeViewAdapter setSlideInOffset(int slideInOffset){
        if (mTouchListener != null)
            mTouchListener.setSlideInOffset(slideInOffset);
        return this;
    }

    public void slideInView(int visiblePosition) {
        if (mTouchListener != null)
            mTouchListener.slideInView(visiblePosition);
    }

    public boolean isViewSliding(int visiblePosition) {
        return mTouchListener != null && mTouchListener.isViewSliding(visiblePosition);
    }

    public void toggleSlideInView(int visiblePosition) {
        if (mTouchListener != null)
            mTouchListener.toggleSlideInView(visiblePosition);
    }

    public boolean slideBack() {
        return mTouchListener != null && mTouchListener.slideBack();
    }

    public int getSlideInViewPosition() {
        if (mTouchListener != null)
            return mTouchListener.getSlideInViewPosition();
        return -1;
    }

    public boolean hasSlideInView() {
        return mTouchListener != null && mTouchListener.hasSlideInView();
    }

    /**
     * Set whether items should have a fadeOut animation
     *
     * @param fadeOut true makes items fade out with a swipe (opacity -> 0)
     * @return A reference to the current instance so that commands can be chained
     */
    @SuppressWarnings("unused")
    public SwipeViewAdapter setFadeOut(boolean fadeOut) {
        this.mFadeOut = fadeOut;
        return this;
    }

    /**
     * Set whether items should have a fadeOut animation
     *
     * @param fadeOutLeft true makes items fade out with a left swipe (opacity -> 0)
     * @return A reference to the current instance so that commands can be chained
     */
    @SuppressWarnings("unused")
    public SwipeViewAdapter setFadeOutLeft(boolean fadeOutLeft){
        this.mFadeOutLeft = fadeOutLeft;
        return this;
    }

    /**
     * Set whether items should have a fadeOut animation
     *
     * @param fadeOutRight true makes items fade out with a right swipe (opacity -> 0)
     * @return A reference to the current instance so that commands can be chained
     */
    @SuppressWarnings("unused")
    public SwipeViewAdapter setFadeOutRight(boolean fadeOutRight){
        this.mFadeOutRight = fadeOutRight;
        return this;
    }

    /**
     * Set whether the backgrounds should be fixed or swipe in from the side
     * The default value for this property is false: backgrounds will swipe in
     *
     * @param fixedBackgrounds true for fixed backgrounds, false for swipe in
     */
    @SuppressWarnings("unused")
    public SwipeViewAdapter setFixedBackgrounds(boolean fixedBackgrounds){
        this.mFixedBackgrounds = fixedBackgrounds;
        return this;
    }

    public SwipeViewAdapter setCanSlideIn(boolean canSlideIn){
        this.canSlideIn = canSlideIn;
        if (mTouchListener != null)
            mTouchListener.setCanSlideIn(canSlideIn);
        return this;
    }

    /**
     * Set the fraction of the View Width that needs to be swiped before it is counted as a far swipe
     *
     * @param farSwipeFraction float between 0 and 1
     */
    @SuppressWarnings("unused")
    public SwipeViewAdapter setFarSwipeFraction(float farSwipeFraction) {
        if (farSwipeFraction < 0 || farSwipeFraction > 1) {
            throw new IllegalArgumentException("Must be a float between 0 and 1");
        }
        this.mFarSwipeFraction = farSwipeFraction;
        if (mTouchListener != null)
            mTouchListener.setFarSwipeFraction(farSwipeFraction);
        return this;
    }

    /**
     * Set the fraction of the View Width that needs to be swiped before it is counted as a normal swipe
     *
     * @param normalSwipeFraction float between 0 and 1
     */
    @SuppressWarnings("unused")
    public SwipeViewAdapter setNormalSwipeFraction(float normalSwipeFraction) {
        if (normalSwipeFraction < 0 || normalSwipeFraction > 1) {
            throw new IllegalArgumentException("Must be a float between 0 and 1");
        }
        this.mNormalSwipeFraction = normalSwipeFraction;
        if (mTouchListener != null)
            mTouchListener.setNormalSwipeFraction(normalSwipeFraction);
        return this;
    }

    /**
     * Enable a swipe direction (none are enabled by default)
     * Automatically adds on addBackground
     *
     * @param direction Integer const from SwipeDirections
     */
    public SwipeViewAdapter addEnabledDirection(Integer direction, Integer type) {
        mTouchListener.addEnabledDirection(direction);
        mBackgroundType.put(direction, type);
        return this;
    }

    /**
     * We need the ListView to be able to modify it's OnTouchListener
     *
     * @param listView the ListView to which the adapter will be attached
     * @return A reference to the current instance so that commands can be chained
     */
    public SwipeViewAdapter setListView(ListView listView) {
        mTouchListener = new SwipeViewTouchListener(listView, this);
        listView.setOnTouchListener(mTouchListener);
        listView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView absListView, int scrollState) {
                mTouchListener.onScrollStateChanged(absListView, scrollState);
                isFlinging = scrollState == SCROLL_STATE_FLING;
                if (mSwipeActionListener != null)
                    mSwipeActionListener.onScrollStateChanged(absListView, scrollState);
            }

            @Override
            public void onScroll(AbsListView absListView, int i, int i1, int i2) {
                if (mSwipeActionListener != null)
                    mSwipeActionListener.onScroll(absListView, i, i1, i2);
            }
        });
        listView.setClipChildren(false);
        mTouchListener.setOnSwipeActionTouchListener(new SwipeViewTouchListener.OnSwipeActionTouchListener() {
            @Override
            public void onSliding(SwipeViewGroup swipeViewGroup, int position) {
                if (mSwipeActionListener != null)
                    mSwipeActionListener.onSliding(swipeViewGroup, position);
                slideInView(-1);
            }
        });
        mTouchListener.setNormalSwipeFraction(mNormalSwipeFraction);
        mTouchListener.setFarSwipeFraction(mFarSwipeFraction);
        return this;
    }

    public boolean isFlinging() {
        return isFlinging;
    }

    /**
     * Add a background image for a certain callback. The key for the background must be one of the
     * directions from the SwipeDirections class.
     *
     * @param key the identifier of the callback for which this resource should be shown
     * @param layoutId the resource Id of the background to add
     * @param type int -1 -2 -3 if set to wrap_content, the row height will be resized to the background height when shown, if set to dismiss, the row will shrink to 0
     * @return A reference to the current instance so that commands can be chained
     */
    public SwipeViewAdapter addBackground(int key, int layoutId, int type){
        if(SwipeDirections.getAllDirections().contains(key)) {
            mBackgroundResIds.put(key, layoutId);
            addEnabledDirection(key, type);
        }
        return this;
    }

    public SwipeViewAdapter addBackground(int key, int resId) {
        return addBackground(key, resId, SwipeViewGroup.LAYOUT_MATCH_PARENT);
    }

    @Override
    public void notifyDataSetChanged() {
        super.notifyDataSetChanged();
        //slideBack();
    }

    /**
     * Set the listener for swipe events
     *
     * @param mSwipeActionListener class listening to swipe events
     * @return A reference to the current instance so that commands can be chained
     */
    public SwipeViewAdapter setSwipeActionListener(SwipeActionListener mSwipeActionListener){
        this.mSwipeActionListener = mSwipeActionListener;
        return this;
    }

    public void setAnimSlideDuration(int animSlideDuration) {
        this.animSlideDuration = animSlideDuration;
    }

    /**
     * Interface that listeners of swipe events should implement
     */
    public interface SwipeActionListener{
        boolean hasActions(int position);
        void onSwipeToDismiss(int position, int direction);
        void onSwipeNormal(int position, int direction);
        void onScrollStateChanged(AbsListView absListView, int scrollState);
        void onScroll(AbsListView absListView, int i, int i1, int i2);
        void onSliding(SwipeViewGroup swipeViewGroup, int position);
    }
}
