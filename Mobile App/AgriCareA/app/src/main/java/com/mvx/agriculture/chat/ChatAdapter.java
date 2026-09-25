package com.mvx.agriculture.chat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.mvx.agriculture.R;

import java.util.ArrayList;
import java.util.List;

/** Renders the transcript as left/right bubbles, plus a typing row while waiting. */
public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.BubbleHolder> {

    private static final int TYPE_USER = 0;
    private static final int TYPE_BOT = 1;
    private static final int TYPE_TYPING = 2;

    private final List<ChatMessage> messages = new ArrayList<>();
    private boolean typing;

    public List<ChatMessage> messages() {
        return messages;
    }

    public void add(ChatMessage message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    /** Shows or hides the "thinking" row at the bottom. */
    public void setTyping(boolean typing) {
        if (this.typing == typing) {
            return;
        }
        this.typing = typing;
        if (typing) {
            notifyItemInserted(messages.size());
        } else {
            notifyItemRemoved(messages.size());
        }
    }

    /** Index of the last row, for scrolling. */
    public int lastPosition() {
        return getItemCount() - 1;
    }

    @Override
    public int getItemCount() {
        return messages.size() + (typing ? 1 : 0);
    }

    @Override
    public int getItemViewType(int position) {
        if (position >= messages.size()) {
            return TYPE_TYPING;
        }
        return messages.get(position).role() == ChatMessage.Role.USER ? TYPE_USER : TYPE_BOT;
    }

    @NonNull
    @Override
    public BubbleHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout;
        if (viewType == TYPE_USER) {
            layout = R.layout.item_message_user;
        } else if (viewType == TYPE_BOT) {
            layout = R.layout.item_message_bot;
        } else {
            layout = R.layout.item_message_typing;
        }
        View view = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new BubbleHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BubbleHolder holder, int position) {
        if (position < messages.size() && holder.text != null) {
            holder.text.setText(messages.get(position).text());
        }
    }

    static class BubbleHolder extends RecyclerView.ViewHolder {
        final TextView text;

        BubbleHolder(@NonNull View itemView) {
            super(itemView);
            text = itemView.findViewById(R.id.messageText);
        }
    }
}
