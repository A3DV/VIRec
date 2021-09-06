package io.a3dv.VIRec;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Locale;

import io.a3dv.VIRec.ImuViewContent.SingleAxis;
import io.a3dv.VIRec.ImuViewFragment.OnListFragmentInteractionListener;

public class ImuRecyclerViewAdapter extends RecyclerView.Adapter<ImuRecyclerViewAdapter.ViewHolder> {
    private final List<SingleAxis> mValues;
    private final OnListFragmentInteractionListener mListener;

    public ImuRecyclerViewAdapter(List<SingleAxis> items, OnListFragmentInteractionListener listener) {
        mValues = items;
        mListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext()).inflate(
                R.layout.imu_fragment, parent, false);
        return new ViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        holder.mItem = mValues.get(position);
        holder.mIdView.setText(holder.mItem.id);
        holder.mContentView.setText(holder.mItem.content);
        holder.mUnitView.setText(FileHelper.fromHtml(holder.mItem.unit));
        holder.mView.setOnClickListener(v -> {
            if (null != mListener) {
                // Notify the active callbacks interface (the activity, if the
                // fragment is attached to one) that an item has been selected.
                mListener.onListFragmentInteraction(holder.mItem);
            }
        });
    }

    @Override
    public int getItemViewType(int position) {
        return R.layout.imu_fragment;
    }

    @Override
    public int getItemCount() {
        return mValues.size();
    }

    public void updateListItem(int position, float value) {
        mValues.get(position).content = String.format(Locale.US, "%.3f", value);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public final View mView;
        public final TextView mIdView;
        public final TextView mContentView;
        public final TextView mUnitView;
        public SingleAxis mItem;

        public ViewHolder(View view) {
            super(view);
            mView = view;
            mIdView = view.findViewById(R.id.item_number);
            mContentView = view.findViewById(R.id.content);
            mUnitView = view.findViewById(R.id.unit);
        }

        @NonNull
        @Override
        public String toString() {
            return super.toString() + " '" + mContentView.getText() + "'";
        }
    }
}
