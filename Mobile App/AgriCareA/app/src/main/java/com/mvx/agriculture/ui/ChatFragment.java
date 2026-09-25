package com.mvx.agriculture.ui;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mvx.agriculture.LocaleManager;
import com.mvx.agriculture.MainShellActivity;
import com.mvx.agriculture.R;
import com.mvx.agriculture.chat.ChatAdapter;
import com.mvx.agriculture.chat.ChatMessage;
import com.mvx.agriculture.chat.NvidiaChatClient;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;

/** Conversation with AgriBot, answering in whichever language the app is set to. */
public class ChatFragment extends Fragment {

    private NvidiaChatClient client;
    private ChatAdapter adapter;
    private RecyclerView chatList;
    private View emptyState;
    private TextInputEditText userInput;
    private TextInputLayout inputLayout;
    private MaterialButton sendButton;
    private boolean awaitingReply;

    private final ActivityResultLauncher<Intent> speechLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() != android.app.Activity.RESULT_OK
                        || result.getData() == null) {
                    return;
                }
                ArrayList<String> spoken =
                        result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                if (spoken != null && !spoken.isEmpty()) {
                    userInput.setText(spoken.get(0));
                    userInput.setSelection(spoken.get(0).length());
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_chat, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        client = new NvidiaChatClient();

        chatList = view.findViewById(R.id.chatList);
        emptyState = view.findViewById(R.id.emptyState);
        userInput = view.findViewById(R.id.user_input);
        inputLayout = view.findViewById(R.id.inputLayout);
        sendButton = view.findViewById(R.id.send_button);

        adapter = new ChatAdapter();
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        layoutManager.setStackFromEnd(true);
        chatList.setLayoutManager(layoutManager);
        chatList.setAdapter(adapter);

        sendButton.setOnClickListener(v -> send());
        userInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                send();
                return true;
            }
            return false;
        });
        view.findViewById(R.id.speek).setOnClickListener(v -> startSpeechInput());

        if (!NvidiaChatClient.hasApiKey()) {
            inputLayout.setError(getString(R.string.chat_no_key));
        }

        // Arriving from a scan or the encyclopedia: ask about that finding straight away.
        Bundle args = getArguments();
        String disease = args == null ? null : args.getString("disease");
        String prefill = args == null ? null : args.getString("prefill");
        if (!TextUtils.isEmpty(prefill)) {
            userInput.setText(prefill);
        } else if (!TextUtils.isEmpty(disease)) {
            userInput.setText(getString(R.string.chat_prefill, disease));
        }
    }

    private void send() {
        String message = userInput.getText() == null ? "" : userInput.getText().toString().trim();
        if (TextUtils.isEmpty(message) || awaitingReply) {
            return;
        }

        inputLayout.setError(null);
        userInput.setText("");
        showTranscript();

        adapter.add(new ChatMessage(ChatMessage.Role.USER, message));
        setAwaitingReply(true);
        scrollToEnd();

        client.send(adapter.messages(), LocaleManager.currentEnglishName(requireContext()),
                new NvidiaChatClient.ResponseListener() {
                    @Override
                    public void onReply(String reply) {
                        if (!isAdded()) {
                            return;
                        }
                        setAwaitingReply(false);
                        adapter.add(new ChatMessage(ChatMessage.Role.ASSISTANT, reply));
                        scrollToEnd();
                    }

                    @Override
                    public void onError(String message, boolean isNetworkFailure) {
                        if (!isAdded()) {
                            return;
                        }
                        setAwaitingReply(false);
                        if (isNetworkFailure) {
                            offerEncyclopedia();
                        } else {
                            adapter.add(new ChatMessage(ChatMessage.Role.ASSISTANT, message));
                            scrollToEnd();
                        }
                    }
                });
    }

    private void setAwaitingReply(boolean awaiting) {
        awaitingReply = awaiting;
        adapter.setTyping(awaiting);
        sendButton.setEnabled(!awaiting);
        if (awaiting) {
            scrollToEnd();
        }
    }

    private void showTranscript() {
        emptyState.setVisibility(View.GONE);
        chatList.setVisibility(View.VISIBLE);
    }

    private void scrollToEnd() {
        chatList.post(() -> chatList.smoothScrollToPosition(Math.max(0, adapter.lastPosition())));
    }

    private void offerEncyclopedia() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.chat_offline_title)
                .setMessage(R.string.chat_offline_message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_open_encyclopedia, (dialog, which) ->
                        ((MainShellActivity) requireActivity())
                                .openDestination(R.id.drawer_encyclopedia))
                .show();
    }

    private void startSpeechInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        // Dictate in the app's language, not always English.
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,
                LocaleManager.currentTag(requireContext()));
        try {
            speechLauncher.launch(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(requireContext(), R.string.speech_unsupported, Toast.LENGTH_SHORT).show();
        }
    }
}
