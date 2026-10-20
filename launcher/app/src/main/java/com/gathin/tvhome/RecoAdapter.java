package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.Outline;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Collections;
import java.util.List;

/** The cards of the recommendations row (see {@link RecoStore}). */
final class RecoAdapter extends RecyclerView.Adapter<RecoAdapter.Holder> {

    interface Listener {
        void onRecoOpen(RecoStore.Item item);

        /** A key pressed on a card. Return true when handled; false leaves Left/Right to the list. */
        boolean onRecoKey(int position, int keyCode);
    }

    private static final float FOCUS_SCALE = 1.08f;

    private final RecoStore store;
    private final Listener listener;
    private List<RecoStore.Item> items = Collections.emptyList();

    RecoAdapter(RecoStore store, Listener listener) {
        this.store = store;
        this.listener = listener;
    }

    void setItems(List<RecoStore.Item> list) {
        items = list;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_reco, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        RecoStore.Item item = items.get(position);
        h.title.setText(item.title);
        // The app above the title, unless the title already is the app (an entry that is just "you used this app").
        boolean showApp = item.appLabel != null && !item.appLabel.equals(item.title);
        h.app.setVisibility(showApp ? View.VISIBLE : View.GONE);
        h.app.setText(item.appLabel);
        h.url = item.imageUrl;
        Bitmap cached = store.cachedImage(item.imageUrl);
        if (cached != null) {
            h.image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            h.image.setImageBitmap(cached);
        } else {
            // Until the poster arrives (or when there is none) the card shows the app's own banner.
            h.image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            h.image.setImageDrawable(store.appArt(item.pkg));
        }
        if (item.imageUrl != null && cached == null) {
            final String url = item.imageUrl;
            store.loadImage(url, bitmap -> {
                if (url.equals(h.url)) h.image.setImageBitmap(bitmap);
            });
        }
        h.progress.setVisibility(item.progress > 0f ? View.VISIBLE : View.GONE);
        if (item.progress > 0f) {
            ViewGroup.LayoutParams lp = h.progress.getLayoutParams();
            lp.width = Math.max(1, Math.round(h.card.getLayoutParams().width * item.progress));
            h.progress.setLayoutParams(lp);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    final class Holder extends RecyclerView.ViewHolder {
        final View card;
        final ImageView image;
        final TextView title;
        final TextView app;
        final View progress;
        String url;

        Holder(View v) {
            super(v);
            card = v.findViewById(R.id.recoCard);
            image = v.findViewById(R.id.recoImage);
            title = v.findViewById(R.id.recoTitle);
            app = v.findViewById(R.id.recoApp);
            progress = v.findViewById(R.id.recoProgress);

            final float density = v.getResources().getDisplayMetrics().density;
            card.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 12 * density);
                }
            });
            card.setClipToOutline(true);

            card.setOnClickListener(view -> {
                int pos = getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) listener.onRecoOpen(items.get(pos));
            });
            card.setOnKeyListener((view, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                int pos = getBindingAdapterPosition();
                return pos != RecyclerView.NO_POSITION && listener.onRecoKey(pos, keyCode);
            });
            // Like the app tiles, the card grows at once when it gets focus (no animation).
            card.setOnFocusChangeListener((view, focused) -> {
                view.setScaleX(focused ? FOCUS_SCALE : 1f);
                view.setScaleY(focused ? FOCUS_SCALE : 1f);
            });
        }
    }
}
