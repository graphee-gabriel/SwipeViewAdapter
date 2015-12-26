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
    private boolean
            mFadeOut = false,
            mIsFlinging = false;
    private float
            mFarSwipeFraction = 0.5f,
            mNormalSwipeFraction = 0.25f;
    protected SparseIntArray
            mBackgroundResIds = new SparseIntArray(),
            mBackgroundType = new SparseIntArray();

    private List<Integer> itemViewTypesWithoutExpandList = new ArrayList<>();

    protected SwipeActionListener mSwipeActionListener;

    public SwipeViewAdapter(BaseAdapter baseAdapter){
        super(baseAdapter);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent){
        SwipeViewGroup output = (SwipeViewGroup) convertView;

        int itemViewType = getItemViewType(position);
        if (!itemViewTypesWithoutExpandList.contains(itemViewType)) {
            if (output == null) {
                output = new SwipeViewGroup(parent.getContext());
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
                output.setSwipeTouchListener(mTouchListener);
            } else {
                for (int i = 0; i < mBackgroundResIds.size(); i++) {
                    int direction = mBackgroundResIds.keyAt(i);
                    View bg = output.getBackground(direction);
                    if(bg != null)
                        onGetBackground(false, direction, position, bg, output);
                }
            }

            output.setContentView(super.getView(position, output.getContentView(), output));
            if (mSwipeActionListener != null)
                mSwipeActionListener.onGetView(output);
        } else {
            output = new SwipeViewGroup(parent.getContext());
            output.setContentView(super.getView(position, output.getContentView(), output));
            output.setSwipeTouchListener(null);
        }
        return output;
    }

    public void onGetBackground(boolean isCreate, int direction, int position, View background, SwipeViewGroup parent) {

    }

    public void setItemViewTypesWithoutExpand(int... itemViewTypesWithoutExpand) {
        itemViewTypesWithoutExpandList.clear();
        for(int i : itemViewTypesWithoutExpand) {
            itemViewTypesWithoutExpandList.add(itemViewTypesWithoutExpand[i]);
        }
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
     * @param mFadeOut true makes items fade out with a swipe (opacity -> 0)
     * @return A reference to the current instance so that commands can be chained
     */
    @SuppressWarnings("unused")
    public SwipeViewAdapter setFadeOut(boolean mFadeOut){
        this.mFadeOut = mFadeOut;
        if (mTouchListener != null)
            mTouchListener.setFadeOut(mFadeOut);
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
                mIsFlinging = scrollState == SCROLL_STATE_FLING;
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
            }
        });
        mTouchListener.setFadeOut(mFadeOut);
        mTouchListener.setNormalSwipeFraction(mNormalSwipeFraction);
        mTouchListener.setFarSwipeFraction(mFarSwipeFraction);
        return this;
    }

    public boolean ismIsFlinging() {
        return mIsFlinging;
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

    /**
     * Interface that listeners of swipe events should implement
     */
    public interface SwipeActionListener{
        boolean hasActions(int position);
        void onSwipeToDismiss(int position, int direction);
        void onSwipeNormal(int position, int direction);
        void onScrollStateChanged(AbsListView absListView, int scrollState);
        void onScroll(AbsListView absListView, int i, int i1, int i2);
        void onGetView(View view);
        void onSliding(SwipeViewGroup swipeViewGroup, int position);
    }
}
